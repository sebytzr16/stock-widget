package io.github.sebytzr16.stockwidget.system

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.sebytzr16.stockwidget.MainActivity
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.UserData
import io.github.sebytzr16.stockwidget.core.displaySymbol

/** Long-press app-icon shortcuts: Search plus up to three pinned stocks. */
object Shortcuts {

    fun update(context: Context, user: UserData) {
        val search = ShortcutInfoCompat.Builder(context, "search")
            .setShortLabel(context.getString(R.string.shortcut_search))
            .setLongLabel(context.getString(R.string.shortcut_search_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_search))
            .setIntent(MainActivity.searchIntent(context))
            .setRank(0)
            .build()
        val stocks = user.items.filter { it.pinned }.take(3).mapIndexed { i, item ->
            ShortcutInfoCompat.Builder(context, "stock_${item.symbol}")
                .setShortLabel(displaySymbol(item.symbol))
                .setLongLabel(item.name.ifBlank { displaySymbol(item.symbol) })
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_stock))
                .setIntent(MainActivity.openSymbolIntent(context, item.symbol))
                .setRank(i + 1)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, listOf(search) + stocks) }
    }
}
