package io.github.sebytzr16.stockwidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.displaySymbol
import io.github.sebytzr16.stockwidget.ui.components.AppIcon
import io.github.sebytzr16.stockwidget.ui.detail.DetailScreen
import io.github.sebytzr16.stockwidget.ui.home.HomeScreen
import io.github.sebytzr16.stockwidget.ui.search.SearchScreen
import io.github.sebytzr16.stockwidget.ui.settings.SettingsScreen
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Screens are plain string routes on a saveable back stack: "home", "search", "settings", "detail:AAPL". */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    private const val DETAIL = "detail:"

    fun detail(symbol: String) = DETAIL + symbol
    fun symbolOf(route: String): String? = if (route.startsWith(DETAIL)) route.removePrefix(DETAIL) else null
}

private val stackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/** Two panes (list and detail) from this width up, e.g. tablets and unfolded foldables. */
private val TwoPaneWidth = 720.dp

/**
 * The app's navigation. Phones show one screen at a time; wide windows keep the list on the
 * left with the selected stock beside it, and open search and settings over both.
 */
@Composable
fun AppRoot(vm: AppViewModel, initialRoute: String?, requests: Flow<String>) {
    val stack = rememberSaveable(saver = stackSaver) {
        listOfNotNull(Routes.HOME, initialRoute?.takeIf { it != Routes.HOME }).toMutableStateList()
    }
    val states = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun forget(route: String) {
        if (route !in stack) states.removeState(route)
    }

    fun push(route: String) {
        if (stack.last() != route) stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) forget(stack.removeAt(stack.lastIndex))
    }

    /** Opens a stock; a new pick replaces the one on top instead of piling up. */
    fun open(symbol: String) {
        val route = Routes.detail(symbol)
        if (Routes.symbolOf(stack.last()) != null) {
            val old = stack.removeAt(stack.lastIndex)
            stack.add(route)
            forget(old)
        } else push(route)
    }

    LaunchedEffect(requests) {
        requests.collect { route ->
            val old = stack.toList()
            stack.clear()
            stack.add(Routes.HOME)
            if (route != Routes.HOME) stack.add(route)
            old.forEach(::forget)
        }
    }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            // A new message replaces the one showing rather than queueing behind it.
            snackbar.currentSnackbarData?.dismiss()
            launch {
                when (m) {
                    is UiMessage.Removed -> {
                        val result = snackbar.showSnackbar(
                            message = context.getString(R.string.msg_removed, displaySymbol(m.stock.item.symbol)),
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) vm.undo(m.stock)
                    }
                    is UiMessage.Text -> snackbar.showSnackbar(
                        if (m.arg != null) context.getString(m.res, m.arg) else context.getString(m.res),
                    )
                }
            }
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    @Composable
    fun Screen(route: String, wide: Boolean, selected: String?) {
        states.SaveableStateProvider(route) {
            when (route) {
                Routes.HOME -> HomeScreen(
                    vm = vm,
                    selected = selected,
                    onOpen = ::open,
                    onSearch = { push(Routes.SEARCH) },
                    onSettings = { push(Routes.SETTINGS) },
                )
                Routes.SEARCH -> SearchScreen(vm, onBack = ::pop, onOpen = ::open)
                Routes.SETTINGS -> SettingsScreen(vm, onBack = ::pop)
                else -> Routes.symbolOf(route)?.let { DetailScreen(vm, it, showBack = !wide, onBack = ::pop) }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= TwoPaneWidth
        val top = stack.last()
        val selected = stack.lastOrNull { Routes.symbolOf(it) != null }?.let(Routes::symbolOf)
        // On wide windows, home and a detail share one "panes" screen.
        val shown = if (wide && (top == Routes.HOME || Routes.symbolOf(top) != null)) PANES else top
        val listWidth = (maxWidth * 0.4f).coerceIn(340.dp, 440.dp)

        // Keyed on the layout so a resize swaps layouts outright: animating between them would
        // compose the same screen twice.
        key(wide) {
            AnimatedContent(
                targetState = shown,
                transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                label = "screen",
            ) { screen ->
                if (screen == PANES) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(listWidth).fillMaxHeight()) { Screen(Routes.HOME, wide = true, selected = selected) }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (selected != null) Screen(Routes.detail(selected), wide = true, selected = selected) else NothingSelected()
                        }
                    }
                } else {
                    Screen(screen, wide = wide, selected = null)
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }
}

private const val PANES = "panes"

@Composable
private fun NothingSelected() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(R.drawable.ic_chart, null, Modifier.size(40.dp), tint = LocalPalette.current.text3)
        Text(
            stringResource(R.string.nothing_selected),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
