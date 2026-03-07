package com.example.spineassistant.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SitSessionDao {

    // 插入一条新的会话记录
    @Insert
    suspend fun insert(session: SitSessionEntity): Long

    // 更新一条会话记录
    @Update
    suspend fun update(session: SitSessionEntity)

    // 获取最近一次未结束的会话
    @Query("SELECT * FROM sit_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getOngoingSession(): SitSessionEntity?

    // 获取某一天的所有已结束会话 (用于生成日报表)
    @Query("SELECT * FROM sit_sessions WHERE endTime IS NOT NULL AND startTime >= :dayStart AND startTime < :dayEnd ORDER BY startTime ASC")
    fun getAllFinishedSessionsForDay(dayStart: Long, dayEnd: Long): Flow<List<SitSessionEntity>>

    // 🔥 [新增] 获取指定时间戳之后的所有已完成会话
    // 用于构建 7 天历史趋势图。我们只负责取出数据，按天分组的逻辑交给 ViewModel (Kotlin) 处理，
    // 这样处理时区问题会比写复杂的 SQLite 语句更稳健。
    @Query("SELECT * FROM sit_sessions WHERE endTime IS NOT NULL AND startTime >= :timestamp ORDER BY startTime ASC")
    fun getSessionsSince(timestamp: Long): Flow<List<SitSessionEntity>>
}
