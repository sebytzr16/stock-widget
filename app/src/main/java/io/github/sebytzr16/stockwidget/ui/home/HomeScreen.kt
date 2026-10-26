package io.github.sebytzr16.stockwidget.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.MarketPhase
import io.github.sebytzr16.stockwidget.core.SearchResult
import io.github.sebytzr16.stockwidget.core.SortMode
import io.github.sebytzr16.stockwidget.ui.AppViewModel
import io.github.sebytzr16.stockwidget.ui.EditItem
import io.github.sebytzr16.stockwidget.ui.HomeUi
import io.github.sebytzr16.stockwidget.ui.IndexChip
import io.github.sebytzr16.stockwidget.ui.PortfolioUi
import io.github.sebytzr16.stockwidget.ui.StockRow
import io.github.sebytzr16.stockwidget.ui.components.AppIcon
import io.github.sebytzr16.stockwidget.ui.components.SectionCard
import io.github.sebytzr16.stockwidget.ui.components.Sparkline
import io.github.sebytzr16.stockwidget.ui.components.trendColor
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette

/** One-tap picks for an empty list and an empty search. */
val SUGGESTIONS = listOf(
    SearchResult("^GSPC", "S&P 500", "Index", "INDEX"),
    SearchResult("AAPL", "Apple Inc.", "NASDAQ", "EQUITY"),
    SearchResult("MSFT", "Microsoft Corporation", "NASDAQ", "EQUITY"),
    SearchResult("NVDA", "NVIDIA Corporation", "NASDAQ", "EQUITY"),
    SearchResult("BTC-USD", "Bitcoin", "Crypto", "CRYPTOCURRENCY"),
)

@StringRes
fun sortLabel(mode: SortMode): Int = when (mode) {
    SortMode.CUSTOM -> R.string.sort_custom
    SortMode.NAME -> R.string.sort_name
    SortMode.CHANGE -> R.string.sort_change
    SortMode.PRICE -> R.string.sort_price
    SortMode.VALUE -> R.string.sort_value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    selected: String?,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val ui by vm.home.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = editing) { editing = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (editing) EditHeader(onDone = { editing = false })
            else HomeHeader(ui, onRefresh = vm::refresh, onSettings = onSettings)
        },
    ) { padding ->
        if (editing) {
            EditList(ui.editRows, vm, Modifier.padding(padding))
        } else {
            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "search") { SearchButton(onSearch) }
                    if (ui.indices.isNotEmpty()) item(key = "indices") { IndicesStrip(ui.indices, onOpen) }
                    ui.portfolio?.let { p -> item(key = "portfolio") { PortfolioCard(p) } }
                    item(key = "header") {
                        ListHeader(ui.rows.size, ui.sort, canEdit = ui.rows.isNotEmpty(), onSort = { sorting = true }, onEdit = { editing = true })
                    }
                    if (ui.rows.isEmpty()) item(key = "empty") { EmptyList(onAdd = vm::add) }
                    items(ui.rows, key = { it.symbol }) { row ->
                        StockCard(
                            row = row,
                            selected = row.symbol == selected,
                            onClick = { onOpen(row.symbol) },
                            modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    item(key = "footer") { Footer(ui) }
                }
            }
        }
    }
    if (sorting) {
        SortSheet(current = ui.sort, onPick = { vm.setSort(it); sorting = false }, onDismiss = { sorting = false })
    }
}

@Composable
private fun HomeHeader(ui: HomeUi, onRefresh: () -> Unit, onSettings: () -> Unit) {
    val palette = LocalPalette.current
    val dot = when (ui.statusPhase) {
        MarketPhase.OPEN, MarketPhase.ALWAYS -> palette.up
        MarketPhase.PRE, MarketPhase.POST -> MaterialTheme.colorScheme.primary
        else -> palette.text3
    }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.title_stocks), style = MaterialTheme.typography.headlineMedium)
            ui.status?.let { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = onRefresh, enabled = !ui.refreshing) {
            if (ui.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else AppIcon(R.drawable.ic_refresh, stringResource(R.string.refresh))
        }
        IconButton(onClick = onSettings) { AppIcon(R.drawable.ic_settings, stringResource(R.string.settings)) }
    }
}

