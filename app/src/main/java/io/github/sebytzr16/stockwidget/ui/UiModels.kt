package io.github.sebytzr16.stockwidget.ui

import android.content.Context
import android.text.format.DateUtils
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.AlertKind
import io.github.sebytzr16.stockwidget.core.Currencies
import io.github.sebytzr16.stockwidget.core.Fmt
import io.github.sebytzr16.stockwidget.core.Holding
import io.github.sebytzr16.stockwidget.core.Indices
import io.github.sebytzr16.stockwidget.core.MarketData
import io.github.sebytzr16.stockwidget.core.MarketHours
import io.github.sebytzr16.stockwidget.core.MarketPhase
import io.github.sebytzr16.stockwidget.core.Portfolio
import io.github.sebytzr16.stockwidget.core.Quote
import io.github.sebytzr16.stockwidget.core.SortMode
import io.github.sebytzr16.stockwidget.core.UserData
import io.github.sebytzr16.stockwidget.core.WatchItem
import io.github.sebytzr16.stockwidget.core.displaySymbol

data class StockRow(
    val symbol: String,
    val display: String,
    val name: String,
    val subtitle: String,
    val pinned: Boolean,
    val hasPrice: Boolean,
    val price: String,
    val change: String,
    val up: Boolean,
    val extLabel: String?,
    val extChange: String?,
    val extUp: Boolean,
    val holding: String?,
    val spark: List<Double>,
    val baseline: Double?,
)

/** A row in edit mode: no prices, so a refresh never disturbs a drag in progress. */
data class EditItem(val symbol: String, val display: String, val name: String, val pinned: Boolean)

data class IndexChip(val symbol: String, val name: String, val value: String, val change: String, val up: Boolean)

data class PortfolioUi(
    val currency: String,
    val total: String,
    val day: String,
    val dayUp: Boolean,
    val gain: String,
    val gainUp: Boolean,
    val series: List<Double>,
    val partial: Boolean,
)

data class HomeUi(
    val rows: List<StockRow> = emptyList(),
    /** The list in its saved order (pinned first), for edit mode. */
    val editRows: List<EditItem> = emptyList(),
    val indices: List<IndexChip> = emptyList(),
    val portfolio: PortfolioUi? = null,
    val status: String? = null,
    val statusPhase: MarketPhase? = null,
    val updated: String? = null,
    val offline: Boolean = false,
    val refreshing: Boolean = false,
    val sort: SortMode = SortMode.CUSTOM,
)

data class RangeBar(val low: String, val high: String, val fraction: Float)

data class PositionUi(
    val holding: String,
    val avgCost: String,
    val value: String,
    val day: String,
    val dayUp: Boolean,
    val total: String,
    val totalUp: Boolean,
)

data class AlertUi(val id: Long, val title: String, val subtitle: String, val enabled: Boolean)

data class DetailUi(
    val symbol: String,
    val display: String,
    val subtitle: String,
    val inList: Boolean,
    val pinned: Boolean,
    val quote: Quote?,
    val ext: String?,
    val extUp: Boolean,
    val status: String?,
    val statusPhase: MarketPhase?,
    val open: String,
    val previousClose: String,
    val volume: String,
    val exchange: String,
    val dayRange: RangeBar?,
    val yearRange: RangeBar?,
    val canHold: Boolean,
    val holding: Holding?,
    val position: PositionUi?,
    val unitLabel: String,
    val alerts: List<AlertUi>,
    val note: String,
)

/** Builds display models from the repository's state. Pure formatting; no I/O. */
object Ui {

    private const val DASH = "—"

