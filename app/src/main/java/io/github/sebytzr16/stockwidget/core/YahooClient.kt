package io.github.sebytzr16.stockwidget.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Keyless Yahoo Finance endpoints over plain HttpURLConnection (no extra libraries).
 * These are unofficial: a browser-like User-Agent is required, and the second host is
 * tried when the first fails.
 */
class YahooClient(private val userAgent: String = USER_AGENT) {

    suspend fun chart(symbol: String, range: String, interval: String, prePost: Boolean): YahooParser.Chart =
        withContext(Dispatchers.IO) {
            YahooParser.parseChart(
                get("v8/finance/chart/${enc(symbol)}?range=$range&interval=$interval&includePrePost=$prePost")
            )
        }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        YahooParser.parseSearch(get("v1/finance/search?q=${enc(query)}&quotesCount=12&newsCount=0&listsCount=0"))
    }

    suspend fun news(symbol: String): List<NewsItem> = withContext(Dispatchers.IO) {
        YahooParser.parseNews(get("v1/finance/search?q=${enc(symbol)}&quotesCount=0&newsCount=8&listsCount=0"))
    }

    private fun get(path: String): String {
        var last: IOException? = null
        for (host in HOSTS) {
            try {
                return fetch(host + path)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("Network error")
    }

    private fun fetch(url: String): String {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code in 200..299) return body
            // Unknown symbols come back as 404 with a JSON error the parser reports.
            if (code == 404 && body.contains("\"chart\"")) return body
            throw IOException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val HOSTS = listOf("https://query1.finance.yahoo.com/", "https://query2.finance.yahoo.com/")

        // Yahoo's unofficial endpoints reject requests without a browser-like User-Agent.
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
