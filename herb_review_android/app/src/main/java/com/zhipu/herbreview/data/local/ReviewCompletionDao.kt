package com.zhipu.herbreview.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ReviewCompletionEntity)

    @Query(
        """
        SELECT * FROM review_completion_history
        WHERE employeeId = :employeeId
        ORDER BY completedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeForEmployee(employeeId: String, limit: Int): Flow<List<ReviewCompletionEntity>>
}
