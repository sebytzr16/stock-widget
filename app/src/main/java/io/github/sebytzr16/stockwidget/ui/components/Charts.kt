package io.github.sebytzr16.stockwidget.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sebytzr16.stockwidget.ui.theme.LocalPalette
import kotlin.math.roundToInt

/** Small intraday line for list rows, with the previous close as a faint dashed baseline. */
@Composable
fun Sparkline(prices: List<Double>, baseline: Double?, up: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val color = if (up) palette.upLine else palette.downLine
    val baseColor = palette.text3
    Canvas(modifier) {
        drawPriceLine(
            prices = prices,
            baseline = baseline,
            color = color,
            baseColor = baseColor,
            stroke = 1.75.dp,
            padX = 1.dp,
            padY = 3.dp,
            fillAlpha = 0.16f,
        )
    }
}

/**
 * The detail chart. Drag sideways to inspect points (reported through [onScrub], null when
 * released); each new point gives a light haptic tick. Vertical drags still scroll the page.
 */
@Composable
fun PriceChart(
    prices: List<Double>,
    reference: Double?,
    showReference: Boolean,
    up: Boolean,
    onScrub: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val color = if (up) palette.upLine else palette.downLine
    val baseColor = palette.text3
    val cursorColor = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background
    val view = LocalView.current
    var selected by remember(prices) { mutableStateOf<Int?>(null) }
    val scrubbed by rememberUpdatedState(onScrub)

    Canvas(
        modifier.pointerInput(prices) {
            if (prices.size < 2) return@pointerInput
            val pad = 16.dp.toPx()
            fun indexAt(x: Float): Int {
                val f = ((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                return (f * (prices.size - 1)).roundToInt()
            }
            fun pick(i: Int) {
                if (i != selected) {
                    selected = i
                    scrubbed(i)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            detectHorizontalDragGestures(
                onDragStart = { pick(indexAt(it.x)) },
                onDragEnd = {
                    selected = null
                    scrubbed(null)
                },
                onDragCancel = {
                    selected = null
                    scrubbed(null)
                },
            ) { change, _ ->
                change.consume()
                pick(indexAt(change.position.x))
            }
        }
    ) {
        val layout = drawPriceLine(
            prices = prices,
            baseline = reference.takeIf { showReference },
            color = color,
            baseColor = baseColor,
            stroke = 2.25.dp,
            padX = 16.dp,
            padY = 12.dp,
            fillAlpha = 0.22f,
            dot = false,
        ) ?: return@Canvas
        val i = selected ?: prices.lastIndex
        val center = Offset(layout.x(i), layout.y(prices[i]))
        if (selected != null) {
            drawLine(cursorColor, Offset(center.x, 4.dp.toPx()), Offset(center.x, size.height - 4.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        drawCircle(background, radius = 7.dp.toPx(), center = center)
        drawCircle(color, radius = 4.5.dp.toPx(), center = center)
    }
}

/** Maps series indices and prices to canvas coordinates. */
class ChartLayout(private val left: Float, private val width: Float, private val top: Float, private val height: Float, private val count: Int, private val min: Double, private val max: Double) {
    fun x(i: Int): Float = left + width * i / (count - 1).coerceAtLeast(1)
    fun y(v: Double): Float = (top + height * (1 - (v - min) / (max - min))).toFloat()
}

private fun DrawScope.drawPriceLine(
    prices: List<Double>,
    baseline: Double?,
    color: Color,
    baseColor: Color,
    stroke: Dp,
    padX: Dp,
    padY: Dp,
    fillAlpha: Float,
    dot: Boolean = true,
): ChartLayout? {
    if (prices.size < 2) {
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = stroke.toPx(), cap = StrokeCap.Round)
        return null
    }
    var min = prices.min()
    var max = prices.max()
    if (baseline != null && baseline > 0.0) {
        min = minOf(min, baseline)
        max = maxOf(max, baseline)
    }
    if (max - min < 1e-12) {
        max += 1.0
        min -= 1.0
    }
    val px = padX.toPx()
    val py = padY.toPx()
    val layout = ChartLayout(px, size.width - 2 * px, py, size.height - 2 * py, prices.size, min, max)

    if (baseline != null && baseline > 0.0) {
        val y = layout.y(baseline)
        drawLine(
            color = baseColor.copy(alpha = 0.6f),
            start = Offset(px, y),
            end = Offset(size.width - px, y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
    }
    val line = Path().apply {
        moveTo(layout.x(0), layout.y(prices[0]))
        for (i in 1 until prices.size) lineTo(layout.x(i), layout.y(prices[i]))
    }
    val area = Path().apply {
        addPath(line)
        lineTo(layout.x(prices.lastIndex), size.height)
        lineTo(layout.x(0), size.height)
        close()
    }
    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = fillAlpha), Color.Transparent)))
    drawPath(line, color, style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (dot) drawCircle(color, radius = stroke.toPx() * 1.4f, center = Offset(layout.x(prices.lastIndex), layout.y(prices.last())))
    return layout
}
