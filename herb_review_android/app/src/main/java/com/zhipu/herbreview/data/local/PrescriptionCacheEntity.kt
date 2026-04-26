package com.zhipu.herbreview.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prescription_cache")
data class PrescriptionCacheEntity(
    @PrimaryKey val id: Int,
    val prescriptionNo: String,
    val patientName: String?,
    val diagnosis: String?,
    val herbKindCount: Int?,
    val prescribedAt: String?,
    val lastFetchedAt: Long,
)
