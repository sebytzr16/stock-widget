package io.github.sebytzr16.stockwidget.core

import org.json.JSONArray
import org.json.JSONObject

/** Hand-written JSON for what the app persists; no reflection, so R8 can shrink freely. */
object Codec {

    fun encodeUser(u: UserData): String = JSONObject().apply {
        put("v", 1)
        put("items", JSONArray().apply {
            u.items.forEach { put(JSONObject().put("s", it.symbol).put("n", it.name).put("p", it.pinned)) }
        })
        put("holdings", JSONObject().apply {
            u.holdings.forEach { (s, h) -> put(s, JSONObject().put("q", h.shares).put("c", h.avgCost)) }
        })
        put("alerts", JSONArray().apply {
            u.alerts.forEach {
                put(
                    JSONObject().put("id", it.id).put("s", it.symbol).put("k", it.kind.name)
                        .put("v", it.value).put("on", it.enabled).putOpt("d", it.lastFiredDay)
                )
            }
        })
        put("notes", JSONObject().apply { u.notes.forEach { (s, n) -> put(s, n) } })
        put("widgets", JSONObject().apply {
            u.widgets.forEach { (id, symbols) -> put(id.toString(), JSONArray().apply { symbols.forEach { put(it) } }) }
        })
        put("settings", encodeSettings(u.settings))
        put("wthemes", JSONObject().apply { u.widgetThemes.forEach { (id, t) -> put(id.toString(), t.name) } })
    }.toString()

