package io.github.sebytzr16.stockwidget.core

import kotlin.math.roundToInt

object Series {

    /** Keeps at most [max] evenly spaced points, always including the first and the last. */
    fun downsample(points: List<PricePoint>, max: Int): List<PricePoint> {
        if (points.size <= max || max < 3) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return List(max) { i -> points[(i * step).roundToInt().coerceAtMost(points.lastIndex)] }
    }
}