    /** The list in the order the user chose to sort it, pinned stocks first. */
    fun sorted(u: UserData, m: MarketData): List<WatchItem> {
        val home = u.settings.homeCurrency
        val ordered = u.items.filter { it.pinned } + u.items.filterNot { it.pinned }
        return when (u.settings.sort) {
            SortMode.CUSTOM -> ordered
            SortMode.NAME -> ordered.sortedBy { displaySymbol(it.symbol) }
            SortMode.CHANGE -> ordered.sortedByDescending { m.quotes[it.symbol]?.changePercent ?: Double.NEGATIVE_INFINITY }
            SortMode.PRICE -> ordered.sortedByDescending { valueIn(m, it.symbol, home) }
            SortMode.VALUE -> ordered.sortedByDescending { item ->
                val q = m.quotes[item.symbol]
                val h = u.holdings[item.symbol]
                if (q == null || h == null) -1.0 else {
                    val p = Portfolio.position(h, q)
                    p.value * (Portfolio.rate(m.fx, p.currency, home) ?: 0.0)
                }
            }
        }.let { list -> list.filter { it.pinned } + list.filterNot { it.pinned } }
    }

    fun home(context: Context, u: UserData, m: MarketData, refreshing: Boolean, now: Long): HomeUi {
        val settings = u.settings
        val rows = sorted(u, m).map { row(context, it, m.quotes[it.symbol], u.holdings[it.symbol], settings.extendedHours, now) }
        val editRows = (u.items.filter { it.pinned } + u.items.filterNot { it.pinned })
            .map { EditItem(it.symbol, displaySymbol(it.symbol), it.name, it.pinned) }
        val indices = settings.indices.mapNotNull { s ->
            val q = m.quotes[s]?.takeIf { it.hasPrice } ?: return@mapNotNull null
            IndexChip(
                symbol = s,
                name = Indices.short(s) ?: q.name,
                value = Fmt.number(q.price, Fmt.priceDecimals(q.price, q.priceHint)),
                change = Fmt.percent(q.changePercent),
                up = q.isUp,
            )
        }
        val summary = Portfolio.summarize(u.holdings, m.quotes, m.fx, settings.homeCurrency)
        val portfolio = summary?.let {
            PortfolioUi(
                currency = it.currency,
                total = Fmt.money(it.value, it.currency),
                day = Fmt.money(it.dayChange, it.currency, signed = true) + " (" + Fmt.percent(it.dayChangePercent) + ")",
                dayUp = it.dayChange >= 0.0,
                gain = Fmt.money(it.gain, it.currency, signed = true) + " (" + Fmt.percent(it.gainPercent) + ")",
                gainUp = it.gain >= 0.0,
                series = it.series,
                partial = !it.complete,
            )
        }
        val primary = primaryQuote(u.items, m)
        val status = primary?.let { MarketHours.status(it, now) }
        return HomeUi(
            rows = rows,
            editRows = editRows,
            indices = indices,
            portfolio = portfolio,
            status = primary?.let { q -> status?.let { statusText(context, q, it.phase, it.until, now, withExchange = true) } },
            statusPhase = status?.phase,
            updated = if (m.lastSuccess > 0L) time(context, m.lastSuccess) else null,
            offline = m.lastError != null,
            refreshing = refreshing,
            sort = settings.sort,
        )
    }

