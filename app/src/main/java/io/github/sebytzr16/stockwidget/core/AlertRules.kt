package io.github.sebytzr16.stockwidget.core

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

data class AlertHit(val alert: PriceAlert, val quote: Quote)

object AlertRules {

    /**
     * Checks [alerts] against freshly fetched [quotes]. Price alerts fire once and switch
     * off; move alerts fire at most once per exchange-local day. Returns the hits and the
     * alert list with those changes applied.
     */
    fun evaluate(alerts: List<PriceAlert>, quotes: Map<String, Quote>): Pair<List<AlertHit>, List<PriceAlert>> {
        val hits = ArrayList<AlertHit>()
        val updated = alerts.map { a ->
            val q = quotes[a.symbol]
            if (!a.enabled || q == null || !q.hasPrice) return@map a
            when (a.kind) {
                AlertKind.ABOVE -> if (q.price >= a.value) {
                    hits += AlertHit(a, q)
                    a.copy(enabled = false)
                } else a
                AlertKind.BELOW -> if (q.price <= a.value) {
                    hits += AlertHit(a, q)
                    a.copy(enabled = false)
                } else a
                AlertKind.MOVE -> {
                    val day = localDay(q)
                    if (q.previousClose > 0.0 && abs(q.changePercent) >= a.value && a.lastFiredDay != day) {
                        hits += AlertHit(a, q)
                        a.copy(lastFiredDay = day)
                    } else a
                }
            }
        }
        return hits to updated
    }

    /** The exchange-local date of the quote, e.g. "2026-10-26". */
    fun localDay(q: Quote): String {
        val t = if (q.marketTime > 0L) q.marketTime else q.fetchedAt
        return Instant.ofEpochMilli(t).atOffset(ZoneOffset.ofTotalSeconds(q.gmtOffsetSec)).toLocalDate().toString()
    }
}
