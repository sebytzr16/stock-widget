package io.github.sebytzr16.stockwidget.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.sebytzr16.stockwidget.MainActivity
import io.github.sebytzr16.stockwidget.R
import io.github.sebytzr16.stockwidget.core.AlertHit
import io.github.sebytzr16.stockwidget.core.AlertKind
import io.github.sebytzr16.stockwidget.core.Fmt

/** Price-alert notifications. */
object Notifier {

    private const val CHANNEL_ALERTS = "price_alerts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_alerts_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canNotify()
    fun alert(context: Context, hit: AlertHit) {
        if (!canNotify(context)) return
        val q = hit.quote
        val a = hit.alert
        val title = when (a.kind) {
            AlertKind.ABOVE -> context.getString(R.string.alert_title_above, q.displaySymbol, Fmt.price(a.value, q.currency, q.priceHint))
            AlertKind.BELOW -> context.getString(R.string.alert_title_below, q.displaySymbol, Fmt.price(a.value, q.currency, q.priceHint))
            AlertKind.MOVE -> context.getString(
                if (q.isUp) R.string.alert_title_up else R.string.alert_title_down,
                q.displaySymbol,
                Fmt.percent(q.changePercent, signed = false),
            )
        }
        val text = context.getString(R.string.alert_text, Fmt.price(q.price, q.currency, q.priceHint), Fmt.percent(q.changePercent))
        val open = PendingIntent.getActivity(
            context,
            a.id.toInt(),
            MainActivity.openSymbolIntent(context, q.symbol),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(a.id.toInt(), notification)
    }
}