    fun decodeUser(json: String): UserData {
        val o = JSONObject(json)
        val items = o.optJSONArray("items").objects().mapNotNull { i ->
            val s = i.optString("s").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WatchItem(s, i.optString("n", s), i.optBoolean("p", false))
        }.distinctBy { it.symbol }
        val holdings = o.optJSONObject("holdings").entries { s, v ->
            (v as? JSONObject)?.let { Holding(it.optDouble("q", 0.0), it.optDouble("c", 0.0)) }
                ?.takeIf { it.shares > 0.0 }?.let { s to it }
        }.toMap()
        val alerts = o.optJSONArray("alerts").objects().mapNotNull { a ->
            val kind = enumOr(a.optString("k"), AlertKind.ABOVE)
            val s = a.optString("s").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PriceAlert(
                id = a.optLong("id", 0L),
                symbol = s,
                kind = kind,
                value = a.optDouble("v", 0.0),
                enabled = a.optBoolean("on", true),
                lastFiredDay = if (a.isNull("d")) null else a.optString("d"),
            )
        }
        val notes = o.optJSONObject("notes").entries { s, v -> (v as? String)?.let { s to it } }.toMap()
        val widgets = o.optJSONObject("widgets").entries { id, v ->
            val list = (v as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } }
            id.toIntOrNull()?.let { key -> list?.let { key to it } }
        }.toMap()
        val widgetThemes = o.optJSONObject("wthemes").entries { id, v ->
            id.toIntOrNull()?.let { key -> (v as? String)?.let { key to enumOr(it, WidgetTheme.SYSTEM) } }
        }.toMap()
        return UserData(items, holdings, alerts, notes, widgets, decodeSettings(o.optJSONObject("settings")), widgetThemes)
    }

    fun encodeMarket(m: MarketData): String = JSONObject().apply {
        put("v", 1)
        put("quotes", JSONObject().apply { m.quotes.forEach { (s, q) -> put(s, encodeQuote(q)) } })
        put("fx", JSONObject().apply { m.fx.forEach { (k, r) -> if (r.isFinite()) put(k, r) } })
        put("ok", m.lastSuccess)
        put("try", m.lastAttempt)
        putOpt("err", m.lastError)
    }.toString()

    fun decodeMarket(json: String): MarketData {
        val o = JSONObject(json)
        val quotes = o.optJSONObject("quotes").entries { s, v -> (v as? JSONObject)?.let { s to decodeQuote(s, it) } }.toMap()
        val fx = o.optJSONObject("fx").entries { k, v -> (v as? Number)?.toDouble()?.let { k to it } }.toMap()
        return MarketData(
            quotes = quotes,
            fx = fx,
            lastSuccess = o.optLong("ok", 0L),
            lastAttempt = o.optLong("try", 0L),
            lastError = if (o.isNull("err")) null else o.optString("err"),
        )
    }

    private fun encodeSettings(s: Settings) = JSONObject()
        .put("theme", s.theme.name)
        .put("black", s.pureBlack)
        .put("dynamic", s.dynamicColor)
        .put("every", s.refreshMinutes)
        .put("open", s.onlyWhenOpen)
        .put("ext", s.extendedHours)
        .put("alerts", s.alertsEnabled)
        .put("home", s.homeCurrency)
        .put("sort", s.sort.name)
        .put("indices", JSONArray().apply { s.indices.forEach { put(it) } })

    private fun decodeSettings(o: JSONObject?): Settings {
        if (o == null) return Settings()
        val d = Settings()
        val indices = o.optJSONArray("indices")
        return Settings(
            theme = enumOr(o.optString("theme"), d.theme),
            pureBlack = o.optBoolean("black", d.pureBlack),
            dynamicColor = o.optBoolean("dynamic", d.dynamicColor),
            refreshMinutes = o.optInt("every", d.refreshMinutes).coerceIn(15, 240),
            onlyWhenOpen = o.optBoolean("open", d.onlyWhenOpen),
            extendedHours = o.optBoolean("ext", d.extendedHours),
            alertsEnabled = o.optBoolean("alerts", d.alertsEnabled),
            homeCurrency = o.optString("home", d.homeCurrency),
            sort = enumOr(o.optString("sort"), d.sort),
            indices = if (indices == null) d.indices
            else (0 until indices.length()).mapNotNull { indices.optString(it).takeIf { s -> s.isNotBlank() } },
        )
    }

    private fun encodeQuote(q: Quote) = JSONObject().apply {
        put("n", q.name)
        put("p", q.price)
        put("pc", q.previousClose)
        put("o", q.open)
        put("h", q.dayHigh)
        put("l", q.dayLow)
        put("vol", q.volume)
        put("yh", q.yearHigh)
        put("yl", q.yearLow)
        put("cur", q.currency)
        put("ex", q.exchange)
        put("type", q.type)
        put("hint", q.priceHint)
        put("mt", q.marketTime)
        put("ft", q.fetchedAt)
        put("gmt", q.gmtOffsetSec)
        putOpt("pre", q.pre?.let { JSONArray().put(it.start).put(it.end) })
        putOpt("reg", q.regular?.let { JSONArray().put(it.start).put(it.end) })
        putOpt("post", q.post?.let { JSONArray().put(it.start).put(it.end) })
        q.extPrice?.takeIf { it.isFinite() }?.let { put("xp", it) }
        put("xt", q.extTime)
        put("it", JSONArray().apply { q.intraday.forEach { put(it.time) } })
        put("ip", JSONArray().apply { q.intraday.forEach { put(it.price) } })
    }

    private fun decodeQuote(symbol: String, o: JSONObject): Quote {
        val times = o.optJSONArray("it")
        val prices = o.optJSONArray("ip")
        val n = minOf(times?.length() ?: 0, prices?.length() ?: 0)
        return Quote(
            symbol = symbol,
            name = o.optString("n", symbol),
            price = o.optDouble("p", 0.0),
            previousClose = o.optDouble("pc", 0.0),
            open = o.optDouble("o", 0.0),
            dayHigh = o.optDouble("h", 0.0),
            dayLow = o.optDouble("l", 0.0),
            volume = o.optLong("vol", 0L),
            yearHigh = o.optDouble("yh", 0.0),
            yearLow = o.optDouble("yl", 0.0),
            currency = o.optString("cur"),
            exchange = o.optString("ex"),
            type = o.optString("type"),
            priceHint = o.optInt("hint", -1),
            marketTime = o.optLong("mt", 0L),
            fetchedAt = o.optLong("ft", 0L),
            gmtOffsetSec = o.optInt("gmt", 0),
            pre = session(o.optJSONArray("pre")),
            regular = session(o.optJSONArray("reg")),
            post = session(o.optJSONArray("post")),
            extPrice = if (o.has("xp")) o.optDouble("xp") else null,
            extTime = o.optLong("xt", 0L),
            intraday = List(n) { PricePoint(times!!.optLong(it), prices!!.optDouble(it)) },
        )
    }

    private fun session(a: JSONArray?): Session? =
        if (a == null || a.length() < 2) null else Session(a.optLong(0), a.optLong(1))

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun <T> JSONObject?.entries(map: (String, Any?) -> T?): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>()
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map(k, opt(k))?.let { out += it }
        }
        return out
    }
}
