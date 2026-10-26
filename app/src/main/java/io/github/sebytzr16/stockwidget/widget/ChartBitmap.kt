package io.github.sebytzr16.stockwidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * Draws a price line with a soft fill into a [Bitmap] for RemoteViews and share cards. An
 * optional dashed [baseline] marks the previous close.
 */
object ChartBitmap {

    /** Line colors readable on both light and dark widget backgrounds. */
    const val UP = 0xFF1E9E4A.toInt()
    const val DOWN = 0xFFE0484E.toInt()

    fun render(
        prices: List<Double>,
        baseline: Double?,
        color: Int,
        width: Int,
        height: Int,
        stroke: Float,
        baselineColor: Int = 0x66888888,
    ): Bitmap {
        val w = width.coerceAtLeast(2)
        val h = height.coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val pad = stroke * 1.5f

        if (prices.size < 2) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(pad, h / 2f, w - pad, h / 2f, paint)
            return bitmap
        }

        var min = prices.min()
        var max = prices.max()
        if (baseline != null && baseline > 0.0) {
            min = minOf(min, baseline)
            max = maxOf(max, baseline)
        }
        if (max - min < 1e-9) {
            max += 1.0
            min -= 1.0
        }
        val chartW = w - pad * 2
        val chartH = h - pad * 2
        fun x(i: Int) = pad + chartW * i / (prices.size - 1)
        fun y(v: Double) = (pad + chartH * (1 - (v - min) / (max - min))).toFloat()

        if (baseline != null && baseline > 0.0) {
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = baselineColor
                style = Paint.Style.STROKE
                strokeWidth = (stroke / 2f).coerceAtLeast(1f)
                pathEffect = DashPathEffect(floatArrayOf(stroke * 1.5f, stroke * 2f), 0f)
            }
            val by = y(baseline)
            canvas.drawLine(pad, by, w - pad, by, dash)
        }

        val line = Path().apply {
            moveTo(x(0), y(prices[0]))
            for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
        }
        val area = Path(line).apply {
            lineTo(x(prices.lastIndex), h.toFloat())
            lineTo(x(0), h.toFloat())
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), (color and 0x00FFFFFF) or 0x38000000, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(area, fill)
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })
        canvas.drawCircle(x(prices.lastIndex), y(prices.last()), stroke * 1.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        })
        return bitmap
    }
}
