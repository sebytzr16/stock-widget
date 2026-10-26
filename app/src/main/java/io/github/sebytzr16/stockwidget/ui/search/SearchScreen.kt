package io.github.sebytzr16.stockwidget.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.SearchResult
import io.github.sebytzr16.stockwidget.core.displaySymbol
import io.github.sebytzr16.stockwidget.ui.AppViewModel
import io.github.sebytzr16.stockwidget.ui.components.AppIcon
import io.github.sebytzr16.stockwidget.ui.home.SuggestionChips
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette

@Composable
fun SearchScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ui by vm.search.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val tracked = remember(user) { user.items.map { it.symbol }.toSet() }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { vm.query.value = "" } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    AppIcon(R.drawable.ic_back, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
                }
                TextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
                    leadingIcon = { AppIcon(R.drawable.ic_search, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.query.value = "" }) {
                                AppIcon(R.drawable.ic_close, stringResource(R.string.clear_search), Modifier.size(18.dp))
                            }
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            if (ui.loading) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            when {
                query.isBlank() -> item(key = "popular") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            stringResource(R.string.popular),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
                        )
                        SuggestionChips(onAdd = vm::add, exclude = { it in tracked })
                    }
                }
                ui.failed -> item(key = "offline") { Message(stringResource(R.string.search_offline_title), stringResource(R.string.search_offline_text)) }
                !ui.loading && ui.query.isNotEmpty() && ui.results.isEmpty() -> item(key = "none") {
                    Message(stringResource(R.string.search_none_title, ui.query), stringResource(R.string.search_none_text))
                }
                else -> Unit
            }
            items(if (query.isBlank()) emptyList() else ui.results, key = { it.symbol }) { r ->
                ResultRow(
                    result = r,
                    added = r.symbol in tracked,
                    onAdd = { vm.add(r) },
                    onOpen = { onOpen(r.symbol) },
                )
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, added: Boolean, onAdd: () -> Unit, onOpen: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth()
            .clickable { if (added) onOpen() else onAdd() }
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(displaySymbol(result.symbol), style = MaterialTheme.typography.titleSmall)
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(
                        stringResource(typeLabel(result.type)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                listOf(result.name, result.exchange).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (added) {
            IconButton(onClick = onOpen) {
                AppIcon(R.drawable.ic_check, stringResource(R.string.in_list, displaySymbol(result.symbol)), tint = palette.up)
            }
        } else {
            FilledTonalIconButton(onClick = onAdd, shape = CircleShape, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                AppIcon(R.drawable.ic_add, stringResource(R.string.add_symbol, displaySymbol(result.symbol)), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun Message(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun typeLabel(type: String): Int = when (type) {
    "ETF" -> R.string.type_etf
    "INDEX" -> R.string.type_index
    "MUTUALFUND" -> R.string.type_fund
    "CURRENCY" -> R.string.type_currency
    "CRYPTOCURRENCY" -> R.string.type_crypto
    "FUTURE" -> R.string.type_future
    else -> R.string.type_stock
}
