package com.example.spineassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FeedbackDao {
    @Insert
    suspend fun insert(feedback: FeedbackEntity): Long

    // 预留查询：获取最近的反馈，用于分析
    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentFeedbacks(): List<FeedbackEntity>

    // 🔥 [新增] 获取所有未同步的反馈数据
    @Query("SELECT * FROM user_feedback WHERE isSynced = 0")
    suspend fun getUnsyncedFeedbacks(): List<FeedbackEntity>

    // 🔥 [新增] 批量标记为已同步
    @Query("UPDATE user_feedback SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
}
