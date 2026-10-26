package io.github.sebytzr16.stockwidget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class YahooParserTest {

    private fun sec(iso: String) = Instant.parse(iso).epochSecond

    // Monday 2026-10-26, New York (UTC-4): pre 04:00, regular 09:30–16:00, post 16:00–20:00.
    private val preStart = sec("2026-10-26T08:00:00Z")
    private val regStart = sec("2026-10-26T13:30:00Z")
    private val regEnd = sec("2026-10-26T20:00:00Z")
    private val postEnd = sec("2026-10-27T00:00:00Z")

    private fun chartJson(): String {
        val times = listOf(regStart - 600, regStart, regStart + 300, regStart + 600, regEnd - 300, regEnd + 300, regEnd + 600)
        return """
        {"chart":{"result":[{"meta":{"currency":"USD","symbol":"AAPL","exchangeName":"NMS",
          "fullExchangeName":"NasdaqGS","instrumentType":"EQUITY","regularMarketTime":$regEnd,
          "gmtoffset":-14400,"regularMarketPrice":227.48,"fiftyTwoWeekHigh":237.23,
          "fiftyTwoWeekLow":164.08,"regularMarketDayHigh":228.95,"regularMarketDayLow":223.8,
          "regularMarketVolume":48200000,"longName":"Apple Inc.","shortName":"Apple Inc.",
          "chartPreviousClose":224.69,"previousClose":224.69,"priceHint":2,
          "currentTradingPeriod":{
            "pre":{"timezone":"EDT","start":$preStart,"end":$regStart,"gmtoffset":-14400},
            "regular":{"timezone":"EDT","start":$regStart,"end":$regEnd,"gmtoffset":-14400},
            "post":{"timezone":"EDT","start":$regEnd,"end":$postEnd,"gmtoffset":-14400}}},
          "timestamp":[${times.joinToString(",")}],
          "indicators":{"quote":[{
            "open":[224.9,225.1,225.5,null,227.0,227.6,228.0],
            "high":[225.0,225.9,226.0,null,227.6,227.9,228.2],
            "low":[224.8,225.0,225.2,null,226.9,227.4,227.9],
            "close":[224.95,225.6,225.8,null,227.48,227.9,228.1],
            "volume":[0,100,100,null,100,0,0]}]}}],"error":null}}
        """.trimIndent()
    }

    @Test
    fun buildsQuoteFromOneDayChartWithPrePost() {
        val chart = YahooParser.parseChart(chartJson())
        val q = YahooParser.toQuote(chart, "AAPL", null, now = 1L)
        assertNotNull(q)
        q!!
        assertEquals(227.48, q.price, 1e-9)
        assertEquals(224.69, q.previousClose, 1e-9)
        assertEquals(225.1, q.open, 1e-9) // first regular bar, not the pre-market one
        assertEquals(2.79, q.change, 1e-9)
        assertEquals("NASDAQ", q.exchange)
        assertEquals("Apple Inc.", q.name)
        assertEquals(3, q.intraday.size) // regular bars only, null close skipped
        assertEquals(228.1, q.extPrice!!, 1e-9) // latest after-hours bar
        assertEquals((regEnd + 600) * 1000, q.extTime)
        assertEquals(regStart * 1000, q.regular!!.start)
        assertEquals(48_200_000L, q.volume)
    }

    @Test
    fun watchlistNameWinsOverYahooName() {
        val q = YahooParser.toQuote(YahooParser.parseChart(chartJson()), "AAPL", "Apple", now = 1L)
        assertEquals("Apple", q!!.name)
    }

    @Test
    fun unknownSymbolReportsYahooError() {
        val json = """{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}"""
        val chart = YahooParser.parseChart(json)
        assertEquals("No data found, symbol may be delisted", chart.error)
        assertNull(YahooParser.toQuote(chart, "XXXX", null, now = 1L))
    }

    @Test
    fun keepsPreviousValuesWhenResponseIsSparse() {
        val previous = Quote("AAPL", price = 220.0, previousClose = 219.0, yearHigh = 237.0, currency = "USD")
        val json = """{"chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":221.5}}],"error":null}}"""
        val q = YahooParser.toQuote(YahooParser.parseChart(json), "AAPL", null, 1L, previous)!!
        assertEquals(221.5, q.price, 1e-9)
        assertEquals(219.0, q.previousClose, 1e-9)
        assertEquals(237.0, q.yearHigh, 1e-9)
        assertEquals("USD", q.currency)
    }

    @Test
    fun rangeSeriesMeasuresFromCloseBeforeRange() {
        val json = """{"chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":227.48,"chartPreviousClose":190.0},
            "timestamp":[1,2,3],"indicators":{"quote":[{"close":[191.0,null,227.48]}]}}],"error":null}}"""
        val series = YahooParser.toRangeSeries(YahooParser.parseChart(json))!!
        assertEquals(190.0, series.reference, 1e-9)
        assertEquals(2, series.points.size)
    }

    @Test
    fun searchKeepsTradeableTypesOnly() {
        val json = """{"quotes":[
            {"symbol":"AAPL","shortname":"Apple Inc.","longname":"Apple Inc.","quoteType":"EQUITY","exchDisp":"NASDAQ"},
            {"symbol":"AAPL240119C00100000","quoteType":"OPTION","exchDisp":"OPR"},
            {"symbol":"^gspc","shortname":"S&P 500","quoteType":"INDEX","exchDisp":"SNP"},
            {"symbol":"BTC-USD","shortname":"Bitcoin USD","quoteType":"CRYPTOCURRENCY","exchDisp":"CCC"}]}"""
        val results = YahooParser.parseSearch(json)
        assertEquals(listOf("AAPL", "^GSPC", "BTC-USD"), results.map { it.symbol })
        assertEquals("S&P 500", results[1].name)
    }

    @Test
    fun parsesNews() {
        val json = """{"news":[{"title":"Apple edges higher","publisher":"Wire","link":"https://example.com/a",
            "providerPublishTime":1793000000},{"title":"No link"}]}"""
        val news = YahooParser.parseNews(json)
        assertEquals(1, news.size)
        assertEquals(1793000000000L, news[0].time)
        assertTrue(news[0].link.startsWith("https://"))
    }

    @Test
    fun beforeTheOpenShowsTheLastSession() {
        // Monday's session hasn't started; the chart holds Friday's bars, regular and after hours.
        val fri = regStart - 3 * 86_400
        val times = listOf(fri, fri + 300, fri + 600, regEnd - 3 * 86_400 + 300)
        val json = """
        {"chart":{"result":[{"meta":{"currency":"USD","symbol":"AAPL","regularMarketPrice":227.48,
          "previousClose":224.69,"gmtoffset":-14400,
          "currentTradingPeriod":{"regular":{"start":$regStart,"end":$regEnd}}},
          "timestamp":[${times.joinToString(",")}],
          "indicators":{"quote":[{"open":[225.0,225.5,226.0,227.6],"close":[225.4,225.9,227.48,227.9]}]}}],"error":null}}
        """.trimIndent()
        val q = YahooParser.toQuote(YahooParser.parseChart(json), "AAPL", null, now = 1L)!!
        assertEquals(listOf(225.4, 225.9, 227.48), q.intraday.map { it.price })
        assertEquals(225.0, q.open, 1e-9)
    }

    @Test
    fun keepsTheCachedSessionUntilTheOpen() {
        // Only today's pre-market bars so far, and none from the last session.
        val json = """
        {"chart":{"result":[{"meta":{"symbol":"AAPL","regularMarketPrice":227.48,
          "currentTradingPeriod":{"regular":{"start":$regStart,"end":$regEnd}}},
          "timestamp":[${regStart - 600},${regStart - 300}],
          "indicators":{"quote":[{"close":[228.0,228.2]}]}}],"error":null}}
        """.trimIndent()
        val cached = listOf(PricePoint(1L, 225.0), PricePoint(2L, 227.48))
        val previous = Quote("AAPL", price = 227.48, open = 225.1, intraday = cached)
        val q = YahooParser.toQuote(YahooParser.parseChart(json), "AAPL", null, 1L, previous)!!
        assertEquals(cached, q.intraday)
        assertEquals(225.1, q.open, 1e-9)
        assertEquals(228.2, q.extPrice!!, 1e-9)
    }

    @Test
    fun lastSessionTakesTheLatestDay() {
        val day = 86_400
        val times = listOf(regStart - day, regStart - day + 300, regStart, regStart + 300, regStart + 600)
        val json = """
        {"chart":{"result":[{"meta":{"symbol":"AAPL","gmtoffset":-14400},
          "timestamp":[${times.joinToString(",")}],
          "indicators":{"quote":[{"close":[1.0,2.0,3.0,4.0,5.0]}]}}],"error":null}}
        """.trimIndent()
        assertEquals(listOf(3.0, 4.0, 5.0), YahooParser.lastSession(YahooParser.parseChart(json)).map { it.price })
    }

    @Test
    fun oneDayPointsArePlacedOnTheSessionClock() {
        val regular = Session(regStart * 1000, regEnd * 1000)
        val span = (regEnd - regStart) * 1000
        // An hour into today's session: the line covers the first part of the chart.
        val morning = listOf(PricePoint(regStart * 1000, 1.0), PricePoint(regStart * 1000 + span / 4, 2.0))
        assertEquals(listOf(0f, 0.25f), Series.sessionPositions(morning, regular))
        // Friday's points before Monday's open sit on Friday's session.
        val friday = morning.map { it.copy(time = it.time - 3 * 86_400_000L) }
        assertEquals(Session(regular.start - 3 * 86_400_000L, regular.end - 3 * 86_400_000L), Series.sessionOf(friday, regular))
        assertEquals(listOf(0f, 0.25f), Series.sessionPositions(friday, regular))
        assertNull(Series.sessionPositions(morning, null))
    }
}
