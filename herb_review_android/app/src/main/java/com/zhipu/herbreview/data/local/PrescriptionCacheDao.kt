package com.zhipu.herbreview.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PrescriptionCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PrescriptionCacheEntity>)

    @Query(
        """
        SELECT * FROM prescription_cache
        WHERE (:q = '')
           OR prescriptionNo LIKE '%' || :q || '%'
           OR IFNULL(patientName, '') LIKE '%' || :q || '%'
           OR IFNULL(diagnosis, '') LIKE '%' || :q || '%'
        ORDER BY lastFetchedAt DESC
        LIMIT :lim
        """,
    )
    suspend fun searchLocal(q: String, lim: Int): List<PrescriptionCacheEntity>
}
