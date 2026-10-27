package io.github.sebytzr16.stockwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.github.sebytzr16.stockwidget.MainActivity
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.Quote
import io.github.sebytzr16.stockwidget.core.WatchItem
import io.github.sebytzr16.stockwidget.core.WidgetTheme
import io.github.sebytzr16.stockwidget.repository
import io.github.sebytzr16.stockwidget.ui.Ui

/** Rows for the list widget, in the same order as the app's list. */
class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

    private class Factory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {

        private var rows: List<Pair<WatchItem, Quote?>> = emptyList()
        private var colors: WidgetColors? = null
        private var chartWidthDp = 110

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val repo = context.repository
            val u = repo.user.value
            val m = repo.market.value
            rows = Ui.sorted(u, m).map { it to m.quotes[it.symbol] }
            colors = WidgetRenderer.colorsFor(u.widgetThemes[widgetId] ?: WidgetTheme.SYSTEM)
            // Rows share the width with their text like the single widget: 0.9 of 1.9.
            val width = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
            chartWidthDp = ((width - 42) * 0.9f / 1.9f).toInt()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_list_row)
            val (item, quote) = rows.getOrNull(position) ?: return views
            WidgetRenderer.fillBlock(context, views, item.symbol, quote, item.name, colors, chartWidthDp, 36)
            views.setOnClickFillInIntent(R.id.block, MainActivity.openFillIn(item.symbol))
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = rows.getOrNull(position)?.first?.symbol?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true

        override fun onDestroy() {
            rows = emptyList()
        }
    }
}
