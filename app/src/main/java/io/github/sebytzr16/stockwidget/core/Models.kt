package io.github.sebytzr16.stockwidget.core

/** A point in a price series: epoch millis and price. */
data class PricePoint(val time: Long, val price: Double)

/** A trading window in epoch millis. */
data class Session(val start: Long, val end: Long) {
    operator fun contains(t: Long): Boolean = t >= start && t < end
}

/**
 * The latest quote for a symbol plus today's regular-session series. A single Yahoo chart
 * call fills all of it, and it's cached so the app and widgets render instantly, offline.
 */
data class Quote(
    val symbol: String,
    val name: String = symbol,
    val price: Double = 0.0,
    val previousClose: Double = 0.0,
    val open: Double = 0.0,
    val dayHigh: Double = 0.0,
    val dayLow: Double = 0.0,
    val volume: Long = 0L,
    val yearHigh: Double = 0.0,
    val yearLow: Double = 0.0,
    /** Yahoo currency code; minor units are kept as reported (e.g. "GBp" for pence). */
    val currency: String = "",
    val exchange: String = "",
    /** Yahoo instrument type: EQUITY, ETF, INDEX, CRYPTOCURRENCY, CURRENCY, FUTURE, … */
    val type: String = "",
    /** Decimals Yahoo suggests for this instrument; -1 when unknown. */
    val priceHint: Int = -1,
    val marketTime: Long = 0L,
    val fetchedAt: Long = 0L,
    val gmtOffsetSec: Int = 0,
    val pre: Session? = null,
    val regular: Session? = null,
    val post: Session? = null,
    /** Latest pre-market or after-hours price, when the exchange reports one. */
    val extPrice: Double? = null,
    val extTime: Long = 0L,
    /** Today's regular-session prices, downsampled for storage. */
    val intraday: List<PricePoint> = emptyList(),
) {
    val hasPrice: Boolean get() = price > 0.0

    /** Change vs. the previous close, the same baseline Yahoo, Google and brokers use. */
    val change: Double get() = if (previousClose > 0.0 && hasPrice) price - previousClose else 0.0
    val changePercent: Double
        get() = if (previousClose > 0.0 && hasPrice) change / previousClose * 100.0 else 0.0
    val isUp: Boolean get() = change >= 0.0

    val isCrypto: Boolean get() = type == "CRYPTOCURRENCY"
    val isCurrency: Boolean get() = type == "CURRENCY"
    val isIndex: Boolean get() = type == "INDEX" || symbol.startsWith("^")
    val displaySymbol: String get() = displaySymbol(symbol)
}

/** Ticker for display: index tickers like "^GSPC" show without the caret. */
fun displaySymbol(symbol: String): String = symbol.removePrefix("^").uppercase()

data class WatchItem(val symbol: String, val name: String, val pinned: Boolean = false)

/** What the user owns of a symbol, priced in the quote's currency (pence for "GBp"). */
data class Holding(val shares: Double, val avgCost: Double)

enum class AlertKind { ABOVE, BELOW, MOVE }

data class PriceAlert(
    val id: Long,
    val symbol: String,
    val kind: AlertKind,
    /** A price for ABOVE/BELOW, a percentage for MOVE. */
    val value: Double,
    val enabled: Boolean = true,
    /** Exchange-local day a MOVE alert last fired, so it fires at most once a day. */
    val lastFiredDay: String? = null,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortMode { CUSTOM, NAME, CHANGE, PRICE, VALUE }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val refreshMinutes: Int = 30,
    val onlyWhenOpen: Boolean = true,
    val extendedHours: Boolean = true,
    val alertsEnabled: Boolean = true,
    /** Currency portfolio totals are shown in; empty until first launch picks the device's. */
    val homeCurrency: String = "",
    val sort: SortMode = SortMode.CUSTOM,
    val indices: List<String> = DEFAULT_INDICES,
) {
    companion object {
        val DEFAULT_INDICES = listOf("^GSPC", "^IXIC", "^DJI")
    }
}

/** Everything the user owns. Small, and saved whenever it changes. */
data class UserData(
    val items: List<WatchItem> = emptyList(),
    val holdings: Map<String, Holding> = emptyMap(),
    val alerts: List<PriceAlert> = emptyList(),
    val notes: Map<String, String> = emptyMap(),
    /** Widget id to the symbols it shows (single, two-stock, strip and chart widgets). */
    val widgets: Map<Int, List<String>> = emptyMap(),
    val settings: Settings = Settings(),
)

/** The last fetched market data. Rebuilt on every refresh. */
data class MarketData(
    val quotes: Map<String, Quote> = emptyMap(),
    /** Exchange rates keyed like "USDEUR": how many EUR one USD buys. */
    val fx: Map<String, Double> = emptyMap(),
    val lastSuccess: Long = 0L,
    val lastAttempt: Long = 0L,
    val lastError: String? = null,
)

data class SearchResult(val symbol: String, val name: String, val exchange: String, val type: String)

data class NewsItem(val title: String, val publisher: String, val link: String, val time: Long)

/** Detail-screen chart ranges and the Yahoo range/interval that backs each. */
enum class ChartRange(val label: String, val range: String, val interval: String) {
    D1("1D", "1d", "5m"),
    W1("1W", "5d", "15m"),
    M1("1M", "1mo", "60m"),
    M3("3M", "3mo", "1d"),
    M6("6M", "6mo", "1d"),
    YTD("YTD", "ytd", "1d"),
    Y1("1Y", "1y", "1d"),
    Y5("5Y", "5y", "1wk"),
    MAX("Max", "max", "1mo"),
}

/** A chart for one range: its points and the price its change is measured from. */
data class RangeSeries(val points: List<PricePoint>, val reference: Double)
