package com.zhipu.herbreview.data

import android.content.Context
import com.zhipu.herbreview.data.local.HerbReviewDatabase
import com.zhipu.herbreview.data.local.PrescriptionCacheEntity
import com.zhipu.herbreview.network.PrescriptionListItemDto
import com.zhipu.herbreview.network.PrescriptionPageDto

object PrescriptionCacheRepository {

    suspend fun upsertSearchResults(context: Context, page: PrescriptionPageDto) {
        val dao = HerbReviewDatabase.getInstance(context).prescriptionCacheDao()
        val now = System.currentTimeMillis()
        val rows = page.items.map {
            PrescriptionCacheEntity(
                id = it.id,
                prescriptionNo = it.prescriptionNo,
                patientName = it.patientName,
                diagnosis = it.diagnosis,
                herbKindCount = it.herbKindCount,
                prescribedAt = it.prescribedAt,
                lastFetchedAt = now,
            )
        }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
    }

    suspend fun searchOffline(context: Context, q: String, limit: Int = 40): List<PrescriptionListItemDto> {
        val trimmed = q.trim()
        val dao = HerbReviewDatabase.getInstance(context).prescriptionCacheDao()
        return dao.searchLocal(trimmed, limit.coerceIn(1, 200)).map { e ->
            PrescriptionListItemDto(
                id = e.id,
                prescriptionNo = e.prescriptionNo,
                patientName = e.patientName,
                diagnosis = e.diagnosis,
                prescribedAt = e.prescribedAt,
                herbKindCount = e.herbKindCount,
            )
        }
    }
}
