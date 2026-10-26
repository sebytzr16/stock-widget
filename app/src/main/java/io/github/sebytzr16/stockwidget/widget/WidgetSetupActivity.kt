package io.github.sebytzr16.stockwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.displaySymbol
import io.github.sebytzr16.stockwidget.repository
import io.github.sebytzr16.stockwidget.ui.AppViewModel
import io.github.sebytzr16.stockwidget.ui.components.AppIcon
import io.github.sebytzr16.stockwidget.ui.theme.StockTheme

/**
 * Picks the stocks a widget shows: from the user's list, or any symbol found by search.
 * Opened by the launcher when a widget is placed or reconfigured, or by tapping an empty widget.
 */
class WidgetSetupActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out cancels placing the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val kind = WidgetKind.of(AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId))
        if (kind == null || kind.picks == 0) {
            finish()
            return
        }
        val initial = repository.user.value.widgets[widgetId].orEmpty()
        setContent {
            val user by vm.user.collectAsStateWithLifecycle()
            StockTheme(user.settings) {
                SetupScreen(
                    vm = vm,
                    picks = kind.picks,
                    initial = initial,
                    onDone = { symbols ->
                        repository.setWidget(widgetId, symbols)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, WidgetSetupActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("stockwidget://widget/$widgetId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private data class Choice(val symbol: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(vm: AppViewModel, picks: Int, initial: List<String>, onDone: (List<String>) -> Unit, onClose: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val picked = remember { mutableStateListOf<String>().apply { addAll(initial.take(picks)) } }
    val names = remember { mutableStateMapOf<String, String>() }
    DisposableEffect(Unit) { onDispose { vm.query.value = "" } }

    fun nameOf(symbol: String) = names[symbol] ?: user.items.firstOrNull { it.symbol == symbol }?.name.orEmpty()

    fun toggle(c: Choice) {
        names[c.symbol] = c.name
        when {
            c.symbol in picked -> picked.remove(c.symbol)
            picks == 1 -> {
                picked.clear()
                picked.add(c.symbol)
            }
            picked.size < picks -> picked.add(c.symbol)
        }
    }

    val choices: List<Choice> = if (query.isBlank()) {
        // Picks from an earlier search stay visible, ahead of the list.
        val inList = user.items.map { Choice(it.symbol, it.name) }
        picked.filter { s -> inList.none { it.symbol == s } }.map { Choice(it, nameOf(it)) } + inList
    } else {
        search.results.map { Choice(it.symbol, it.name) }
    }
    val background = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (picks == 1) stringResource(R.string.setup_title_one) else pluralStringResource(R.plurals.setup_title_many, picks, picks),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { AppIcon(R.drawable.ic_close, stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.onSurface) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
            )
        },
        bottomBar = {
            Surface(color = background) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (picks == 1) picked.firstOrNull()?.let(::displaySymbol).orEmpty()
                        else stringResource(R.string.setup_count, picked.size, picks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { onDone(picked.toList()) }, enabled = picked.isNotEmpty()) { Text(stringResource(R.string.setup_done)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 8.dp)) {
            item(key = "search") {
                TextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text(stringResource(R.string.setup_search_hint), maxLines = 1) },
                    leadingIcon = { AppIcon(R.drawable.ic_search, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.query.value = "" }) { AppIcon(R.drawable.ic_close, stringResource(R.string.clear_search), Modifier.size(18.dp)) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (search.loading && query.isNotBlank()) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            if (query.isBlank()) {
                item(key = "label") {
                    Text(
                        stringResource(if (user.items.isEmpty()) R.string.setup_empty else R.string.setup_your_list),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
            } else if (search.failed) {
                item(key = "offline") { Hint(stringResource(R.string.search_offline_text)) }
            } else if (!search.loading && search.query.isNotEmpty() && search.results.isEmpty()) {
                item(key = "none") { Hint(stringResource(R.string.search_none_title, search.query)) }
            }
            items(choices, key = { it.symbol }) { c ->
                val index = picked.indexOf(c.symbol)
                ChoiceRow(c, single = picks == 1, index = index, enabled = index >= 0 || picks == 1 || picked.size < picks) { toggle(c) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(c: Choice, single: Boolean, index: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = index >= 0, enabled = enabled, role = if (single) Role.RadioButton else Role.Checkbox, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(displaySymbol(c.symbol), style = MaterialTheme.typography.titleSmall)
            if (c.name.isNotBlank()) {
                Text(c.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            single -> RadioButton(selected = index >= 0, onClick = null)
            index >= 0 -> Box(
                Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            else -> Checkbox(checked = false, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    )
}
