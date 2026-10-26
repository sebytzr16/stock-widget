package io.github.sebytzr16.stockwidget.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.AlertKind
import io.github.sebytzr16.stockwidget.core.ChartRange
import io.github.sebytzr16.stockwidget.core.Holding
import io.github.sebytzr16.stockwidget.core.NewsItem
import io.github.sebytzr16.stockwidget.core.RangeSeries
import io.github.sebytzr16.stockwidget.core.SearchResult
import io.github.sebytzr16.stockwidget.core.Settings
import io.github.sebytzr16.stockwidget.core.SortMode
import io.github.sebytzr16.stockwidget.core.UserData
import io.github.sebytzr16.stockwidget.core.displaySymbol
import io.github.sebytzr16.stockwidget.data.RemovedStock
import io.github.sebytzr16.stockwidget.repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

sealed interface ChartLoad {
    data object Loading : ChartLoad
    data class Ready(val series: RangeSeries) : ChartLoad
    data object Empty : ChartLoad
    data object Failed : ChartLoad
}

sealed interface NewsLoad {
    data object Loading : NewsLoad
    data class Ready(val items: List<NewsItem>) : NewsLoad
    data object Failed : NewsLoad
}

data class SearchUi(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val failed: Boolean = false,
)

sealed interface UiMessage {
    data class Removed(val stock: RemovedStock) : UiMessage
    data class Text(@param:StringRes val res: Int, val arg: String? = null) : UiMessage
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = app.repository

    val user: StateFlow<UserData> = repo.user

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val home: StateFlow<HomeUi> = combine(repo.user, repo.market, repo.refreshing, ticker) { u, m, refreshing, now ->
        Ui.home(app, u, m, refreshing, now)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        Ui.home(app, repo.user.value, repo.market.value, false, System.currentTimeMillis()),
    )

    // ---- Detail -------------------------------------------------------------------------

    private val selected = MutableStateFlow<String?>(null)
    private val _range = MutableStateFlow(ChartRange.D1)
    val range: StateFlow<ChartRange> = _range.asStateFlow()

    fun select(symbol: String) {
        if (selected.value != symbol) {
            selected.value = symbol
            _range.value = ChartRange.D1
        }
    }

    fun setRange(range: ChartRange) {
        _range.value = range
    }

    val detail: StateFlow<DetailUi?> = combine(selected, repo.user, repo.market, ticker) { s, u, m, now ->
        s?.let { Ui.detail(app, it, u, m, now) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chart: StateFlow<ChartLoad> = combine(selected, _range, repo.market) { s, r, m ->
        Triple(s, r, s?.let { m.quotes[it]?.fetchedAt })
    }.distinctUntilChanged().transformLatest { (s, r, _) ->
        if (s == null) {
            emit(ChartLoad.Empty)
            return@transformLatest
        }
        if (r != ChartRange.D1) emit(ChartLoad.Loading)
        val series = try {
            repo.range(s, r)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ChartLoad.Failed)
            return@transformLatest
        }
        emit(if (series == null) ChartLoad.Empty else ChartLoad.Ready(series))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartLoad.Loading)

    val news: StateFlow<NewsLoad> = selected.filterNotNull().transformLatest { s ->
        emit(NewsLoad.Loading)
        emit(
            try {
                NewsLoad.Ready(repo.news(s))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NewsLoad.Failed
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsLoad.Loading)

    // ---- Search -------------------------------------------------------------------------

    val query = MutableStateFlow("")
    private var lastResults: List<SearchResult> = emptyList()

    val search: StateFlow<SearchUi> = query.debounce(250).map { it.trim() }.distinctUntilChanged().transformLatest { q ->
        if (q.isEmpty()) {
            lastResults = emptyList()
            emit(SearchUi())
            return@transformLatest
        }
        emit(SearchUi(q, loading = true, results = lastResults))
        emit(
            try {
                SearchUi(q, results = repo.search(q).also { lastResults = it })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUi(q, failed = true)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())

    // ---- Messages -----------------------------------------------------------------------

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    // ---- Actions ------------------------------------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            val r = repo.refresh(force = true)
            if (r.ok == 0 && r.failed > 0) _messages.send(UiMessage.Text(R.string.msg_offline))
        }
    }

    fun onForeground() {
        viewModelScope.launch { repo.refresh(force = false) }
    }

    fun isTracked(symbol: String): Boolean = repo.isTracked(symbol)

    fun add(result: SearchResult) {
        if (repo.isTracked(result.symbol)) return
        repo.add(result.symbol, result.name)
        _messages.trySend(UiMessage.Text(R.string.msg_added, displaySymbol(result.symbol)))
    }

    fun remove(symbol: String) {
        repo.remove(symbol)?.let { _messages.trySend(UiMessage.Removed(it)) }
    }

    fun undo(removed: RemovedStock) = repo.restore(removed)

    fun togglePin(symbol: String) {
        repo.togglePin(symbol)
    }

    fun reorder(symbols: List<String>) {
        repo.reorder(symbols)
    }

    fun setSort(mode: SortMode) = repo.updateSettings { it.copy(sort = mode) }

    fun setHolding(symbol: String, holding: Holding?) = repo.setHolding(symbol, holding)

    fun addAlert(symbol: String, kind: AlertKind, value: Double) {
        repo.addAlert(symbol, kind, value)
        _messages.trySend(UiMessage.Text(R.string.msg_alert_created, displaySymbol(symbol)))
    }

    fun setAlertEnabled(id: Long, enabled: Boolean) {
        repo.setAlertEnabled(id, enabled)
    }

    fun deleteAlert(id: Long) {
        repo.deleteAlert(id)
    }

    fun setNote(symbol: String, note: String) {
        repo.setNote(symbol, note)
    }

    fun updateSettings(transform: (Settings) -> Settings) = repo.updateSettings(transform)

    fun message(@StringRes res: Int, arg: String? = null) {
        _messages.trySend(UiMessage.Text(res, arg))
    }
}
