package io.github.sebytzr16.stockwidget.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Parses Yahoo Finance's keyless chart and search responses (org.json, no reflection). */
object YahooParser {

    data class Bar(val time: Long, val open: Double?, val high: Double?, val low: Double?, val close: Double)

    /** One chart response: the meta block plus its price bars. */
    data class Chart(
        val symbol: String = "",
        val currency: String = "",
        val exchangeName: String = "",
        val fullExchangeName: String = "",
        val type: String = "",
        val longName: String? = null,
        val shortName: String? = null,
        val price: Double? = null,
        val previousClose: Double? = null,
        val chartPreviousClose: Double? = null,
        val dayHigh: Double? = null,
        val dayLow: Double? = null,
        val volume: Long? = null,
        val yearHigh: Double? = null,
        val yearLow: Double? = null,
        val marketTime: Long = 0L,
        val gmtOffsetSec: Int = 0,
        val priceHint: Int = -1,
        val pre: Session? = null,
        val regular: Session? = null,
        val post: Session? = null,
        val bars: List<Bar> = emptyList(),
        val error: String? = null,
    )

    fun parseChart(json: String): Chart {
        val chart = JSONObject(json).optJSONObject("chart") ?: return Chart(error = "No data")
        val error = chart.optJSONObject("error")?.let { str(it, "description") ?: str(it, "code") }
        val result = chart.optJSONArray("result")?.optJSONObject(0)
            ?: return Chart(error = error ?: "No data")
        val meta = result.optJSONObject("meta") ?: JSONObject()
        val period = meta.optJSONObject("currentTradingPeriod")
        return Chart(
            symbol = str(meta, "symbol").orEmpty(),
            currency = str(meta, "currency").orEmpty(),
            exchangeName = str(meta, "exchangeName").orEmpty(),
            fullExchangeName = str(meta, "fullExchangeName").orEmpty(),
            type = str(meta, "instrumentType").orEmpty(),
            longName = str(meta, "longName"),
            shortName = str(meta, "shortName"),
            price = num(meta, "regularMarketPrice"),
            previousClose = num(meta, "previousClose"),
            chartPreviousClose = num(meta, "chartPreviousClose"),
            dayHigh = num(meta, "regularMarketDayHigh"),
            dayLow = num(meta, "regularMarketDayLow"),
            volume = num(meta, "regularMarketVolume")?.toLong(),
            yearHigh = num(meta, "fiftyTwoWeekHigh"),
            yearLow = num(meta, "fiftyTwoWeekLow"),
            marketTime = (num(meta, "regularMarketTime") ?: 0.0).toLong() * 1000L,
            gmtOffsetSec = meta.optInt("gmtoffset", 0),
            priceHint = meta.optInt("priceHint", -1),
            pre = session(period?.optJSONObject("pre")),
            regular = session(period?.optJSONObject("regular")),
            post = session(period?.optJSONObject("post")),
            bars = bars(result),
            error = error,
        )
    }

    /**
     * Builds a [Quote] from a one-day chart fetched with pre/post-market data. Values Yahoo
     * leaves out fall back to [previous], so a sparse response never blanks a known price.
     * Returns null when there is no price at all.
     */
    fun toQuote(
        chart: Chart,
        symbol: String,
        name: String?,
        now: Long,
        previous: Quote? = null,
        maxPoints: Int = 120,
    ): Quote? {
        val regular = chart.regular
        val regularBars = if (regular == null) chart.bars else chart.bars.filter { it.time in regular }
        val price = chart.price ?: regularBars.lastOrNull()?.close ?: previous?.price ?: return null
        val first = regularBars.firstOrNull()
        val ext = if (regular == null) null else chart.bars.lastOrNull { it.time !in regular }
        return Quote(
            symbol = symbol,
            name = name?.takeIf { it.isNotBlank() } ?: chart.longName ?: chart.shortName
                ?: previous?.name ?: symbol,
            price = price,
            previousClose = chart.previousClose ?: chart.chartPreviousClose
                ?: previous?.previousClose ?: 0.0,
            open = first?.open ?: first?.close ?: 0.0,
            dayHigh = chart.dayHigh ?: regularBars.maxOfOrNull { it.high ?: it.close } ?: 0.0,
            dayLow = chart.dayLow ?: regularBars.minOfOrNull { it.low ?: it.close } ?: 0.0,
            volume = chart.volume ?: 0L,
            yearHigh = chart.yearHigh ?: previous?.yearHigh ?: 0.0,
            yearLow = chart.yearLow ?: previous?.yearLow ?: 0.0,
            currency = chart.currency.ifBlank { previous?.currency.orEmpty() },
            exchange = Exchanges.friendly(chart.exchangeName, chart.fullExchangeName)
                .ifBlank { previous?.exchange.orEmpty() },
            type = chart.type.ifBlank { previous?.type.orEmpty() },
            priceHint = chart.priceHint,
            marketTime = chart.marketTime,
            fetchedAt = now,
            gmtOffsetSec = chart.gmtOffsetSec,
            pre = chart.pre,
            regular = regular,
            post = chart.post,
            extPrice = ext?.close,
            extTime = ext?.time ?: 0L,
            intraday = Series.downsample(regularBars.map { PricePoint(it.time, it.close) }, maxPoints),
        )
    }

