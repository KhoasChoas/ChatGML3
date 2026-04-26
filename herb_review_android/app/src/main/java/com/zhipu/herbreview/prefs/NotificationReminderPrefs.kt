package com.zhipu.herbreview.prefs

import android.content.Context

object NotificationReminderPrefs {
    private const val PREFS = "herb_review_notification_reminders"
    const val KEY_ERROR_DESK_RESOLUTION_ENABLED = "error_desk_resolution_enabled"
    private const val KEY_ERROR_DESK_PENDING_SNAPSHOT = "error_desk_pending_snapshot"

    fun isErrorDeskResolutionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ERROR_DESK_RESOLUTION_ENABLED, true)

    fun setErrorDeskResolutionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ERROR_DESK_RESOLUTION_ENABLED, enabled)
            .apply()
    }

    fun readErrorDeskPendingSnapshot(context: Context): Map<String, Int> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ERROR_DESK_PENDING_SNAPSHOT, null)
        return parsePendingSnapshot(raw)
    }

    fun writeErrorDeskPendingSnapshot(context: Context, snapshot: Map<String, Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ERROR_DESK_PENDING_SNAPSHOT, formatPendingSnapshot(snapshot))
            .apply()
    }

    internal fun parsePendingSnapshot(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(';').mapNotNull { part ->
            val idx = part.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val sid = part.substring(0, idx)
            val p = part.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
            sid to p
        }.toMap()
    }

    internal fun formatPendingSnapshot(m: Map<String, Int>): String =
        m.entries.joinToString(";") { "${it.key}:${it.value}" }
}
