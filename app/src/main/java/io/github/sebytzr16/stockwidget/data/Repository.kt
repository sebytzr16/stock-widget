package io.github.sebytzr16.stockwidget.data

import android.content.Context
import io.github.sebytzr16.stockwidget.core.AlertKind
import io.github.sebytzr16.stockwidget.core.AlertRules
import io.github.sebytzr16.stockwidget.core.ChartRange
import io.github.sebytzr16.stockwidget.core.Currencies
import io.github.sebytzr16.stockwidget.core.FileStore
import io.github.sebytzr16.stockwidget.core.Holding
import io.github.sebytzr16.stockwidget.core.Indices
import io.github.sebytzr16.stockwidget.core.MarketData
import io.github.sebytzr16.stockwidget.core.MarketHours
import io.github.sebytzr16.stockwidget.core.NewsItem
import io.github.sebytzr16.stockwidget.core.Portfolio
import io.github.sebytzr16.stockwidget.core.PriceAlert
import io.github.sebytzr16.stockwidget.core.Quote
import io.github.sebytzr16.stockwidget.core.RangeSeries
import io.github.sebytzr16.stockwidget.core.SearchResult
import io.github.sebytzr16.stockwidget.core.Series
import io.github.sebytzr16.stockwidget.core.Settings
import io.github.sebytzr16.stockwidget.core.UserData
import io.github.sebytzr16.stockwidget.core.WatchItem
import io.github.sebytzr16.stockwidget.core.WidgetTheme
import io.github.sebytzr16.stockwidget.core.YahooClient
import io.github.sebytzr16.stockwidget.core.YahooParser
import io.github.sebytzr16.stockwidget.system.Notifier
import io.github.sebytzr16.stockwidget.system.Shortcuts
import io.github.sebytzr16.stockwidget.widget.WidgetUpdater
import io.github.sebytzr16.stockwidget.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** What a stock removal took away, so Undo can put it all back. */
data class RemovedStock(val item: WatchItem, val index: Int, val holding: Holding?, val alerts: List<PriceAlert>, val note: String?)

data class RefreshResult(val ok: Int, val failed: Int, val skipped: Boolean = false)

/**
 * The app's single source of truth. User data and the last market data live in memory as
 * StateFlows backed by two small JSON files. Refreshes are serialized, throttled and run
 * the symbol fetches in parallel; widgets and alerts update when new prices arrive.
 */
class Repository(context: Context) {

