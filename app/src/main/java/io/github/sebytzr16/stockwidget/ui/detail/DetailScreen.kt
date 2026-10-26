package io.github.sebytzr16.stockwidget.ui.detail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.ChartRange
import io.github.sebytzr16.stockwidget.core.Fmt
import io.github.sebytzr16.stockwidget.core.MarketPhase
import io.github.sebytzr16.stockwidget.core.SearchResult
import io.github.sebytzr16.stockwidget.core.Series
import io.github.sebytzr16.stockwidget.core.displaySymbol
import io.github.sebytzr16.stockwidget.system.Notifier
import io.github.sebytzr16.stockwidget.ui.AppViewModel
import io.github.sebytzr16.stockwidget.ui.ChartLoad
import io.github.sebytzr16.stockwidget.ui.DetailUi
import io.github.sebytzr16.stockwidget.ui.NewsLoad
import io.github.sebytzr16.stockwidget.ui.StatUi
import io.github.sebytzr16.stockwidget.ui.components.AppIcon
import io.github.sebytzr16.stockwidget.ui.components.CardTitle
import io.github.sebytzr16.stockwidget.ui.components.LabeledValue
import io.github.sebytzr16.stockwidget.ui.components.PriceChart
import io.github.sebytzr16.stockwidget.ui.components.RowDivider
import io.github.sebytzr16.stockwidget.ui.components.SectionCard
import io.github.sebytzr16.stockwidget.ui.components.trendColor
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, symbol: String, showBack: Boolean, onBack: () -> Unit) {
    LaunchedEffect(symbol) { vm.select(symbol) }
    val detail by vm.detail.collectAsStateWithLifecycle()
    val chart by vm.chart.collectAsStateWithLifecycle()
    val news by vm.news.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    var sheet by rememberSaveable(symbol) { mutableStateOf<String?>(null) }
    var scrub by remember(symbol, range, chart) { mutableStateOf<Int?>(null) }
    val d = detail?.takeIf { it.symbol == symbol }
    val background = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.message(R.string.msg_notifications_denied)
    }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(d?.display ?: displaySymbol(symbol), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            AppIcon(R.drawable.ic_back, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                actions = {
                    if (d?.quote != null) {
                        IconButton(onClick = { sheet = "alert" }) { AppIcon(R.drawable.ic_bell, stringResource(R.string.price_alerts)) }
                        IconButton(onClick = { sheet = "share" }) { AppIcon(R.drawable.ic_share, stringResource(R.string.share)) }
                    }
                    if (d != null) {
                        if (d.inList) {
                            IconButton(onClick = { vm.togglePin(symbol) }) {
                                AppIcon(
                                    if (d.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin,
                                    stringResource(if (d.pinned) R.string.unpin else R.string.pin, d.display),
                                    tint = if (d.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            IconButton(onClick = { vm.add(SearchResult(symbol, d.quote?.name ?: d.display, "", "")) }) {
                                AppIcon(R.drawable.ic_add, stringResource(R.string.add_symbol, d.display))
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
            )
        },
    ) { padding ->
        if (d == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header(d, chart, range, scrub)
            ChartSection(d, chart, range, onScrub = { scrub = it })
            RangePicker(range, vm::setRange)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (d.canHold && d.quote != null) PositionCard(d, onEdit = { sheet = "hold" })
                StatsCard(d)
                AlertsCard(d, alertsOn = user.settings.alertsEnabled, onAdd = { sheet = "alert" }, onToggle = vm::setAlertEnabled, onDelete = vm::deleteAlert)
                NotesCard(d.note, onSave = { vm.setNote(symbol, it) })
            }
            NewsSection(news)
            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.labelSmall,
                color = LocalPalette.current.text3,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            )
        }
    }

    val q = d?.quote
    when (sheet) {
        "hold" -> if (d != null && q != null) {
            HoldingSheet(d, q, onSave = { vm.setHolding(symbol, it); sheet = null }, onDismiss = { sheet = null })
        }
        "alert" -> if (q != null) {
            AlertSheet(
                q,
                onCreate = { kind, value ->
                    vm.addAlert(symbol, kind, value)
                    sheet = null
                    if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(context)) {
                        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onDismiss = { sheet = null },
            )
        }
        "share" -> if (q != null) {
            ShareSheet(q, onCopied = { if (Build.VERSION.SDK_INT < 33) vm.message(R.string.msg_copied) }, onDismiss = { sheet = null })
        }
    }
}

@Composable
private fun Header(d: DetailUi, chart: ChartLoad, range: ChartRange, scrub: Int?) {
    val context = LocalContext.current
    val q = d.quote
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(d.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (q == null) {
            Text("—", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 6.dp))
            Text(stringResource(R.string.no_data), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        // Until a range has loaded, the header keeps showing today's move.
        val series = (chart as? ChartLoad.Ready)?.series
        val point = scrub?.let { series?.points?.getOrNull(it) }
        val price = point?.price ?: q.price
        val reference = series?.reference ?: q.previousClose
        val delta = price - reference
        val pct = if (reference > 0.0) delta / reference * 100.0 else 0.0
        val color = trendColor(delta >= 0.0)
        val secondary = MaterialTheme.colorScheme.onSurfaceVariant
        val lastTime = series?.points?.lastOrNull()?.time
        val suffix = when {
            point != null -> pointLabel(context, point.time, range, axis = false)
            series == null -> stringResource(R.string.range_today)
            // Before the open the 1D chart is the last session: name its day.
            range == ChartRange.D1 && lastTime != null && !DateUtils.isToday(lastTime) -> DateUtils.formatDateTime(
                context, lastTime,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR,
            )
            else -> stringResource(rangeCaption(range))
        }
        Text(Fmt.price(price, q.currency, q.priceHint), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 6.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) {
                    append(Fmt.signedPrice(delta, q.currency, Fmt.priceDecimals(q.price, q.priceHint)) + " (" + Fmt.percent(pct) + ")")
                }
                withStyle(SpanStyle(color = secondary)) { append("  $suffix") }
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (point == null && range == ChartRange.D1) {
            d.ext?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary, modifier = Modifier.padding(top = 2.dp)) }
        }
        d.status?.let { StatusChip(it, d.statusPhase) }
    }
}

@Composable
private fun StatusChip(text: String, phase: MarketPhase?) {
    val palette = LocalPalette.current
    val dot = when (phase) {
        MarketPhase.OPEN, MarketPhase.ALWAYS -> palette.up
        MarketPhase.PRE, MarketPhase.POST -> MaterialTheme.colorScheme.primary
        else -> palette.text3
    }
    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(top = 10.dp)) {
        Row(Modifier.height(26.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartSection(d: DetailUi, chart: ChartLoad, range: ChartRange, onScrub: (Int?) -> Unit) {
    val context = LocalContext.current
    val q = d.quote
    val text3 = LocalPalette.current.text3
    Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(232.dp), contentAlignment = Alignment.Center) {
        when (chart) {
            is ChartLoad.Ready -> {
                val prices = remember(chart) { chart.series.points.map { it.price } }
                val positions = remember(chart, range) {
                    if (range == ChartRange.D1) Series.sessionPositions(chart.series.points, q?.regular) else null
                }
                val reference = chart.series.reference
                PriceChart(
                    prices = prices,
                    reference = reference,
                    showReference = range == ChartRange.D1,
                    up = (q?.price ?: prices.last()) >= reference,
                    onScrub = onScrub,
                    modifier = Modifier.fillMaxSize(),
                    positions = positions,
                )
            }
            ChartLoad.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            ChartLoad.Failed -> Text(stringResource(R.string.chart_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            ChartLoad.Empty -> Text(stringResource(R.string.chart_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
    val points = (chart as? ChartLoad.Ready)?.series?.points
    if (points != null && points.size >= 2) {
        // A one-day chart spans its whole session; other ranges span their points.
        val session = if (range == ChartRange.D1) Series.sessionOf(points, q?.regular) else null
        val times = if (session != null) listOf(session.start, (session.start + session.end) / 2, session.end)
        else listOf(points.first().time, points[points.size / 2].time, points.last().time)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            times.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.weight(1f))
                Text(pointLabel(context, t, range, axis = true), style = MaterialTheme.typography.labelSmall, color = text3)
            }
        }
    }
}

@Composable
private fun RangePicker(selected: ChartRange, onPick: (ChartRange) -> Unit) {
    Row(
        Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ChartRange.entries.forEach { r ->
            val on = r == selected
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .selectable(selected = on, onClick = { onPick(r) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PositionCard(d: DetailUi, onEdit: () -> Unit) {
    val p = d.position
    SectionCard {
        CardTitle(stringResource(R.string.your_position)) {
            if (p != null) TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
        }
        if (p == null) {
            Text(
                stringResource(R.string.position_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            FilledTonalButton(onClick = onEdit) {
                AppIcon(R.drawable.ic_add, null, Modifier.size(18.dp), tint = LocalContentColor.current)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_position))
            }
        } else {
            Row(Modifier.padding(top = 4.dp)) {
                LabeledValue(stringResource(R.string.holding), p.holding, Modifier.weight(1f))
                LabeledValue(stringResource(R.string.avg_cost), p.avgCost, Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 12.dp)) {
                LabeledValue(stringResource(R.string.market_value), p.value, Modifier.weight(1f))
                LabeledValue(stringResource(R.string.today), p.day, Modifier.weight(1f), valueColor = trendColor(p.dayUp))
            }
            LabeledValue(stringResource(R.string.total_return), p.total, Modifier.padding(top = 12.dp), valueColor = trendColor(p.totalUp))
        }
    }
}

/** Key stats as small rounded tiles, two per row. */
@Composable
private fun StatsCard(d: DetailUi) {
    SectionCard {
        CardTitle(stringResource(R.string.key_stats))
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.stats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { StatTile(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(stat: StatUi, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(stat.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stat.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = stat.up?.let { trendColor(it) } ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AlertsCard(d: DetailUi, alertsOn: Boolean, onAdd: () -> Unit, onToggle: (Long, Boolean) -> Unit, onDelete: (Long) -> Unit) {
    val text3 = LocalPalette.current.text3
    SectionCard {
        CardTitle(stringResource(R.string.price_alerts)) {
            if (d.quote != null) {
                TextButton(onClick = onAdd) {
                    AppIcon(R.drawable.ic_add, null, Modifier.size(18.dp), tint = LocalContentColor.current)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.add))
                }
            }
        }
        if (!alertsOn) {
            Text(stringResource(R.string.alerts_off_hint), style = MaterialTheme.typography.bodySmall, color = text3, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (d.alerts.isEmpty()) {
            Text(stringResource(R.string.alerts_empty, d.display), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.alerts.forEach { a ->
            RowDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = MaterialTheme.typography.bodyLarge)
                    Text(a.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDelete(a.id) }) { AppIcon(R.drawable.ic_trash, stringResource(R.string.delete_alert), Modifier.size(20.dp), tint = text3) }
                Switch(checked = a.enabled, onCheckedChange = { onToggle(a.id, it) })
            }
        }
    }
}

@Composable
private fun NotesCard(note: String, onSave: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable(note) { mutableStateOf(note) }
    SectionCard {
        CardTitle(stringResource(R.string.notes)) {
            TextButton(onClick = {
                if (editing) onSave(text)
                editing = !editing
            }) {
                Text(stringResource(if (editing) R.string.done else if (note.isBlank()) R.string.add else R.string.edit))
            }
        }
        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp),
            )
        } else {
            Text(
                note.ifBlank { stringResource(R.string.note_hint) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (note.isBlank()) LocalPalette.current.text3 else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NewsSection(news: NewsLoad) {
    val context = LocalContext.current
    val text3 = LocalPalette.current.text3
    Column(Modifier.padding(top = 20.dp)) {
        Text(stringResource(R.string.news), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        when (news) {
            NewsLoad.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
            NewsLoad.Failed -> Text(stringResource(R.string.news_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            is NewsLoad.Ready -> {
                if (news.items.isEmpty()) {
                    Text(stringResource(R.string.news_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                news.items.forEachIndexed { i, n ->
                    if (i > 0) Box(Modifier.padding(horizontal = 20.dp)) { RowDivider() }
                    Row(
                        Modifier.fillMaxWidth().clickable { openLink(context, n.link) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            val ago = if (n.time > 0L) DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString() else ""
                            Text(listOf(n.publisher, ago).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = text3, modifier = Modifier.padding(top = 4.dp))
                        }
                        AppIcon(R.drawable.ic_external, null, Modifier.padding(top = 3.dp).size(16.dp), tint = text3)
                    }
                }
            }
        }
    }
}

private fun openLink(context: Context, link: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
}

private fun rangeCaption(range: ChartRange): Int = when (range) {
    ChartRange.D1 -> R.string.range_today
    ChartRange.W1 -> R.string.range_week
    ChartRange.M1 -> R.string.range_month
    ChartRange.M3 -> R.string.range_3_months
    ChartRange.M6 -> R.string.range_6_months
    ChartRange.YTD -> R.string.range_ytd
    ChartRange.Y1 -> R.string.range_year
    ChartRange.Y5 -> R.string.range_5_years
    ChartRange.MAX -> R.string.range_max
}

/** Time or date of a chart point, detailed for the header and short for the axis. */
private fun pointLabel(context: Context, time: Long, range: ChartRange, axis: Boolean): String {
    if (axis && (range == ChartRange.Y5 || range == ChartRange.MAX)) {
        return Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.YEAR).toString()
    }
    val flags = when (range) {
        ChartRange.D1 -> DateUtils.FORMAT_SHOW_TIME
        ChartRange.W1 -> if (axis) DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
        else DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_TIME
        ChartRange.M1, ChartRange.M3, ChartRange.M6, ChartRange.YTD ->
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
        ChartRange.Y1 -> if (axis) DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_MONTH_DAY or DateUtils.FORMAT_SHOW_YEAR
        else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR
        ChartRange.Y5, ChartRange.MAX -> DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR
    }
    return DateUtils.formatDateTime(context, time, flags)
}
