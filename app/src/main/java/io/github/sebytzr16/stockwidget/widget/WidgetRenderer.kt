package io.github.sebytzr16.stockwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import io.github.sebytzr16.stockwidget.MainActivity
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.Fmt
import io.github.sebytzr16.stockwidget.core.MarketData
import io.github.sebytzr16.stockwidget.core.Portfolio
import io.github.sebytzr16.stockwidget.core.Quote
import io.github.sebytzr16.stockwidget.core.Settings
import io.github.sebytzr16.stockwidget.core.UserData
import io.github.sebytzr16.stockwidget.core.displaySymbol
import io.github.sebytzr16.stockwidget.repository
import io.github.sebytzr16.stockwidget.ui.Ui

/**
 * Builds each widget's RemoteViews from the cached data. Layout colors come from resources
 * with night variants, so widgets follow the system theme; trend colors use a light and a
 * dark value on Android 12+ and a mid tone readable on both before that.
 */
object WidgetRenderer {

    private const val LIGHT_UP = 0xFF12783A.toInt()
    private const val LIGHT_DOWN = 0xFFC42B2F.toInt()
    private const val DARK_UP = 0xFF45C97A.toInt()
    private const val DARK_DOWN = 0xFFF2706F.toInt()

    /** Largest chart bitmap side in pixels, well inside the RemoteViews memory budget. */
    private const val MAX_BITMAP_SIDE = 1200

    fun render(context: Context, manager: AppWidgetManager, kind: WidgetKind, id: Int) {
        val repo = context.repository
        val u = repo.user.value
        val m = repo.market.value
        val views = when (kind) {
            WidgetKind.LIST -> list(context, id, u, m)
            WidgetKind.SINGLE, WidgetKind.TWO -> blocks(context, kind, id, u, m)
            WidgetKind.STRIP -> strip(context, kind, id, u, m)
            WidgetKind.PORTFOLIO -> portfolio(context, manager, id, u, m)
            WidgetKind.CHART -> chart(context, manager, kind, id, u, m)
        }
        views.setInt(android.R.id.background, "setBackgroundResource", background(u.settings))
        manager.updateAppWidget(id, views)
        if (kind == WidgetKind.LIST) {
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(id, R.id.list)
        }
    }

    // ---- Widgets ----------------------------------------------------------------------

    private fun list(context: Context, id: Int, u: UserData, m: MarketData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_list)
        val adapter = Intent(context, ListWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .setData(Uri.parse("stockwidget://list/$id"))
        @Suppress("DEPRECATION")
        views.setRemoteAdapter(R.id.list, adapter)
        views.setEmptyView(R.id.list, R.id.empty)
        views.setTextViewText(R.id.empty, context.getString(if (u.items.isEmpty()) R.string.widget_list_empty else R.string.widget_loading))
        views.setTextViewText(R.id.list_updated, updated(context, m))
        views.setPendingIntentTemplate(
            R.id.list,
            PendingIntent.getActivity(
                context, 0, MainActivity.openTemplateIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        )
        views.setOnClickPendingIntent(R.id.list_title, openApp(context))
        views.setOnClickPendingIntent(R.id.list_refresh, refresh(context, WidgetKind.LIST))
        return views
    }

    /** Single and two-stock widgets: one block per stock, stacked. */
    private fun blocks(context: Context, kind: WidgetKind, id: Int, u: UserData, m: MarketData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_blocks)
        val symbols = u.widgets[id].orEmpty().take(kind.picks)
        views.removeAllViews(R.id.content)
        if (symbols.isEmpty()) return unconfigured(context, views, kind, id)
        symbols.forEachIndexed { i, symbol ->
            if (i > 0) views.addView(R.id.content, RemoteViews(context.packageName, R.layout.widget_divider))
            val block = RemoteViews(context.packageName, R.layout.widget_block)
            fillBlock(context, block, symbol, m.quotes[symbol], u.items.firstOrNull { it.symbol == symbol }?.name)
            block.setOnClickPendingIntent(R.id.block, openSymbol(context, symbol))
            views.addView(R.id.content, block)
        }
        return views
    }

