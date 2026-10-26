package io.github.sebytzr16.stockwidget.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.Fmt
import io.github.sebytzr16.stockwidget.core.PricePoint
import io.github.sebytzr16.stockwidget.core.Quote
import io.github.sebytzr16.stockwidget.widget.ChartBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

/** Sharing a stock as a clean image card or a line of text. */
object ShareCard {

    fun text(context: Context, q: Quote): String = context.getString(
        R.string.share_text,
        q.displaySymbol,
        Fmt.price(q.price, q.currency, q.priceHint),
        Fmt.percent(q.changePercent),
    )

    fun copyText(context: Context, q: Quote) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(q.displaySymbol, text(context, q)))
    }

    suspend fun shareImage(context: Context, q: Quote, points: List<PricePoint>, dark: Boolean) {
        val uri = withContext(Dispatchers.IO) { writeImage(context, q, points, dark) }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text(context, q))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun writeImage(context: Context, q: Quote, points: List<PricePoint>, dark: Boolean): Uri {
        val bitmap = render(context, q, points, dark)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, q.displaySymbol.replace(Regex("[^A-Za-z0-9]"), "_") + ".png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    private fun render(context: Context, q: Quote, points: List<PricePoint>, dark: Boolean): Bitmap {
        val w = 1200
        val h = 630
        val bg = if (dark) 0xFF161918.toInt() else 0xFFFFFFFF.toInt()
        val text = if (dark) 0xFFECEEED.toInt() else 0xFF141615.toInt()
        val secondary = if (dark) 0xFFA6ACA9.toInt() else 0xFF575C5A.toInt()
        val trend = if (q.isUp) (if (dark) 0xFF45C97A.toInt() else 0xFF12783A.toInt()) else (if (dark) 0xFFF2706F.toInt() else 0xFFC42B2F.toInt())

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg)
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fun paint(size: Float, color: Int, face: Typeface = Typeface.DEFAULT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = face
        }

        val pad = 64f
        canvas.drawText(q.displaySymbol, pad, 112f, paint(60f, text, bold))
        val symbolWidth = paint(60f, text, bold).measureText(q.displaySymbol)
        canvas.drawText(q.name.take(40), pad + symbolWidth + 24f, 112f, paint(32f, secondary))
        canvas.drawText(Fmt.price(q.price, q.currency, q.priceHint), pad, 220f, paint(88f, text, medium))
        val change = Fmt.signedPrice(q.change, q.currency, Fmt.priceDecimals(q.price, q.priceHint)) +
            " (" + Fmt.percent(q.changePercent) + ")"
        canvas.drawText(change, pad, 278f, paint(38f, trend, medium))

        val chart = ChartBitmap.render(
            prices = points.map { it.price },
            baseline = q.previousClose.takeIf { it > 0.0 },
            color = if (q.isUp) ChartBitmap.UP else ChartBitmap.DOWN,
            width = (w - pad * 2).toInt(),
            height = 220,
            stroke = 5f,
        )
        canvas.drawBitmap(chart, pad, 320f, null)

        val footer = paint(26f, secondary)
        canvas.drawText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date()), pad, h - 40f, footer)
        val brand = context.getString(R.string.app_name)
        canvas.drawText(brand, w - pad - footer.measureText(brand), h - 40f, footer)
        return bitmap
    }
}
