package io.github.sebytzr16.stockwidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.sebytzr16.stockwidget.system.Shortcuts
import io.github.sebytzr16.stockwidget.ui.AppRoot
import io.github.sebytzr16.stockwidget.ui.AppViewModel
import io.github.sebytzr16.stockwidget.ui.Routes
import io.github.sebytzr16.stockwidget.ui.theme.StockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val requests = MutableSharedFlow<String>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        // After a configuration change the restored back stack wins over the launch intent.
        val initial = if (savedInstanceState == null) routeFor(intent) else null
        setContent {
            val user by vm.user.collectAsStateWithLifecycle()
            StockTheme(user.settings) {
                AppRoot(vm, initial, requests)
            }
        }
        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.Default) { Shortcuts.update(applicationContext, repository.user.value) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFor(intent)?.let { requests.tryEmit(it) }
    }

    override fun onResume() {
        super.onResume()
        vm.onForeground()
    }

    /**
     * Asks for the screen's fastest mode at the current resolution (120 Hz on most phones that
     * have it), so scrolling and the chart run smoothly instead of at the default 60 Hz.
     */
    private fun preferHighestRefreshRate() {
        @Suppress("DEPRECATION")
        val display = (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay) ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    private fun routeFor(intent: Intent?): String? = when (intent?.action) {
        ACTION_OPEN -> intent.getStringExtra(EXTRA_SYMBOL)?.takeIf { it.isNotBlank() }?.let(Routes::detail)
        ACTION_SEARCH -> Routes.SEARCH
        else -> null
    }

    companion object {
        const val ACTION_OPEN = "io.github.sebytzr16.stockwidget.OPEN"
        const val ACTION_SEARCH = "io.github.sebytzr16.stockwidget.SEARCH"
        private const val EXTRA_SYMBOL = "symbol"

        /** Opens a stock's detail. The data URI keeps PendingIntents for different stocks apart. */
        fun openSymbolIntent(context: Context, symbol: String): Intent =
            openTemplateIntent(context).setData(symbolUri(symbol)).putExtra(EXTRA_SYMBOL, symbol)

        /** The list widget's row-click template; each row adds its stock via [openFillIn]. */
        fun openTemplateIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun openFillIn(symbol: String): Intent = Intent().setData(symbolUri(symbol)).putExtra(EXTRA_SYMBOL, symbol)

        private fun symbolUri(symbol: String): Uri = Uri.parse("stockwidget://symbol/" + Uri.encode(symbol))

        fun searchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_SEARCH)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
