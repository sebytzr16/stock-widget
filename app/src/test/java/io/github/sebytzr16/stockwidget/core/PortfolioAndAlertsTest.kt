package io.github.sebytzr16.stockwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioAndAlertsTest {

    private val aapl = Quote("AAPL", price = 227.48, previousClose = 224.69, currency = "USD", fetchedAt = 1L)
    private val vod = Quote("VOD.L", price = 70.42, previousClose = 69.58, currency = "GBp", fetchedAt = 1L)
    private val asml = Quote("ASML.AS", price = 702.40, previousClose = 711.80, currency = "EUR", fetchedAt = 1L)
    private val quotes = mapOf("AAPL" to aapl, "VOD.L" to vod, "ASML.AS" to asml)

    private val holdings = mapOf(
        "AAPL" to Holding(10.0, 197.30),
        "VOD.L" to Holding(100.0, 60.0), // pence
        "ASML.AS" to Holding(3.0, 640.0),
    )

    @Test
    fun penceHoldingsAreValuedInPounds() {
        val p = Portfolio.position(Holding(100.0, 60.0), vod)
        assertEquals("GBP", p.currency)
        assertEquals(70.42, p.value, 1e-9)
        assertEquals(60.0, p.cost, 1e-9)
    }

    @Test
    fun summaryConvertsToHomeCurrency() {
        val fx = mapOf("USDEUR" to 0.92, "GBPEUR" to 1.19)
        val s = Portfolio.summarize(holdings, quotes, fx, "EUR")
        assertNotNull(s)
        s!!
        val expected = 2274.8 * 0.92 + 70.42 * 1.19 + 2107.2
        assertEquals(expected, s.value, 1e-6)
        val day = 27.9 * 0.92 + 0.84 * 1.19 - 28.2
        assertEquals(day, s.dayChange, 1e-6)
        assertTrue(s.complete)
        assertEquals(setOf("USD", "GBP"), Portfolio.neededCurrencies(holdings, quotes, "EUR"))
    }

    @Test
    fun missingRateIsReportedNotGuessed() {
        val s = Portfolio.summarize(holdings, quotes, mapOf("EURUSD" to 1.0852), "USD")!!
        assertFalse(s.complete) // no GBP rate yet
        assertEquals(2274.8 + 2107.2 * 1.0852, s.value, 1e-6) // inverse rate used for EUR
    }

    @Test
    fun priceAlertsFireOnceAndSwitchOff() {
        val alerts = listOf(
            PriceAlert(1, "AAPL", AlertKind.ABOVE, 225.0),
            PriceAlert(2, "AAPL", AlertKind.BELOW, 200.0),
            PriceAlert(3, "AAPL", AlertKind.ABOVE, 230.0),
        )
        val (hits, updated) = AlertRules.evaluate(alerts, quotes)
        assertEquals(listOf(1L), hits.map { it.alert.id })
        assertFalse(updated[0].enabled)
        assertTrue(updated[1].enabled && updated[2].enabled)
    }

    @Test
    fun moveAlertsFireAtMostOncePerDay() {
        val alert = PriceAlert(4, "ASML.AS", AlertKind.MOVE, 1.0)
        val (hits, updated) = AlertRules.evaluate(listOf(alert), quotes)
        assertEquals(1, hits.size)
        val (again, _) = AlertRules.evaluate(updated, quotes)
        assertTrue(again.isEmpty())
    }
}