    fun detail(context: Context, symbol: String, u: UserData, m: MarketData, now: Long): DetailUi {
        val item = u.items.firstOrNull { it.symbol == symbol }
        val q = m.quotes[symbol]?.takeIf { it.hasPrice }
        val status = q?.let { MarketHours.status(it, now) }
        val holding = u.holdings[symbol]
        val position = if (q != null && holding != null) {
            val p = Portfolio.position(holding, q)
            PositionUi(
                holding = quantity(context, q, holding.shares),
                avgCost = Fmt.price(holding.avgCost, q.currency, q.priceHint),
                value = Fmt.money(p.value, p.currency),
                day = Fmt.money(p.dayChange, p.currency, signed = true) + " (" + Fmt.percent(q.changePercent) + ")",
                dayUp = p.dayChange >= 0.0,
                total = Fmt.money(p.gain, p.currency, signed = true) + " (" + Fmt.percent(p.gainPercent) + ")",
                totalUp = p.gain >= 0.0,
            )
        } else null
        val ext = q?.let { ext(context, it, now, u.settings.extendedHours, detailed = true) }
        val name = item?.name ?: q?.name ?: Indices.POPULAR.firstOrNull { it.symbol == symbol }?.name ?: displaySymbol(symbol)
        return DetailUi(
            symbol = symbol,
            display = displaySymbol(symbol),
            subtitle = listOfNotNull(
                name,
                q?.exchange?.takeIf { it.isNotBlank() },
                q?.currency?.takeIf { it.isNotBlank() }?.let { if (it == "GBp") "GBX" else it },
            ).joinToString(" · "),
            inList = item != null,
            pinned = item?.pinned == true,
            quote = q,
            ext = ext?.first,
            extUp = ext?.second ?: true,
            status = if (q != null && status != null) statusText(context, q, status.phase, status.until, now, withExchange = false) else null,
            statusPhase = status?.phase,
            open = q?.open?.takeIf { it > 0.0 }?.let { Fmt.price(it, q.currency, q.priceHint) } ?: DASH,
            previousClose = q?.previousClose?.takeIf { it > 0.0 }?.let { Fmt.price(it, q.currency, q.priceHint) } ?: DASH,
            volume = q?.volume?.takeIf { it > 0L }?.let { Fmt.compact(it) } ?: DASH,
            exchange = q?.exchange?.takeIf { it.isNotBlank() } ?: DASH,
            dayRange = q?.let { rangeBar(it, it.dayLow, it.dayHigh) },
            yearRange = q?.let { rangeBar(it, it.yearLow, it.yearHigh) },
            canHold = !(q?.isIndex ?: symbol.startsWith("^")) && q?.isCurrency != true,
            holding = holding,
            position = position,
            unitLabel = q?.let { unitName(context, it, 2.0) } ?: context.getString(R.string.unit_shares),
            alerts = u.alerts.filter { it.symbol == symbol }.map { a ->
                AlertUi(
                    id = a.id,
                    title = when (a.kind) {
                        AlertKind.ABOVE -> context.getString(R.string.alert_above, price(q, a.value))
                        AlertKind.BELOW -> context.getString(R.string.alert_below, price(q, a.value))
                        AlertKind.MOVE -> context.getString(R.string.alert_move, Fmt.number(a.value, if (a.value % 1.0 == 0.0) 0 else 1))
                    },
                    subtitle = context.getString(
                        when {
                            !a.enabled -> R.string.alert_off
                            a.kind == AlertKind.MOVE -> R.string.alert_once_a_day
                            else -> R.string.alert_once
                        }
                    ),
                    enabled = a.enabled,
                )
            },
            note = u.notes[symbol].orEmpty(),
        )
    }

    fun changeText(q: Quote): String =
        Fmt.signedPrice(q.change, q.currency, Fmt.priceDecimals(q.price, q.priceHint)) + " (" + Fmt.percent(q.changePercent) + ")"

    /** "10 shares", "0.05 BTC". */
    fun quantity(context: Context, q: Quote, shares: Double): String = Fmt.quantity(shares) + " " + unitName(context, q, shares)

    private fun unitName(context: Context, q: Quote, amount: Double): String = when {
        q.isCrypto -> q.symbol.substringBefore('-')
        amount == 1.0 -> context.getString(R.string.unit_share)
        else -> context.getString(R.string.unit_shares)
    }

    private fun row(context: Context, item: WatchItem, q: Quote?, holding: Holding?, extHours: Boolean, now: Long): StockRow {
        val has = q != null && q.hasPrice
        val ext = if (has && extHours) ext(context, q!!, now, true, detailed = false) else null
        val subtitle = listOfNotNull(item.name.takeIf { it.isNotBlank() }, q?.exchange?.takeIf { it.isNotBlank() }).joinToString(" · ")
        return StockRow(
            symbol = item.symbol,
            display = displaySymbol(item.symbol),
            name = item.name,
            subtitle = subtitle,
            pinned = item.pinned,
            hasPrice = has,
            price = if (has) Fmt.price(q!!.price, q.currency, q.priceHint) else DASH,
            change = if (has) changeText(q!!) else "",
            up = q?.isUp ?: true,
            extLabel = ext?.let { context.getString(if (isPre(q!!)) R.string.pre_market else R.string.after_hours) },
            extChange = ext?.first,
            extUp = ext?.second ?: true,
            holding = if (has && holding != null) {
                val p = Portfolio.position(holding, q!!)
                quantity(context, q, holding.shares) + " · " + Fmt.money(p.value, p.currency)
            } else null,
            spark = q?.intraday?.map { it.price }.orEmpty(),
            baseline = q?.previousClose?.takeIf { it > 0.0 },
        )
    }

