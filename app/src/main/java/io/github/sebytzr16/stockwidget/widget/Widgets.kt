package io.github.sebytzr16.stockwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.sebytzr16.stockwidget.repository
import io.github.sebytzr16.stockwidget.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The six widgets and how many stocks each one is set up with (0: no setup needed). */
enum class WidgetKind(val picks: Int) {
    LIST(0),
    SINGLE(1),
    TWO(2),
    STRIP(4),
    PORTFOLIO(0),
    CHART(1);

    val provider: Class<out AppWidgetProvider>
        get() = when (this) {
            LIST -> ListWidget::class.java
            SINGLE -> SingleWidget::class.java
            TWO -> TwoWidget::class.java
            STRIP -> StripWidget::class.java
            PORTFOLIO -> PortfolioWidget::class.java
            CHART -> ChartWidget::class.java
        }

    companion object {
        fun of(info: AppWidgetProviderInfo?): WidgetKind? =
            info?.provider?.className?.let { name -> entries.firstOrNull { it.provider.name == name } }
    }
}

/**
 * Shared behavior: draw from the cached data, then ask for fresh prices when they're old.
 * Rendering runs off the main thread via goAsync(). Nothing polls on a timer of its own;
 * RefreshWorker and the app push updates through [WidgetUpdater].
 */
abstract class StockWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            RefreshWorker.refreshNow(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = background {
        ids.forEach { WidgetRenderer.render(context, manager, kind, it) }
        val market = context.repository.market.value
        if (System.currentTimeMillis() - market.lastAttempt > STALE_AFTER) RefreshWorker.refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = background {
        WidgetRenderer.render(context, manager, kind, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) = background {
        context.repository.removeWidgets(ids.toList())
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) = background {
        context.repository.remapWidgets(oldIds, newIds)
    }

    private fun background(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "io.github.sebytzr16.stockwidget.REFRESH"
        private const val STALE_AFTER = 15 * 60_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

class ListWidget : StockWidget(WidgetKind.LIST)
class SingleWidget : StockWidget(WidgetKind.SINGLE)
class TwoWidget : StockWidget(WidgetKind.TWO)
class StripWidget : StockWidget(WidgetKind.STRIP)
class PortfolioWidget : StockWidget(WidgetKind.PORTFOLIO)
class ChartWidget : StockWidget(WidgetKind.CHART)

/** Redraws every placed widget from the repository's current data. */
object WidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (kind in WidgetKind.entries) {
            for (id in manager.getAppWidgetIds(ComponentName(context, kind.provider))) {
                runCatching { WidgetRenderer.render(context, manager, kind, id) }
            }
        }
    }
}
