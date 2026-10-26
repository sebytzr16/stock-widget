package io.github.sebytzr16.stockwidget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.sebytzr16.stockwidget.repository
import java.util.concurrent.TimeUnit

/**
 * Background refresh for widgets and alerts. The periodic run skips the network while all
 * tracked markets are closed ("Only while markets are open"); crypto keeps it going.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = applicationContext.repository
        val now = inputData.getBoolean(KEY_NOW, false)
        if (!now && !repo.shouldRefreshInBackground(System.currentTimeMillis())) return Result.success()
        val result = repo.refresh(force = now)
        return if (result.ok == 0 && result.failed > 0 && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "refresh_periodic"
        private const val NOW = "refresh_now"
        private const val KEY_NOW = "now"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** (Re)schedules the periodic refresh; WorkManager's minimum period is 15 minutes. */
        fun schedule(context: Context, minutes: Int) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** A one-off refresh as soon as there's a network, e.g. right after a widget is placed. */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(online)
                .setInputData(workDataOf(KEY_NOW to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
