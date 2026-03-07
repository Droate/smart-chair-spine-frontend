package com.example.spineassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SitSessionEntity::class, FeedbackEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sitSessionDao(): SitSessionDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spine_assistant_database"
                )
                    .fallbackToDestructiveMigration()
                    // 🔥 核心修复：允许在主线程执行查询。
                    // 这可以防止因在 Composable 中意外触发 DB 查询导致的 UI 线程阻塞。
                    .allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
