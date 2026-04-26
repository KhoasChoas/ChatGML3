package com.zhipu.herbreview.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zhipu.herbreview.network.HerbReviewRemote
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "herb_review_pending"
private const val NOTIF_ID = 71001
private const val PREFS = "herb_review_poll"
private const val KEY_LAST_PENDING = "last_pending_count"

class PendingReviewPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!HerbReviewRemote.isConfigured()) return Result.success()
        return try {
            HerbReviewRemote.ensureLoggedIn()
            val sessions = HerbReviewRemote.fetchRecentReviewSessions(limit = 60, mine = true)
            val pending = sessions.count {
                it.status == "in_progress" || it.status == "draft"
            }
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val prev = prefs.getInt(KEY_LAST_PENDING, -1)
            if (pending > 0 && pending > prev) {
                notifyPending(applicationContext, pending)
            }
            prefs.edit().putInt(KEY_LAST_PENDING, pending).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PendingReviewPollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "pending_review_poll_v1",
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        private fun notifyPending(context: Context, pending: Int) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "复核工单提醒",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "待继续的复核会话数量增加时提醒"
                    },
                )
            }
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("有待继续的复核")
                .setContentText("当前有 $pending 条进行中的复核会话，可在工作台查看。")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(NOTIF_ID, n)
        }
    }
}
