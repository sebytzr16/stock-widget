package io.github.sebytzr16.stockwidget.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import io.github.sebytzr16.stockwidget.repository
import io.github.sebytzr16.stockwidget.widget.WidgetKind
import io.github.sebytzr16.stockwidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Debug builds only: hosts the app's widgets on a wallpaper-like board, so screenshots of
 * them can be taken without driving a launcher (scripts/screenshots.sh). Binding needs
 * `adb shell appwidget grantbind --package <package>` first.
 */
class WidgetBoardActivity : Activity() {

    private class Spec(val kind: WidgetKind, val widthDp: Int, val heightDp: Int, val symbols: List<String> = emptyList())

    private lateinit var host: AppWidgetHost
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge, like a launcher: the wallpaper runs under the system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        host = AppWidgetHost(this, HOST_ID)
        host.deleteHost()
        val manager = AppWidgetManager.getInstance(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(88), dp(16), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF9DB9CF.toInt(), 0xFF4D7A99.toInt(), 0xFF27445C.toInt()),
            )
        }
        for (spec in if (intent.getStringExtra("board") == "2") BOARD_2 else BOARD_1) {
            val id = host.allocateAppWidgetId()
            if (!manager.bindAppWidgetIdIfAllowed(id, ComponentName(this, spec.kind.provider))) {
                column.addView(TextView(this).apply {
                    text = "Run: adb shell appwidget grantbind --package $packageName"
                    setTextColor(Color.WHITE)
                })
                continue
            }
            if (spec.symbols.isNotEmpty()) repository.setWidget(id, spec.symbols)
            val view = host.createView(applicationContext, id, manager.getAppWidgetInfo(id))
            view.setPadding(0, 0, 0, 0)
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(Bundle(), spec.widthDp, spec.heightDp, spec.widthDp, spec.heightDp)
            column.addView(view, LinearLayout.LayoutParams(dp(spec.widthDp), dp(spec.heightDp)).apply { bottomMargin = dp(16) })
        }
        setContentView(column)
        WidgetUpdater.updateAll(this)
        scope.launch(Dispatchers.Default) { repository.refresh(force = true) }
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        host.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val HOST_ID = 4242

        val BOARD_1 = listOf(
            Spec(WidgetKind.LIST, 379, 300),
            Spec(WidgetKind.STRIP, 379, 80, listOf("^GSPC", "AAPL", "NVDA", "BTC-USD")),
            Spec(WidgetKind.CHART, 379, 190, listOf("AAPL")),
        )

        val BOARD_2 = listOf(
            Spec(WidgetKind.PORTFOLIO, 379, 190),
            Spec(WidgetKind.TWO, 379, 190, listOf("MSFT", "TSLA")),
            Spec(WidgetKind.SINGLE, 379, 86, listOf("GOOGL")),
        )
    }
}