    private fun strip(context: Context, kind: WidgetKind, id: Int, u: UserData, m: MarketData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_strip)
        val symbols = u.widgets[id].orEmpty().take(kind.picks)
        views.removeAllViews(R.id.content)
        if (symbols.isEmpty()) return unconfigured(context, views, kind, id)
        symbols.forEachIndexed { i, symbol ->
            if (i > 0) views.addView(R.id.content, RemoteViews(context.packageName, R.layout.widget_divider_vertical))
            val cell = RemoteViews(context.packageName, R.layout.widget_strip_cell)
            val q = m.quotes[symbol]?.takeIf { it.hasPrice }
            cell.setTextViewText(R.id.symbol, displaySymbol(symbol))
            if (q != null) {
                cell.setTextViewText(R.id.price, Fmt.price(q.price, q.currency, q.priceHint))
                cell.setTextViewText(R.id.change, Fmt.percent(q.changePercent))
                trend(cell, R.id.change, q.isUp)
                cell.setContentDescription(R.id.cell, describe(q))
            } else {
                cell.setTextViewText(R.id.price, DASH)
                cell.setTextViewText(R.id.change, "")
            }
            cell.setOnClickPendingIntent(R.id.cell, openSymbol(context, symbol))
            views.addView(R.id.content, cell)
        }
        return views
    }

    private fun portfolio(context: Context, manager: AppWidgetManager, id: Int, u: UserData, m: MarketData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_portfolio)
        views.setOnClickPendingIntent(android.R.id.background, openApp(context))
        val s = Portfolio.summarize(u.holdings, m.quotes, m.fx, u.settings.homeCurrency)
        if (s == null) {
            views.setViewVisibility(R.id.content, View.GONE)
            views.setViewVisibility(R.id.empty, View.VISIBLE)
            views.setTextViewText(R.id.empty, context.getString(if (u.holdings.isEmpty()) R.string.widget_portfolio_empty else R.string.widget_loading))
            return views
        }
        views.setViewVisibility(R.id.content, View.VISIBLE)
        views.setViewVisibility(R.id.empty, View.GONE)
        val label = context.getString(R.string.widget_portfolio_label, s.currency)
        views.setTextViewText(R.id.label, if (s.complete) label else label + " · " + context.getString(R.string.widget_partial))
        views.setTextViewText(R.id.value, Fmt.money(s.value, s.currency))
        views.setTextViewText(R.id.day, Fmt.money(s.dayChange, s.currency, signed = true) + " (" + Fmt.percent(s.dayChangePercent) + ")")
        trend(views, R.id.day, s.dayChange >= 0.0)
        views.setTextViewText(R.id.total, Fmt.money(s.gain, s.currency, signed = true) + " (" + Fmt.percent(s.gainPercent) + ")")
        trend(views, R.id.total, s.gain >= 0.0)
        val (w, h) = sizeDp(manager, id, 250, 110)
        if (s.series.size >= 2) {
            views.setImageViewBitmap(R.id.chart, chartBitmap(context, s.series, s.value - s.dayChange, s.dayChange >= 0.0, w - 32, (h - 108).coerceAtLeast(32), 2f))
            views.setViewVisibility(R.id.chart, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.chart, View.INVISIBLE)
        }
        return views
    }

    private fun chart(context: Context, manager: AppWidgetManager, kind: WidgetKind, id: Int, u: UserData, m: MarketData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_chart)
        val symbol = u.widgets[id]?.firstOrNull() ?: return unconfigured(context, views, kind, id)
        views.setViewVisibility(R.id.content, View.VISIBLE)
        views.setViewVisibility(R.id.empty, View.GONE)
        views.setOnClickPendingIntent(android.R.id.background, openSymbol(context, symbol))
        val q = m.quotes[symbol]?.takeIf { it.hasPrice }
        views.setTextViewText(R.id.symbol, displaySymbol(symbol))
        views.setTextViewText(R.id.name, q?.name ?: u.items.firstOrNull { it.symbol == symbol }?.name.orEmpty())
        if (q == null) {
            views.setTextViewText(R.id.price, DASH)
            views.setTextViewText(R.id.change, "")
            views.setTextViewText(R.id.info, context.getString(R.string.widget_loading))
            views.setViewVisibility(R.id.chart, View.INVISIBLE)
            return views
        }
        views.setTextViewText(R.id.price, Fmt.price(q.price, q.currency, q.priceHint))
        views.setTextViewText(R.id.change, Ui.changeText(q))
        trend(views, R.id.change, q.isUp)
        views.setTextViewText(R.id.info, openPrevClose(context, q))
        views.setTextViewText(R.id.updated, updated(context, m))
        val (w, h) = sizeDp(manager, id, 250, 110)
        val prices = q.intraday.map { it.price }
        views.setImageViewBitmap(R.id.chart, chartBitmap(context, prices, q.previousClose.takeIf { it > 0.0 }, q.isUp, w - 32, (h - 96).coerceAtLeast(32), 2.25f))
        views.setViewVisibility(R.id.chart, View.VISIBLE)
        views.setContentDescription(android.R.id.background, describe(q))
        return views
    }

    // ---- Shared pieces ----------------------------------------------------------------

    /** Symbol and change, price, "Open · Prev close" and a small intraday chart. */
    fun fillBlock(context: Context, views: RemoteViews, symbol: String, quote: Quote?, fallbackName: String?) {
        val q = quote?.takeIf { it.hasPrice }
        views.setTextViewText(R.id.symbol, displaySymbol(symbol))
        if (q == null) {
            views.setTextViewText(R.id.change, "")
            views.setTextViewText(R.id.price, DASH)
            views.setTextViewText(R.id.info, fallbackName ?: context.getString(R.string.widget_loading))
            views.setViewVisibility(R.id.chart, View.INVISIBLE)
            return
        }
        views.setTextViewText(R.id.change, Fmt.percent(q.changePercent))
        trend(views, R.id.change, q.isUp)
        views.setTextViewText(R.id.price, Fmt.price(q.price, q.currency, q.priceHint))
        views.setTextViewText(R.id.info, openPrevClose(context, q))
        val prices = q.intraday.map { it.price }
        views.setImageViewBitmap(R.id.chart, chartBitmap(context, prices, q.previousClose.takeIf { it > 0.0 }, q.isUp, 96, 44, 1.75f))
        views.setViewVisibility(R.id.chart, View.VISIBLE)
        views.setContentDescription(R.id.block, describe(q))
    }

    private fun unconfigured(context: Context, views: RemoteViews, kind: WidgetKind, id: Int): RemoteViews {
        views.setViewVisibility(R.id.content, View.GONE)
        views.setViewVisibility(R.id.empty, View.VISIBLE)
        views.setTextViewText(R.id.empty, context.getString(if (kind.picks == 1) R.string.widget_pick_one else R.string.widget_pick_many))
        views.setOnClickPendingIntent(android.R.id.background, setup(context, id))
        return views
    }

    private fun openPrevClose(context: Context, q: Quote): String = context.getString(
        R.string.widget_open_prev_close,
        q.open.takeIf { it > 0.0 }?.let { Fmt.price(it, q.currency, q.priceHint) } ?: DASH,
        q.previousClose.takeIf { it > 0.0 }?.let { Fmt.price(it, q.currency, q.priceHint) } ?: DASH,
    )

    private fun describe(q: Quote): String =
        q.displaySymbol + ", " + Fmt.price(q.price, q.currency, q.priceHint) + ", " + Fmt.percent(q.changePercent)

    private fun updated(context: Context, m: MarketData): String {
        if (m.lastSuccess <= 0L) return ""
        val flags = if (DateUtils.isToday(m.lastSuccess)) DateUtils.FORMAT_SHOW_TIME
        else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
        return DateUtils.formatDateTime(context, m.lastSuccess, flags)
    }

    private fun background(settings: Settings): Int = when {
        settings.pureBlack -> R.drawable.widget_bg_black
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> R.drawable.widget_bg_dynamic
        else -> R.drawable.widget_bg
    }

    private fun trend(views: RemoteViews, id: Int, up: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            views.setColorInt(id, "setTextColor", if (up) LIGHT_UP else LIGHT_DOWN, if (up) DARK_UP else DARK_DOWN)
        } else {
            views.setTextColor(id, if (up) ChartBitmap.UP else ChartBitmap.DOWN)
        }
    }

    private fun chartBitmap(context: Context, prices: List<Double>, baseline: Double?, up: Boolean, widthDp: Int, heightDp: Int, strokeDp: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        return ChartBitmap.render(
            prices = prices,
            baseline = baseline,
            color = if (up) ChartBitmap.UP else ChartBitmap.DOWN,
            width = (widthDp * density).toInt().coerceIn(2, MAX_BITMAP_SIDE),
            height = (heightDp * density).toInt().coerceIn(2, MAX_BITMAP_SIDE),
            stroke = strokeDp * density,
        )
    }

    /** The widget's portrait size in dp, as the launcher reports it. */
    private fun sizeDp(manager: AppWidgetManager, id: Int, defaultWidth: Int, defaultHeight: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(id)
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return (if (w > 0) w else defaultWidth) to (if (h > 0) h else defaultHeight)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openSymbol(context: Context, symbol: String): PendingIntent = PendingIntent.getActivity(
        context, 0, MainActivity.openSymbolIntent(context, symbol),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setup(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
        context, id, WidgetSetupActivity.intent(context, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun refresh(context: Context, kind: WidgetKind): PendingIntent = PendingIntent.getBroadcast(
        context, kind.ordinal,
        Intent(context, kind.provider).setAction(StockWidget.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val DASH = "—"
}
