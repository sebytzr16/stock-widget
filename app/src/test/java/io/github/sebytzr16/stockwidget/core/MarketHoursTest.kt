package io.github.sebytzr16.stockwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MarketHoursTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    // Sessions cached on Friday 2026-10-23 for a New York listing (UTC-4).
    private val us = Quote(
        symbol = "AAPL",
        price = 1.0,
        type = "EQUITY",
        gmtOffsetSec = -14400,
        pre = Session(ms("2026-10-23T08:00:00Z"), ms("2026-10-23T13:30:00Z")),
        regular = Session(ms("2026-10-23T13:30:00Z"), ms("2026-10-23T20:00:00Z")),
        post = Session(ms("2026-10-23T20:00:00Z"), ms("2026-10-24T00:00:00Z")),
    )

    @Test
    fun projectsCachedSessionsOntoToday() {
        val s = MarketHours.status(us, ms("2026-10-26T15:00:00Z")) // Monday 11:00 New York
        assertEquals(MarketPhase.OPEN, s.phase)
        assertEquals(ms("2026-10-26T20:00:00Z"), s.until)
    }

    @Test
    fun preAndAfterHours() {
        assertEquals(MarketPhase.PRE, MarketHours.status(us, ms("2026-10-26T12:00:00Z")).phase)
        assertEquals(MarketPhase.POST, MarketHours.status(us, ms("2026-10-26T21:00:00Z")).phase)
    }

    @Test
    fun weekendIsClosedUntilMonday() {
        val s = MarketHours.status(us, ms("2026-10-24T16:00:00Z")) // Saturday
        assertEquals(MarketPhase.CLOSED, s.phase)
        assertEquals(ms("2026-10-26T13:30:00Z"), s.until)
    }

    @Test
    fun smartRefreshHasGraceAroundTheSession() {
        assertTrue(MarketHours.isActive(us, ms("2026-10-26T07:50:00Z")))  // 10 min before pre-market
        assertTrue(MarketHours.isActive(us, ms("2026-10-27T00:20:00Z")))  // 20 min after post ends
        assertFalse(MarketHours.isActive(us, ms("2026-10-27T03:00:00Z"))) // 23:00 New York
        assertFalse(MarketHours.isActive(us, ms("2026-10-25T15:00:00Z"))) // Sunday
    }

    @Test
    fun cryptoAndCurrencies() {
        val btc = Quote("BTC-USD", type = "CRYPTOCURRENCY")
        assertEquals(MarketPhase.ALWAYS, MarketHours.status(btc, ms("2026-10-25T03:00:00Z")).phase)
        assertTrue(MarketHours.isActive(btc, ms("2026-10-25T03:00:00Z")))
        val fx = Quote("EURUSD=X", type = "CURRENCY")
        assertTrue(MarketHours.isActive(fx, ms("2026-10-26T03:00:00Z")))
        assertFalse(MarketHours.isActive(fx, ms("2026-10-25T12:00:00Z")))
    }

    @Test
    fun anyActiveWhenNothingIsKnown() {
        assertTrue(MarketHours.anyActive(emptyList(), 0L))
        assertFalse(MarketHours.anyActive(listOf(us), ms("2026-10-25T15:00:00Z")))
    }
}
