package com.example.spineassistant

// 1. 原有的数据模型保持不变
data class HealthData(
    val totalSittingTime: String = "8h",
    val postureScore: Int = 80,
    val sedentaryReminders: Int = 3,
    val weeklyData: List<Int> = listOf(6, 7, 5, 8, 6, 7, 8),
    val healthAdvice: String = "您今天下午3点后坐姿有所松懈，建议适时调整。",
    val modeDistribution: Map<String, Float> = mapOf( // 新增：模式分布数据
        "办公模式" to 0.6f,
        "休息模式" to 0.25f,
        "娱乐模式" to 0.15f
    )
)

data class ChairState(
    var currentMode: ChairMode = ChairMode.OFFICE,
    var height: Float = 48f,
    var backAngle: Float = 98f,
    var seatAngle: Float = 3f,
    var isCushionFollow: Boolean = true,
    var isAutoAdjust: Boolean = false
)

enum class ChairMode {
    OFFICE, REST, ENTERTAINMENT
}

// 2. 新增：自定义模式模型
data class CustomPreset(
    val id: String,
    val name: String, // 如：“午休小睡”
    val backAngle: Float,
    val seatAngle: Float,
    val height: Float
)

// 3. 新增：健康分详情模型
data class HealthScoreDetail(
    val totalScore: Int = 80, // 总分
    val ranking: String = "击败72%用户", // 排名
    val components: List<ScoreComponent> = listOf( // 分数构成项
        ScoreComponent("坐姿时长", 8, 10),
        ScoreComponent("姿势切换", 7, 10),
        ScoreComponent("久坐提醒", 9, 10),
        ScoreComponent("坐姿稳定", 8, 10)
    ),
    val suggestion: String = "建议每45分钟起身活动5分钟，保持腰椎自然曲线。" // 具体建议
)

data class ScoreComponent(
    val name: String,
    val score: Int,
    val maxScore: Int
)

// 4. 新增：固件信息模型
data class FirmwareInfo(
    val currentVersion: String = "v1.2.3",
    val latestVersion: String = "v1.3.0",
    val hasUpdate: Boolean = true,
    val updateProgress: Float = 0f // 0-1
)

// 5. 新增：设备连接状态
data class DeviceStatus(
    val isConnected: Boolean = true,
    val deviceName: String = "Chairfree-A1"
)