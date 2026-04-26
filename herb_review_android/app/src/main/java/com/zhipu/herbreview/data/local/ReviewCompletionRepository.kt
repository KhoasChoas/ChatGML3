package com.zhipu.herbreview.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow

object ReviewCompletionRepository {
    suspend fun recordCompleted(
        context: Context,
        employeeId: String,
        sessionId: String,
        prescriptionNo: String,
        prescriptionId: Int,
        archiveStatus: String,
        stepTotal: Int,
        stepCorrect: Int,
        stepIncorrect: Int,
        stepNeedsReview: Int,
    ) {
        HerbReviewDatabase.getInstance(context).reviewCompletionDao().upsert(
            ReviewCompletionEntity(
                sessionId = sessionId,
                employeeId = employeeId,
                prescriptionNo = prescriptionNo,
                prescriptionId = prescriptionId,
                archiveStatus = archiveStatus,
                completedAtMillis = System.currentTimeMillis(),
                stepTotal = stepTotal,
                stepCorrect = stepCorrect,
                stepIncorrect = stepIncorrect,
                stepNeedsReview = stepNeedsReview,
            ),
        )
    }

    fun observeForEmployee(context: Context, employeeId: String, limit: Int = 100): Flow<List<ReviewCompletionEntity>> =
        HerbReviewDatabase.getInstance(context).reviewCompletionDao().observeForEmployee(employeeId, limit)
}
