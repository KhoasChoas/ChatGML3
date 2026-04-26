package com.zhipu.herbreview.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PrescriptionCacheEntity::class,
        ReviewCompletionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class HerbReviewDatabase : RoomDatabase() {
    abstract fun prescriptionCacheDao(): PrescriptionCacheDao
    abstract fun reviewCompletionDao(): ReviewCompletionDao

    companion object {
        @Volatile
        private var instance: HerbReviewDatabase? = null

        fun getInstance(context: Context): HerbReviewDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HerbReviewDatabase::class.java,
                    "herb_review_cache.db",
                ).fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
        }
    }
}
