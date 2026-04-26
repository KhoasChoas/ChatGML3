package com.zhipu.herbreview.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zhipu.herbreview.network.DirectorErrorTimelineDto
import com.zhipu.herbreview.network.HerbReviewRemote
import com.zhipu.herbreview.network.ReviewSessionDto
import com.zhipu.herbreview.prefs.NotificationReminderPrefs
import kotlin.math.absoluteValue

private const val CHANNEL_ID = "herb_review_error_desk"

/**
 * 当本人复核会话在报错台的工单全部由科主任结案后，服务端会将会话标为 completed（完整复核）
 * 或 cancelled（打回返工）。后台轮询根据「待复核工单数」由大于 0 降为 0 且状态符合时发系统通知。
 */
object ErrorDeskResolutionNotifier {

    suspend fun checkAndNotify(context: Context) {
        if (!NotificationReminderPrefs.isErrorDeskResolutionEnabled(context)) return
        if (!HerbReviewRemote.isConfigured()) return

        val mySessions = HerbReviewRemote.fetchRecentReviewSessions(limit = 200, mine = true)
        if (mySessions.isEmpty()) return

        val myPharmacistId = mySessions.first().createdByPharmacistId
        val byId = mySessions.associateBy { it.id }

        suspend fun sessionFor(sid: String): ReviewSessionDto? {
            byId[sid]?.let { return it }
            return try {
                HerbReviewRemote.fetchReviewSession(sid)
                    .takeIf { it.createdByPharmacistId == myPharmacistId }
            } catch (_: Exception) {
                null
            }
        }

        val errRows = HerbReviewRemote.fetchErrorReportsInbox(limit = 500, offset = 0)
        val sessionIds = errRows.mapNotNull { it.sessionId?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .distinct()

        val prevSnap = NotificationReminderPrefs.readErrorDeskPendingSnapshot(context)
        val nextSnap = mutableMapOf<String, Int>()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        for (sid in sessionIds) {
            val session = sessionFor(sid) ?: continue
            val rows = errRows.filter { it.sessionId == sid }
            if (rows.isEmpty()) continue
            val pending = rows.count { it.reviewId == null }
            val prevPending = prevSnap[sid] ?: -1

            if (prevPending > 0 && pending == 0) {
                when {
                    session.status == "completed" -> {
                        notify(
                            context,
                            nm,
                            sessionId = sid,
                            title = "报错台已结案 · 完整复核完成",
                            text = bodyFor(session, rows, outcome = "会话已完成完整复核，处方已归档通过。"),
                        )
                    }
                    session.status == "cancelled" -> {
                        notify(
                            context,
                            nm,
                            sessionId = sid,
                            title = "报错台已结案 · 已打回返工",
                            text = bodyFor(session, rows, outcome = "科主任已全部处理工单，该处方已打回，请到工作台继续返工会话。"),
                        )
                    }
                }
            }
            nextSnap[sid] = pending
        }

        NotificationReminderPrefs.writeErrorDeskPendingSnapshot(context, nextSnap)
    }

    private fun bodyFor(
        session: ReviewSessionDto,
        rows: List<DirectorErrorTimelineDto>,
        outcome: String,
    ): String {
        val rx = session.prescriptionNo?.trim()?.takeIf { it.isNotEmpty() }
            ?: rows.firstNotNullOfOrNull { it.prescriptionNo?.trim()?.takeIf { n -> n.isNotEmpty() } }
        val head = if (rx != null) "处方 $rx · " else ""
        return buildString {
            append(head)
            append(outcome)
            append("（会话 ")
            append(session.id.take(8))
            append("…）")
        }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "报错台结案提醒",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "报错台工单全部结案且会话完整复核或打回时提醒"
                },
            )
        }
    }

    private fun notify(
        context: Context,
        nm: NotificationManager,
        sessionId: String,
        title: String,
        text: String,
    ) {
        val nid = 72000 + (sessionId.hashCode().absoluteValue % 8000)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(nid, n)
    }
}