    /**
     * The pre-market or after-hours move, while one is relevant: the latest extended-hours
     * price is newer than the regular one and the regular session isn't trading.
     */
    private fun ext(context: Context, q: Quote, now: Long, enabled: Boolean, detailed: Boolean): Pair<String, Boolean>? {
        val price = q.extPrice ?: return null
        if (!enabled || q.extTime <= q.marketTime || now - q.extTime > 16 * 3_600_000L) return null
        if (MarketHours.status(q, now).phase == MarketPhase.OPEN) return null
        val delta = price - q.price
        val pct = if (q.price > 0.0) delta / q.price * 100.0 else 0.0
        val text = if (detailed) {
            context.getString(
                if (isPre(q)) R.string.ext_pre_detail else R.string.ext_after_detail,
                Fmt.price(price, q.currency, q.priceHint),
                Fmt.signedPrice(delta, q.currency, Fmt.priceDecimals(q.price, q.priceHint)) + " (" + Fmt.percent(pct) + ")",
            )
        } else Fmt.percent(pct)
        return text to (delta >= 0.0)
    }

    private fun isPre(q: Quote): Boolean = q.regular != null && q.extTime < q.regular.start

    fun statusText(context: Context, q: Quote, phase: MarketPhase, until: Long?, now: Long, withExchange: Boolean): String {
        val where = if (withExchange) q.exchange.ifBlank { context.getString(R.string.market) } else context.getString(R.string.market)
        return when (phase) {
            MarketPhase.ALWAYS -> context.getString(R.string.status_always)
            MarketPhase.OPEN -> context.getString(R.string.status_open, where, duration(context, (until ?: now) - now))
            MarketPhase.PRE -> context.getString(R.string.status_pre, where, duration(context, (until ?: now) - now))
            MarketPhase.POST -> context.getString(R.string.status_post, where)
            MarketPhase.CLOSED -> if (until == null) context.getString(R.string.status_closed, where)
            else if (until - now < 12 * 3_600_000L) context.getString(R.string.status_closed_in, where, duration(context, until - now))
            else context.getString(
                R.string.status_closed_at, where,
                DateUtils.formatDateTime(context, until, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_TIME),
            )
        }
    }

    private fun duration(context: Context, ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(1L)
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) context.getString(R.string.duration_hm, h, m) else context.getString(R.string.duration_m, m)
    }

    private fun time(context: Context, t: Long): String {
        val flags = if (DateUtils.isToday(t)) DateUtils.FORMAT_SHOW_TIME
        else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
        return DateUtils.formatDateTime(context, t, flags)
    }

    /** The first stock-market listing in the list: whose open/closed status the header shows. */
    private fun primaryQuote(items: List<WatchItem>, m: MarketData): Quote? {
        val quotes = items.mapNotNull { m.quotes[it.symbol] }.filter { it.regular != null }
        return quotes.firstOrNull { !it.isCrypto && !it.isCurrency } ?: quotes.firstOrNull()
    }

    private fun rangeBar(q: Quote, low: Double, high: Double): RangeBar? {
        if (low <= 0.0 || high <= 0.0 || high < low) return null
        val fraction = if (high - low < 1e-12) 0.5f else ((q.price - low) / (high - low)).toFloat().coerceIn(0f, 1f)
        return RangeBar(Fmt.price(low, q.currency, q.priceHint), Fmt.price(high, q.currency, q.priceHint), fraction)
    }

    private fun price(q: Quote?, value: Double): String =
        if (q == null) Fmt.number(value, 2) else Fmt.price(value, q.currency, q.priceHint)

    private fun valueIn(m: MarketData, symbol: String, home: String): Double {
        val q = m.quotes[symbol] ?: return -1.0
        val unit = Currencies.unit(q.currency)
        return q.price * unit.factor * (Portfolio.rate(m.fx, unit.major, home) ?: 1.0)
    }
}