@Composable
private fun EditHeader(onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.title_edit), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.edit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onDone) { Text(stringResource(R.string.done)) }
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    val hint = LocalPalette.current.text3
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.height(50.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_search, null, Modifier.size(20.dp), tint = hint)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = hint)
        }
    }
}

@Composable
private fun IndicesStrip(chips: List<IndexChip>, onOpen: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.symbol }) { c ->
            Surface(onClick = { onOpen(c.symbol) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.widthIn(min = 132.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(c.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(c.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Text(c.change, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = trendColor(c.up))
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioCard(p: PortfolioUi) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val today = stringResource(R.string.today)
    val totalReturn = stringResource(R.string.total_return)
    val dayColor = trendColor(p.dayUp)
    val gainColor = trendColor(p.gainUp)
    SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.portfolio), style = MaterialTheme.typography.labelMedium, color = secondary, modifier = Modifier.weight(1f))
            Text(p.currency, style = MaterialTheme.typography.labelSmall, color = secondary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(p.total, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = secondary)) { append("$today ") }
                        withStyle(SpanStyle(color = dayColor, fontWeight = FontWeight.Medium)) { append(p.day) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = secondary)) { append("$totalReturn ") }
                        withStyle(SpanStyle(color = gainColor, fontWeight = FontWeight.Medium)) { append(p.gain) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (p.partial) {
                    Text(stringResource(R.string.portfolio_partial), style = MaterialTheme.typography.labelSmall, color = LocalPalette.current.text3)
                }
            }
            Sparkline(p.series, null, p.dayUp, Modifier.padding(bottom = 4.dp).size(96.dp, 52.dp))
        }
    }
}

@Composable
private fun ListHeader(count: Int, sort: SortMode, canEdit: Boolean, onSort: () -> Unit, onEdit: () -> Unit) {
    val title = stringResource(R.string.watchlist)
    val text3 = LocalPalette.current.text3
    val sortText = stringResource(sortLabel(sort))
    val sortDescription = stringResource(R.string.sort_by, sortText)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                append(title)
                if (count > 0) withStyle(SpanStyle(color = text3, fontWeight = FontWeight.Normal)) { append("  $count") }
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onSort,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.semantics { contentDescription = sortDescription },
        ) {
            AppIcon(R.drawable.ic_sort, null, Modifier.size(16.dp), tint = LocalContentColor.current)
            Spacer(Modifier.width(6.dp))
            Text(sortText)
        }
        if (canEdit) TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
    }
}

