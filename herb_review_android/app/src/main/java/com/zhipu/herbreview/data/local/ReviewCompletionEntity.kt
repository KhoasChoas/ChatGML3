package com.zhipu.herbreview.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_completion_history")
data class ReviewCompletionEntity(
    @PrimaryKey val sessionId: String,
    val employeeId: String,
    val prescriptionNo: String,
    val prescriptionId: Int,
    /** completed | offline_completed | archive_failed */
    val archiveStatus: String,
    val completedAtMillis: Long,
    val stepTotal: Int,
    val stepCorrect: Int,
    val stepIncorrect: Int,
    val stepNeedsReview: Int,
)
