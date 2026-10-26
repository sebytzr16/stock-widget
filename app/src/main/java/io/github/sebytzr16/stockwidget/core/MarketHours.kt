package io.github.sebytzr16.stockwidget.core

enum class MarketPhase { OPEN, PRE, POST, CLOSED, ALWAYS }

/** Where a market is in its day; [until] is when that changes (the close, the next open), if known. */
data class MarketStatus(val phase: MarketPhase, val until: Long? = null)

/**
 * Market sessions from Yahoo's trading periods. Cached periods are projected onto today
 * (same exchange-local times) and weekends are skipped, so the answer stays useful
 * between refreshes; an exchange holiday costs at most one wasted refresh.
 */
object MarketHours {

    private const val DAY = 86_400_000L
    private const val GRACE_BEFORE = 15 * 60_000L
    private const val GRACE_AFTER = 30 * 60_000L

    fun status(q: Quote, now: Long): MarketStatus {
        if (q.isCrypto) return MarketStatus(MarketPhase.ALWAYS)
        val reg = q.regular ?: return MarketStatus(MarketPhase.CLOSED)
        val off = q.gmtOffsetSec * 1000L
        val shift = shiftToToday(reg.start, now, off)
        val today = reg.shifted(shift)
        if (!isWeekend(now, off)) {
            if (now in today) return MarketStatus(MarketPhase.OPEN, today.end)
            val pre = q.pre?.shifted(shift)
            if (pre != null && now in pre) return MarketStatus(MarketPhase.PRE, today.start)
            val post = q.post?.shifted(shift)
            if (post != null && now in post) return MarketStatus(MarketPhase.POST, post.end)
        }
        return MarketStatus(MarketPhase.CLOSED, nextOpen(today.start, now, off))
    }

    /**
     * Whether a background refresh is worth it for [q] right now: while its market trades
     * (pre and post included), plus a little grace to catch the open and the final close.
     */
    fun isActive(q: Quote, now: Long): Boolean {
        if (q.isCrypto) return true
        val off = q.gmtOffsetSec * 1000L
        if (q.isCurrency) return !isWeekend(now, off)
        val reg = q.regular ?: return true
        if (isWeekend(now, off)) return false
        val shift = shiftToToday(reg.start, now, off)
        val start = (q.pre ?: reg).start + shift - GRACE_BEFORE
        val end = (q.post ?: reg).end + shift + GRACE_AFTER
        return now in start until end
    }

    /** True when any of [quotes] is active, or when nothing is known yet. */
    fun anyActive(quotes: Collection<Quote>, now: Long): Boolean =
        quotes.isEmpty() || quotes.any { isActive(it, now) }

    /** Saturday or Sunday at the exchange's UTC offset. */
    fun isWeekend(t: Long, offsetMs: Long): Boolean {
        val dayOfWeek = Math.floorMod(Math.floorDiv(t + offsetMs, DAY) + 3L, 7L) // 0 = Monday
        return dayOfWeek >= 5L
    }

    private fun Session.shifted(by: Long) = Session(start + by, end + by)

    /** Whole days that move [t] onto the exchange-local calendar day of [now]. */
    private fun shiftToToday(t: Long, now: Long, off: Long): Long =
        (Math.floorDiv(now + off, DAY) - Math.floorDiv(t + off, DAY)) * DAY

    private fun nextOpen(todayOpen: Long, now: Long, off: Long): Long {
        var open = todayOpen
        while (open <= now || isWeekend(open, off)) open += DAY
        return open
    }
}
