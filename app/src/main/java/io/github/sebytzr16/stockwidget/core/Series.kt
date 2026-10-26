package io.github.sebytzr16.stockwidget.core

import kotlin.math.roundToInt

object Series {

    /** Keeps at most [max] evenly spaced points, always including the first and the last. */
    fun downsample(points: List<PricePoint>, max: Int): List<PricePoint> {
        if (points.size <= max || max < 3) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return List(max) { i -> points[(i * step).roundToInt().coerceAtMost(points.lastIndex)] }
    }

    /**
     * The trading session a day's [points] belong to: today's [regular] hours, or the same
     * hours on the earlier day they come from (the last session, before today's open).
     */
    fun sessionOf(points: List<PricePoint>, regular: Session?): Session? {
        val last = points.lastOrNull() ?: return null
        if (regular == null || regular.end <= regular.start) return null
        val day = 86_400_000L
        var session: Session = regular
        repeat(7) { if (last.time < session.start) session = Session(session.start - day, session.end - day) }
        return session
    }

    /**
     * Where each point sits in its session, from 0 at the open to 1 at the close, so a
     * one-day chart fills in as the day goes. Null when the session hours are unknown.
     */
    fun sessionPositions(points: List<PricePoint>, regular: Session?): List<Float>? {
        if (points.size < 2) return null
        val session = sessionOf(points, regular) ?: return null
        val span = (session.end - session.start).toFloat()
        return points.map { ((it.time - session.start) / span).coerceIn(0f, 1f) }
    }
}