@Composable
fun StockCard(row: StockRow, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val container = when {
        !row.hasPrice -> MaterialTheme.colorScheme.surfaceContainer
        row.up -> palette.upTint
        else -> palette.downTint
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 76.dp).padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.pinned) {
                        AppIcon(R.drawable.ic_pin_filled, stringResource(R.string.pinned), Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(row.display, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.holding?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Sparkline(row.spark, row.baseline, row.up, Modifier.size(56.dp, 32.dp), row.positions)
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 96.dp)) {
                Text(row.price, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (row.hasPrice) {
                    Text(row.change, style = MaterialTheme.typography.labelMedium, color = trendColor(row.up), maxLines = 1)
                }
                if (row.extLabel != null && row.extChange != null) {
                    val extColor = trendColor(row.extUp)
                    Text(
                        buildAnnotatedString {
                            append(row.extLabel + " ")
                            withStyle(SpanStyle(color = extColor, fontWeight = FontWeight.Medium)) { append(row.extChange) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyList(onAdd: (SearchResult) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        SuggestionChips(onAdd)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionChips(onAdd: (SearchResult) -> Unit, exclude: (String) -> Boolean = { false }) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SUGGESTIONS.filterNot { exclude(it.symbol) }.forEach { s ->
            AssistChip(
                onClick = { onAdd(s) },
                label = { Text(if (s.symbol.startsWith("^")) s.name else s.symbol.removeSuffix("-USD")) },
                leadingIcon = { AppIcon(R.drawable.ic_add, null, Modifier.size(16.dp), tint = LocalContentColor.current) },
            )
        }
    }
}

@Composable
private fun Footer(ui: HomeUi) {
    val updated = ui.updated
    val text = when {
        updated == null -> stringResource(R.string.source)
        ui.offline -> stringResource(R.string.offline_since, updated)
        else -> stringResource(R.string.updated, updated) + " · " + stringResource(R.string.source)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalPalette.current.text3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

/** Edit mode: drag the handle to reorder (within pinned / unpinned), pin, or remove. */
@Composable
private fun EditList(rows: List<EditItem>, vm: AppViewModel, modifier: Modifier) {
    val order = remember(rows) { rows.toMutableStateList() }
    // The drag handlers below outlive recompositions; they must see the list that's on screen.
    val latestOrder by rememberUpdatedState(order)
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)

    fun commit() = vm.reorder(latestOrder.map { it.symbol })

    fun moveBy(symbol: String, delta: Int): Boolean {
        val from = order.indexOfFirst { it.symbol == symbol }
        val to = from + delta
        if (from < 0 || to !in order.indices || order[from].pinned != order[to].pinned) return false
        order.add(to, order.removeAt(from))
        commit()
        return true
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        items(order, key = { it.symbol }) { item ->
            val isDragging = item.symbol == dragging
            EditRow(
                item = item,
                dragging = isDragging,
                handle = Modifier.pointerInput(item.symbol) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = item.symbol
                            offset = 0f
                        },
                        onDragEnd = {
                            dragging = null
                            offset = 0f
                            commit()
                        },
                        onDragCancel = {
                            dragging = null
                            offset = 0f
                            commit()
                        },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val visible = listState.layoutInfo.visibleItemsInfo
                        val current = visible.firstOrNull { it.key == item.symbol } ?: return@detectDragGestures
                        val center = current.offset + current.size / 2f + offset
                        val target = visible.firstOrNull {
                            it.key != item.symbol && center > it.offset && center < it.offset + it.size
                        } ?: return@detectDragGestures
                        val from = latestOrder.indexOfFirst { it.symbol == item.symbol }
                        val to = latestOrder.indexOfFirst { it.symbol == target.key }
                        if (from < 0 || to < 0 || latestOrder[from].pinned != latestOrder[to].pinned) return@detectDragGestures
                        latestOrder.add(to, latestOrder.removeAt(from))
                        offset += current.offset - target.offset
                    }
                },
                onPin = { vm.togglePin(item.symbol) },
                onRemove = { vm.remove(item.symbol) },
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) offset else 0f }
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .padding(vertical = 4.dp)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(moveUp) { moveBy(item.symbol, -1) },
                            CustomAccessibilityAction(moveDown) { moveBy(item.symbol, 1) },
                        )
                    },
            )
        }
        item(key = "note") {
            Text(
                stringResource(R.string.edit_footer),
                style = MaterialTheme.typography.bodySmall,
                color = LocalPalette.current.text3,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun EditRow(item: EditItem, dragging: Boolean, handle: Modifier, onPin: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    val palette = LocalPalette.current
    val reorder = stringResource(R.string.reorder, item.display)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Row(Modifier.height(68.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                handle.size(52.dp, 68.dp).semantics { contentDescription = reorder },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(R.drawable.ic_grip, null, tint = palette.text3)
            }
            Column(Modifier.weight(1f)) {
                Text(item.display, style = MaterialTheme.typography.titleSmall)
                Text(item.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPin) {
                AppIcon(
                    if (item.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin,
                    stringResource(if (item.pinned) R.string.unpin else R.string.pin, item.display),
                    tint = if (item.pinned) MaterialTheme.colorScheme.primary else palette.text3,
                )
            }
            IconButton(onClick = onRemove) {
                AppIcon(R.drawable.ic_remove, stringResource(R.string.remove_stock, item.display), tint = palette.down)
            }
        }
    }
}
