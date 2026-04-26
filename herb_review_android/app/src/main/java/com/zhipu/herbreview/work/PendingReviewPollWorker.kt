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
import com.zhipu.herbreview.network.ReviewSessionDto
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "herb_review_pending"
private const val NOTIF_ID = 71001
private const val PREFS = "herb_review_poll"
private const val KEY_LAST_PENDING = "last_pending_count"
private const val KEY_LAST_SUSPENDED = "last_suspended_count"

/** 与 Workbench 挂起队列规则一致（仅依赖 status + notes，避免解析异常）。 */
private fun ReviewSessionDto.matchesSuspendedQueue(): Boolean {
    val st = status ?: ""
    val blob = notes.orEmpty()
    val hasSuspTag = blob.contains("suspended_pending_error")
    val queuedByError = st == "draft" && hasSuspTag
    val hasParent = blob.contains("parent_session_id=")
    val queuedRecheck = st == "in_progress" && hasParent
    return queuedByError || queuedRecheck
}

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
            val suspended = sessions.count { it.matchesSuspendedQueue() }
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val prevPending = prefs.getInt(KEY_LAST_PENDING, -1)
            val prevSuspended = prefs.getInt(KEY_LAST_SUSPENDED, -1)
            val pendingUp = pending > 0 && pending > prevPending
            val suspendedUp = suspended > 0 && suspended > prevSuspended
            if (pendingUp || suspendedUp) {
                notifyPending(
                    applicationContext,
                    pending = pending,
                    suspended = suspended,
                    highlightSuspended = suspendedUp && !pendingUp,
                )
            }
            prefs.edit()
                .putInt(KEY_LAST_PENDING, pending)
                .putInt(KEY_LAST_SUSPENDED, suspended)
                .apply()
            try {
                ErrorDeskResolutionNotifier.checkAndNotify(applicationContext)
            } catch (_: Exception) {
            }
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

        private fun notifyPending(
            context: Context,
            pending: Int,
            suspended: Int,
            highlightSuspended: Boolean,
        ) {
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
            val title = if (highlightSuspended) "挂起/返工会话有更新" else "有待继续的复核"
            val text = buildString {
                append("进行中+草稿 $pending 条")
                if (suspended > 0) append(" · 挂起队列 $suspended 条")
                append("。请打开工作台查看。")
            }
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(NOTIF_ID, n)
        }
    }
}
