package com.hsib.sib.social

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hsib.sib.SibApp
import java.util.concurrent.TimeUnit

/**
 * Catches shared deadlines up in the background while the app is closed, only on a connection, a few times a day. It is
 * scheduled only while someone is signed in and cancelled when they sign out. Without a server push (which needs a paid
 * plan) this is how a changed date or a new message reaches a phone that is not open.
 */
class SocialSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ready = (applicationContext as SibApp).container.awaitReady() ?: return Result.success()
        val social = ready.social ?: return Result.success()
        return when (val r = social.sync()) {
            is SocialResult.Ok -> Result.success()
            is SocialResult.Err -> if (r.error == SocialError.OFFLINE) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val NAME = "social-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SocialSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
