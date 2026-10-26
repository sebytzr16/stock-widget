package io.github.sebytzr16.stockwidget

import android.app.Application
import android.content.Context
import io.github.sebytzr16.stockwidget.data.Repository
import io.github.sebytzr16.stockwidget.system.Notifier
import io.github.sebytzr16.stockwidget.work.RefreshWorker

class StockApp : Application() {

    val repository: Repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        RefreshWorker.schedule(this, repository.settings.refreshMinutes)
    }
}

val Context.repository: Repository get() = (applicationContext as StockApp).repository
