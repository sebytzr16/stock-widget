package io.github.sebytzr16.stockwidget.core

import java.util.Locale

/** Popular indices: surfaced first in search and offered for the indices strip. */
object Indices {

    data class Info(val symbol: String, val name: String, val short: String, val keywords: List<String>)

    val POPULAR = listOf(
        Info("^GSPC", "S&P 500", "S&P 500", listOf("s&p", "sp", "sp500", "s&p 500", "spx", "500")),
        Info("^IXIC", "NASDAQ Composite", "NASDAQ", listOf("nasdaq", "ixic", "composite")),
        Info("^DJI", "Dow Jones Industrial Average", "Dow", listOf("dow", "dji", "jones", "industrial")),
        Info("^RUT", "Russell 2000", "Russell 2000", listOf("russell", "rut", "2000")),
        Info("^VIX", "CBOE Volatility Index", "VIX", listOf("vix", "volatility")),
        Info("^FTSE", "FTSE 100", "FTSE 100", listOf("ftse", "footsie", "uk 100")),
        Info("^GDAXI", "DAX", "DAX", listOf("dax", "german")),
        Info("^FCHI", "CAC 40", "CAC 40", listOf("cac", "cac 40", "french")),
        Info("^STOXX50E", "Euro Stoxx 50", "Euro Stoxx 50", listOf("stoxx", "eurostoxx", "euro stoxx")),
        Info("FTSEMIB.MI", "FTSE MIB", "FTSE MIB", listOf("mib", "ftse mib", "italy", "milan")),
        Info("^IBEX", "IBEX 35", "IBEX 35", listOf("ibex", "spain", "madrid")),
        Info("^N225", "Nikkei 225", "Nikkei", listOf("nikkei", "n225", "japan")),
        Info("^HSI", "Hang Seng", "Hang Seng", listOf("hang seng", "hsi", "hong kong")),
        Info("BTC-USD", "Bitcoin", "Bitcoin", listOf("btc", "bitcoin")),
    )

    fun short(symbol: String): String? = POPULAR.firstOrNull { it.symbol == symbol }?.short

    /**
     * Indices whose name, ticker or keyword starts with the query, so "S&P 500" reliably
     * finds ^GSPC while a company search like "spotify" doesn't surface an index.
     */
    fun matches(query: String): List<SearchResult> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        return POPULAR.filter { idx ->
            idx.name.lowercase(Locale.ROOT).contains(q) ||
                idx.symbol.removePrefix("^").lowercase(Locale.ROOT).startsWith(q) ||
                idx.keywords.any { it.startsWith(q) }
        }.map {
            SearchResult(it.symbol, it.name, if (it.symbol == "BTC-USD") "Crypto" else "Index",
                if (it.symbol == "BTC-USD") "CRYPTOCURRENCY" else "INDEX")
        }
    }
}
