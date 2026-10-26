package io.github.sebytzr16.stockwidget.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CodecTest {

    private val user = UserData(
        items = listOf(WatchItem("AAPL", "Apple Inc.", pinned = true), WatchItem("^GSPC", "S&P 500")),
        holdings = mapOf("AAPL" to Holding(10.0, 197.3)),
        alerts = listOf(PriceAlert(7, "AAPL", AlertKind.MOVE, 3.0, enabled = true, lastFiredDay = "2026-10-26")),
        notes = mapOf("AAPL" to "Earnings soon\nTarget 240"),
        widgets = mapOf(42 to listOf("AAPL", "MSFT")),
        settings = Settings(
            theme = ThemeMode.DARK, pureBlack = true, dynamicColor = true, refreshMinutes = 60,
            onlyWhenOpen = false, extendedHours = false, alertsEnabled = false, homeCurrency = "EUR",
            sort = SortMode.CHANGE, indices = listOf("^FTSE"),
        ),
        widgetThemes = mapOf(42 to WidgetTheme.BLACK, 43 to WidgetTheme.LIGHT),
    )

    private val market = MarketData(
        quotes = mapOf(
            "AAPL" to Quote(
                symbol = "AAPL", name = "Apple Inc.", price = 227.48, previousClose = 224.69, open = 225.1,
                dayHigh = 228.95, dayLow = 223.8, volume = 48_200_000, yearHigh = 237.23, yearLow = 164.08,
                currency = "USD", exchange = "NASDAQ", type = "EQUITY", priceHint = 2, marketTime = 5L,
                fetchedAt = 6L, gmtOffsetSec = -14400, pre = Session(1, 2), regular = Session(2, 3),
                post = Session(3, 4), extPrice = 228.1, extTime = 9L,
                intraday = listOf(PricePoint(10, 225.1), PricePoint(20, 227.48)),
            )
        ),
        fx = mapOf("USDEUR" to 0.92),
        lastSuccess = 100L,
        lastAttempt = 101L,
        lastError = null,
    )

    @Test
    fun userRoundTrip() = assertEquals(user, Codec.decodeUser(Codec.encodeUser(user)))

    @Test
    fun marketRoundTrip() = assertEquals(market, Codec.decodeMarket(Codec.encodeMarket(market)))

    @Test
    fun brokenFilesFallBackToEmpty() {
        val dir = Files.createTempDirectory("store").toFile()
        File(dir, "user.json").writeText("{not json")
        val store = FileStore(dir)
        assertEquals(UserData(), store.loadUser())
        store.saveUser(user)
        assertEquals(user, FileStore(dir).loadUser())
        dir.deleteRecursively()
    }
}