    private val app = context.applicationContext
    private val api = YahooClient()
    private val store = FileStore(File(app.filesDir, "data"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val saveMutex = Mutex()
    private val refreshMutex = Mutex()
    private val widgetMutex = Mutex()

    private val _user = MutableStateFlow(store.loadUser())
    private val _market = MutableStateFlow(store.loadMarket())
    private val _refreshing = MutableStateFlow(false)

    val user: StateFlow<UserData> = _user.asStateFlow()
    val market: StateFlow<MarketData> = _market.asStateFlow()
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    val settings: Settings get() = _user.value.settings

    private val rangeCache = ConcurrentHashMap<String, Pair<Long, RangeSeries>>()
    private val newsCache = ConcurrentHashMap<String, Pair<Long, List<NewsItem>>>()

    init {
        if (settings.homeCurrency.isBlank()) {
            update(EFFECT_NONE) { it.copy(settings = it.settings.copy(homeCurrency = deviceCurrency())) }
        }
    }

    // ---- Watchlist & user data ---------------------------------------------------------

    fun isTracked(symbol: String): Boolean = _user.value.items.any { it.symbol == symbol }

    fun add(symbol: String, name: String) {
        if (isTracked(symbol)) return
        update(EFFECT_WIDGETS) { it.copy(items = it.items + WatchItem(symbol, name)) }
        scope.launch { refresh(force = true, only = setOf(symbol)) }
    }

    fun remove(symbol: String): RemovedStock? {
        val u = _user.value
        val index = u.items.indexOfFirst { it.symbol == symbol }
        if (index < 0) return null
        val removed = RemovedStock(
            item = u.items[index],
            index = index,
            holding = u.holdings[symbol],
            alerts = u.alerts.filter { it.symbol == symbol },
            note = u.notes[symbol],
        )
        update(EFFECT_WIDGETS or EFFECT_SHORTCUTS) { cur ->
            cur.copy(
                items = cur.items.filterNot { it.symbol == symbol },
                holdings = cur.holdings - symbol,
                alerts = cur.alerts.filterNot { it.symbol == symbol },
                notes = cur.notes - symbol,
            )
        }
        return removed
    }

    fun restore(r: RemovedStock) {
        update(EFFECT_WIDGETS or EFFECT_SHORTCUTS) { cur ->
            if (cur.items.any { it.symbol == r.item.symbol }) return@update cur
            val items = cur.items.toMutableList().apply { add(r.index.coerceIn(0, size), r.item) }
            cur.copy(
                items = items,
                holdings = if (r.holding != null) cur.holdings + (r.item.symbol to r.holding) else cur.holdings,
                alerts = cur.alerts + r.alerts,
                notes = if (r.note != null) cur.notes + (r.item.symbol to r.note) else cur.notes,
            )
        }
    }

    fun togglePin(symbol: String) = update(EFFECT_WIDGETS or EFFECT_SHORTCUTS) { cur ->
        val items = cur.items.map { if (it.symbol == symbol) it.copy(pinned = !it.pinned) else it }
        cur.copy(items = items.filter { it.pinned } + items.filterNot { it.pinned })
    }

    /** Saves a new manual order; pinned stocks always stay above the others. */
    fun reorder(symbols: List<String>) = update(EFFECT_WIDGETS or EFFECT_SHORTCUTS) { cur ->
        val bySymbol = cur.items.associateBy { it.symbol }
        val ordered = symbols.mapNotNull { bySymbol[it] } + cur.items.filter { it.symbol !in symbols }
        cur.copy(items = ordered.filter { it.pinned } + ordered.filterNot { it.pinned })
    }

    fun setHolding(symbol: String, holding: Holding?) {
        val before = _user.value.settings.homeCurrency
        update(EFFECT_WIDGETS) { cur ->
            cur.copy(holdings = if (holding == null) cur.holdings - symbol else cur.holdings + (symbol to holding))
        }
        // A holding in a new currency needs its exchange rate for the portfolio total.
        val needed = Portfolio.neededCurrencies(_user.value.holdings, _market.value.quotes, before)
        if (needed.any { Portfolio.rate(_market.value.fx, it, before) == null }) {
            scope.launch { refresh(force = true) }
        }
    }

    fun addAlert(symbol: String, kind: AlertKind, value: Double) = update(EFFECT_NONE) { cur ->
        val id = (cur.alerts.maxOfOrNull { it.id } ?: 0L) + 1L
        cur.copy(alerts = cur.alerts + PriceAlert(id, symbol, kind, value))
    }

    fun setAlertEnabled(id: Long, enabled: Boolean) = update(EFFECT_NONE) { cur ->
        cur.copy(alerts = cur.alerts.map { if (it.id == id) it.copy(enabled = enabled, lastFiredDay = null) else it })
    }

    fun deleteAlert(id: Long) = update(EFFECT_NONE) { cur -> cur.copy(alerts = cur.alerts.filterNot { it.id == id }) }

    fun setNote(symbol: String, note: String) = update(EFFECT_NONE) { cur ->
        cur.copy(notes = if (note.isBlank()) cur.notes - symbol else cur.notes + (symbol to note.trim()))
    }

    /** Saves a widget's picks and, when given, its theme. */
    fun setWidget(widgetId: Int, symbols: List<String>, theme: WidgetTheme? = null) {
        update(EFFECT_WIDGETS) {
            it.copy(
                widgets = if (symbols.isEmpty()) it.widgets - widgetId else it.widgets + (widgetId to symbols),
                widgetThemes = if (theme == null) it.widgetThemes else it.widgetThemes + (widgetId to theme),
            )
        }
        val missing = symbols.filter { it !in _market.value.quotes }.toSet()
        if (missing.isNotEmpty()) scope.launch { refresh(force = true, only = missing) }
    }

    fun removeWidgets(ids: Collection<Int>) = update(EFFECT_NONE) {
        it.copy(widgets = it.widgets - ids.toSet(), widgetThemes = it.widgetThemes - ids.toSet())
    }

    /** After a restore from backup, placed widgets come back under new ids. */
    fun remapWidgets(oldIds: IntArray, newIds: IntArray) = update(EFFECT_WIDGETS) { cur ->
        val moved = oldIds.indices.mapNotNull { i -> cur.widgets[oldIds[i]]?.let { symbols -> newIds.getOrNull(i)?.to(symbols) } }
        val themes = oldIds.indices.mapNotNull { i -> cur.widgetThemes[oldIds[i]]?.let { t -> newIds.getOrNull(i)?.to(t) } }
        cur.copy(
            widgets = cur.widgets - oldIds.toSet() + moved,
            widgetThemes = cur.widgetThemes - oldIds.toSet() + themes,
        )
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val before = settings
        val after = update(EFFECT_WIDGETS) { it.copy(settings = transform(it.settings)) }.settings
        if (before.refreshMinutes != after.refreshMinutes) RefreshWorker.schedule(app, after.refreshMinutes)
        if (before.homeCurrency != after.homeCurrency || before.indices != after.indices) {
            scope.launch { refresh(force = true) }
        }
    }

    // ---- Network ----------------------------------------------------------------------

    /** Every symbol something on screen needs: the list, widget picks and the indices strip. */
    fun trackedSymbols(u: UserData = _user.value): Set<String> =
        (u.items.map { it.symbol } + u.widgets.values.flatten() + u.settings.indices).toSet()

    /**
     * Fetches fresh quotes. A non-forced refresh is skipped when the last attempt was under a
     * minute ago, which collapses the app, widgets and WorkManager asking at the same time.
     * [only] limits the fetch to a few symbols (e.g. one just added).
     */
    suspend fun refresh(force: Boolean, only: Set<String>? = null): RefreshResult = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        val before = _market.value
        if (only == null && !force && now - before.lastAttempt < MIN_REFRESH_GAP) {
            return RefreshResult(0, 0, skipped = true)
        }
        val u = _user.value
        val tracked = trackedSymbols(u)
        val symbols = if (only == null) tracked else only.intersect(tracked)
        if (symbols.isEmpty()) return RefreshResult(0, 0)

        _refreshing.value = only == null
        try {
            val gate = Semaphore(MAX_PARALLEL)
            val fetched = coroutineScope {
                symbols.map { s ->
                    async { gate.withPermit { fetchQuote(s, u.items.firstOrNull { it.symbol == s }?.name, before.quotes[s], now) } }
                }.awaitAll()
            }
            val quotes = before.quotes.toMutableMap()
            val fresh = LinkedHashMap<String, Quote>()
            var failed = 0
            var error: String? = null
            for ((s, q, problem) in fetched) {
                if (q != null) {
                    quotes[s] = q
                    fresh[s] = q
                } else {
                    failed++
                    error = problem
                }
            }

            val fx = before.fx.toMutableMap()
            val home = u.settings.homeCurrency
            val pairs = Portfolio.neededCurrencies(u.holdings, quotes, home)
            if (pairs.isNotEmpty() && fresh.isNotEmpty()) {
                val rates = coroutineScope {
                    pairs.map { c ->
                        async { gate.withPermit { c to runCatching { api.chart(Portfolio.fxSymbol(c, home), "1d", "1d", false).price }.getOrNull() } }
                    }.awaitAll()
                }
                for ((c, rate) in rates) if (rate != null && rate > 0.0) fx[c + home] = rate
            }

            val keep = trackedSymbols(_user.value)
            val updated = MarketData(
                quotes = quotes.filterKeys { it in keep },
                fx = fx,
                lastSuccess = if (only == null && fresh.isNotEmpty()) now else before.lastSuccess,
                lastAttempt = if (only == null) now else before.lastAttempt,
                lastError = if (only == null && fresh.isEmpty() && failed > 0) error ?: "Network error" else before.lastError.takeIf { only != null },
            )
            _market.value = updated
            scope.launch(Dispatchers.IO) { saveMutex.withLock { store.saveMarket(_market.value) } }
            if (fresh.isNotEmpty()) {
                updateWidgets()
                checkAlerts(fresh)
            }
            RefreshResult(fresh.size, failed)
        } finally {
            _refreshing.value = false
        }
    }

    /**
     * One symbol's latest quote, or why it failed. Before the day's open there may be no
     * session in the one-day chart and none cached (e.g. a fresh install): then the last
     * session comes from a short multi-day chart, once, and is kept until the open.
     */
    private suspend fun fetchQuote(symbol: String, name: String?, previous: Quote?, now: Long): Triple<String, Quote?, String?> {
        val result = runCatching { api.chart(symbol, "1d", "5m", prePost = true) }
        val chart = result.getOrNull()
        var q = chart?.let { YahooParser.toQuote(it, symbol, name, now, previous) }
        // Mutual funds have no intraday prices at all; don't ask again on every refresh.
        if (q != null && q.intraday.size < 2 && q.type != "MUTUALFUND") {
            val last = runCatching { api.chart(symbol, "5d", "5m", prePost = false) }.getOrNull()?.let { YahooParser.lastSession(it) }
            if (last != null && last.size >= 2) q = q.copy(intraday = Series.downsample(last, INTRADAY_POINTS))
        }
        return Triple(symbol, q, result.exceptionOrNull()?.message ?: chart?.error)
    }

    /** Whether a background refresh is worth it now (smart schedule). */
    fun shouldRefreshInBackground(now: Long): Boolean {
        val u = _user.value
        val tracked = trackedSymbols(u)
        if (tracked.isEmpty()) return false
        if (!u.settings.onlyWhenOpen) return true
        return MarketHours.anyActive(tracked.mapNotNull { _market.value.quotes[it] }, now)
    }

    /** A detail-chart range; 1D comes straight from the cache, others are fetched and kept briefly. */
    suspend fun range(symbol: String, range: ChartRange): RangeSeries? {
        if (range == ChartRange.D1) {
            val q = _market.value.quotes[symbol] ?: return null
            if (q.intraday.size < 2) return null
            return RangeSeries(q.intraday, q.previousClose.takeIf { it > 0.0 } ?: q.intraday.first().price)
        }
        val key = "$symbol|${range.name}"
        val now = System.currentTimeMillis()
        rangeCache[key]?.let { (t, s) -> if (now - t < rangeTtl(range)) return s }
        val series = YahooParser.toRangeSeries(api.chart(symbol, range.range, range.interval, prePost = false))
            ?: return null
        rangeCache[key] = now to series
        return series
    }

    suspend fun news(symbol: String): List<NewsItem> {
        val now = System.currentTimeMillis()
        newsCache[symbol]?.let { (t, n) -> if (now - t < NEWS_TTL) return n }
        val news = api.news(symbol).take(6)
        newsCache[symbol] = now to news
        return news
    }

    /**
     * Curated indices first (so "S&P 500" finds ^GSPC), then Yahoo's results. Offline, the
     * curated matches still show; with none, the failure reaches the caller.
     */
    suspend fun search(query: String): List<SearchResult> {
        val local = Indices.matches(query)
        val remote = try {
            api.search(query)
        } catch (e: IOException) {
            if (local.isEmpty()) throw e
            emptyList()
        }
        return (local + remote).distinctBy { it.symbol }
    }

    // ---- Internals --------------------------------------------------------------------

    private fun update(effects: Int, transform: (UserData) -> UserData): UserData {
        val updated = _user.updateAndGet(transform)
        scope.launch(Dispatchers.IO) { saveMutex.withLock { store.saveUser(_user.value) } }
        if (effects and EFFECT_WIDGETS != 0) updateWidgets()
        if (effects and EFFECT_SHORTCUTS != 0) scope.launch { Shortcuts.update(app, _user.value) }
        return updated
    }

    private fun updateWidgets() {
        scope.launch { widgetMutex.withLock { WidgetUpdater.updateAll(app) } }
    }

    private fun checkAlerts(fresh: Map<String, Quote>) {
        val u = _user.value
        if (!u.settings.alertsEnabled || u.alerts.none { it.enabled }) return
        val (hits, _) = AlertRules.evaluate(u.alerts, fresh)
        if (hits.isEmpty()) return
        val fired = hits.associateBy { it.alert.id }
        update(EFFECT_NONE) { cur ->
            cur.copy(alerts = cur.alerts.map { a ->
                val hit = fired[a.id] ?: return@map a
                if (a.kind == AlertKind.MOVE) a.copy(lastFiredDay = AlertRules.localDay(hit.quote)) else a.copy(enabled = false)
            })
        }
        hits.forEach { Notifier.alert(app, it) }
    }

    private fun rangeTtl(range: ChartRange): Long = when (range) {
        ChartRange.W1, ChartRange.M1 -> 5 * 60_000L
        else -> 60 * 60_000L
    }

    private fun deviceCurrency(): String {
        val code = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
        return if (code != null && code in Currencies.HOME_CHOICES) code else "USD"
    }

    private companion object {
        const val EFFECT_NONE = 0
        const val EFFECT_WIDGETS = 1
        const val EFFECT_SHORTCUTS = 2
        const val MIN_REFRESH_GAP = 60_000L
        const val MAX_PARALLEL = 6
        const val NEWS_TTL = 15 * 60_000L
        const val INTRADAY_POINTS = 120
    }
}