    /** A detail-chart range; the change is measured from the close before the range starts. */
    fun toRangeSeries(chart: Chart, maxPoints: Int = 400): RangeSeries? {
        val points = chart.bars.map { PricePoint(it.time, it.close) }
        if (points.size < 2) return null
        val reference = chart.chartPreviousClose?.takeIf { it > 0.0 } ?: points.first().price
        return RangeSeries(Series.downsample(points, maxPoints), reference)
    }

    fun parseSearch(json: String): List<SearchResult> {
        val quotes = JSONObject(json).optJSONArray("quotes") ?: return emptyList()
        val out = ArrayList<SearchResult>()
        for (i in 0 until quotes.length()) {
            val o = quotes.optJSONObject(i) ?: continue
            val symbol = str(o, "symbol") ?: continue
            val type = str(o, "quoteType") ?: continue
            if (type !in TRADEABLE) continue
            out += SearchResult(
                symbol = symbol.uppercase(Locale.ROOT),
                name = str(o, "longname") ?: str(o, "shortname") ?: symbol,
                exchange = str(o, "exchDisp").orEmpty(),
                type = type,
            )
        }
        return out.distinctBy { it.symbol }
    }

    fun parseNews(json: String): List<NewsItem> {
        val news = JSONObject(json).optJSONArray("news") ?: return emptyList()
        val out = ArrayList<NewsItem>()
        for (i in 0 until news.length()) {
            val o = news.optJSONObject(i) ?: continue
            val title = str(o, "title") ?: continue
            val link = str(o, "link") ?: continue
            out += NewsItem(
                title = title,
                publisher = str(o, "publisher").orEmpty(),
                link = link,
                time = o.optLong("providerPublishTime", 0L) * 1000L,
            )
        }
        return out
    }

    private val TRADEABLE =
        setOf("EQUITY", "ETF", "INDEX", "MUTUALFUND", "CURRENCY", "CRYPTOCURRENCY", "FUTURE")

    private fun bars(result: JSONObject): List<Bar> {
        val times = result.optJSONArray("timestamp") ?: return emptyList()
        val q = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
            ?: return emptyList()
        val close = q.optJSONArray("close") ?: return emptyList()
        val open = q.optJSONArray("open")
        val high = q.optJSONArray("high")
        val low = q.optJSONArray("low")
        val out = ArrayList<Bar>(times.length())
        for (i in 0 until times.length()) {
            val c = num(close, i) ?: continue
            if (c <= 0.0) continue
            out += Bar(times.optLong(i) * 1000L, num(open, i), num(high, i), num(low, i), c)
        }
        return out
    }

    private fun session(o: JSONObject?): Session? {
        if (o == null) return null
        val start = o.optLong("start", 0L)
        val end = o.optLong("end", 0L)
        return if (start > 0L && end > start) Session(start * 1000L, end * 1000L) else null
    }

    private fun str(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).takeIf { it.isNotBlank() }

    private fun num(o: JSONObject, key: String): Double? =
        if (o.isNull(key)) null else o.optDouble(key, Double.NaN).takeIf { !it.isNaN() }

    private fun num(a: JSONArray?, i: Int): Double? =
        if (a == null || i >= a.length() || a.isNull(i)) null
        else a.optDouble(i, Double.NaN).takeIf { !it.isNaN() }
}
