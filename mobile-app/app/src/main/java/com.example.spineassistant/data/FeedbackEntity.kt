package com.example.spineassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),

    val problemArea: String,
    val initialIntensity: Float,
    val adjustLoopCount: Int = 0,
    val finalStateJson: String,
    val wasSavedAsPreset: Boolean = false,
    val isSynced: Boolean = false,

    // 🔥 [新增] 记录当时的场景模式 (e.g., "OFFICE", "REST")
    // 默认值 "UNKNOWN" 保证旧数据兼容
    val mode: String = "UNKNOWN"
)
