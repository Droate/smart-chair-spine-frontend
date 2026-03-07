package com.example.spineassistant.models // 确保包名和你的文件夹路径一致

// ==========================================
//  APP 本地 UI 模型
// ==========================================

data class HealthData(
    val totalSittingTime: String = "8h",
    val postureScore: Int = 80,
    val sedentaryReminders: Int = 3,

    // 🔥🔥🔥【关键修改】: 将 List<Int> 改为 List<Float>，并为默认值添加 'f' 后缀
    val weeklyData: List<Float> = listOf(6f, 7f, 5f, 8f, 6f, 7f, 8f),

    val healthAdvice: String = "您今天下午3点后坐姿有所松懈，建议适时调整。",
    val modeDistribution: Map<String, Float> = mapOf(
        "办公模式" to 0.6f,
        "休息模式" to 0.25f,
        "娱乐模式" to 0.15f
    )
)

enum class ChairMode {
    OFFICE,         // 办公
    REST,           // 休息
    ENTERTAINMENT,  // 娱乐
    CUSTOM,         // 🔥 新增：自定义模式
    NONE            // 🔥 新增：默认/无模式 (初始状态)
}

data class CustomPreset(
    val id: String,
    val name: String,
    val backAngle: Float,
    val seatAngle: Float,
    val height: Float
)

data class HealthScoreDetail(
    val totalScore: Int = 80,
    val ranking: String = "击败72%用户",
    val components: List<ScoreComponent> = listOf(
        ScoreComponent("坐姿时长", 8, 10),
        ScoreComponent("姿势切换", 7, 10),
        ScoreComponent("久坐提醒", 9, 10),
        ScoreComponent("坐姿稳定", 8, 10)
    ),
    val suggestion: String = "建议每45分钟起身活动5分钟，保持腰椎自然曲线。"
)

data class ScoreComponent(
    val name: String,
    val score: Int,
    val maxScore: Int
)

data class FirmwareInfo(
    val currentVersion: String = "v1.2.3",
    val latestVersion: String = "v1.3.0",
    val hasUpdate: Boolean = true,
    val updateProgress: Float = 0f
)

data class DeviceStatus(
    val isConnected: Boolean = false,
    val deviceName: String = "未连接"
)
