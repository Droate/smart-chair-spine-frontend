package com.example.spineassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 代表一次完整的“入座会话”记录。
 * 这是 Room 数据库中 'sit_sessions' 表的结构定义。
 */
@Entity(tableName = "sit_sessions")
data class SitSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 会话的开始与结束时间 (使用 Long 存储毫秒时间戳)
    val startTime: Long,
    var endTime: Long? = null, // 可为空，代表会话正在进行中

    // 计算出的指标
    var durationMinutes: Int = 0, // 会话结束时计算的总分钟数

    // 预留的姿势健康指标
    var averageAngle: Int? = null,
    var badPostureCount: Int = 0,

    // 🔥 新增：有效姿势切换次数 (用于计算活跃度)
    var postureChangeCount: Int = 0,

    // 算法标记
    var wasCountedAsSedentary: Boolean = false, // 是否已被计为一次“久坐”

    // --- 新增字段：座椅模式 ---
    // 记录这次入座主要是在什么模式下 (OFFICE, REST, ENTERTAINMENT)
    // 默认值为 "OFFICE"，确保不修改插入代码也能编译通过
    var mode: String = "OFFICE",

    // 云同步状态
    var isSynced: Boolean = false
)
