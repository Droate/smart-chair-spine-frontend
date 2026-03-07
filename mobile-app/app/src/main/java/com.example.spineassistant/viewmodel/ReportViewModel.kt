package com.example.spineassistant.viewmodel // 🔥 必须是 .viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spineassistant.data.AppDatabase
import com.example.spineassistant.models.HealthData
import com.example.spineassistant.models.HealthScoreDetail
import com.example.spineassistant.models.ScoreComponent
import com.example.spineassistant.network.HealthReportRequest
import com.example.spineassistant.network.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 报告页面的 ViewModel，负责：
 * - 从数据库读取坐姿会话数据
 * - 计算今日健康指标（总分、久坐次数、模式分布、7天趋势等）
 * - 通过 StateFlow 将数据暴露给 UI（ReportScreen）
 * - 调用网络 API 获取 AI 生成的健康建议
 *
 * AndroidViewModel 是 ViewModel 的子类，它接收 Application 作为参数，
 * 可以用于获取全局 Context（例如访问数据库）。
 */
class ReportViewModel(application: Application) : AndroidViewModel(application) {

    // 获取数据库中的会话表 DAO（Data Access Object）
    private val dao = AppDatabase.getDatabase(application).sitSessionDao()

    // ---- 内部可变的 StateFlow，用于向 UI 暴露数据 ----
    // MutableStateFlow 是一个可观察的数据容器，它的 .value 发生变化时，
    // 所有通过 .asStateFlow() 订阅的 UI 会自动重组。
    private val _healthData = MutableStateFlow(HealthData())
    // 对外暴露只读的 StateFlow，UI 通过 collectAsState() 收集
    val healthData = _healthData.asStateFlow()

    private val _healthScoreDetail = MutableStateFlow(HealthScoreDetail())
    val healthScoreDetail = _healthScoreDetail.asStateFlow()

    private val _aiAdvice = MutableStateFlow<String?>(null)
    val aiAdvice = _aiAdvice.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading = _isAiLoading.asStateFlow()

    // 保存最近一次计算出的数据，用于后续 AI 请求
    private var pendingRequestData: HealthReportRequest? = null

    init {
        // 当 ViewModel 创建时，立即加载真实数据
        loadRealData()
    }

    /**
     * 从数据库加载过去 7 天的会话数据，计算各项健康指标，
     * 并更新 _healthData、_healthScoreDetail 和 pendingRequestData。
     * 使用 viewModelScope.launch 启动协程，确保在 ViewModel 销毁时自动取消。
     */
    private fun loadRealData() {
        viewModelScope.launch {
            // 获取系统默认时区，用于处理日期边界
            val zoneId = ZoneId.systemDefault()
            val todayDate = LocalDate.now()               // 今天的日期（不含时间）
            val sixDaysAgoDate = todayDate.minusDays(6)   // 6 天前的日期

            // 将日期转换为当天的开始时刻的时间戳（毫秒）
            val startTimestamp = sixDaysAgoDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val todayStartTimestamp = todayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

            // 从数据库获取 startTimestamp 之后的所有已完成会话（Flow 形式）
            // getSessionsSince 返回的是 Flow，我们调用 collect 来收集数据，
            // 每当数据库中的数据变化时，collect 块都会重新执行，实现实时更新。
            dao.getSessionsSince(startTimestamp).collect { allSessions ->
                // --- 1. 计算过去 7 天的每日总坐姿时长（小时）---
                val weeklyHours = FloatArray(7) { 0f } // 初始化长度为 7 的 Float 数组，默认 0f
                allSessions.forEach { session ->
                    // 将会话开始时间戳转换为日期（根据系统时区）
                    val sessionDate = java.time.Instant.ofEpochMilli(session.startTime)
                        .atZone(zoneId).toLocalDate()
                    // 计算该会话日期距离 sixDaysAgoDate 的天数差
                    val daysDiff = ChronoUnit.DAYS.between(sixDaysAgoDate, sessionDate).toInt()
                    if (daysDiff in 0..6) { // 确保在 7 天范围内
                        // 会话时长是分钟，除以 60 转换为小时，累加到对应的天数索引
                        weeklyHours[daysDiff] += (session.durationMinutes / 60f)
                    }
                }

                // --- 2. 筛选出今天的会话（startTime >= 今天 00:00）---
                val todaySessions = allSessions.filter { it.startTime >= todayStartTimestamp }
                // 计算今日总时长（分钟）
                val totalMinutes = todaySessions.sumOf { it.durationMinutes }
                // 将总时长格式化为字符串，例如 "45m" 或 "2.5h"
                val totalHoursStr = if (totalMinutes < 60) "${totalMinutes}m"
                else String.format("%.1fh", totalMinutes / 60f)

                // 今日久坐报警次数（wasCountedAsSedentary 为 true 的会话数）
                val sedentaryCount = todaySessions.count { it.wasCountedAsSedentary }

                // --- 3. 计算今日各模式时长占比 ---
                // modeDistributionRaw 是一个 Map，键为模式名（如 "OFFICE"），值为该模式时长占比（0~1）
                val modeDistributionRaw = if (totalMinutes > 0) {
                    todaySessions.groupBy { it.mode }  // 按模式分组
                        .mapValues { (_, modeSessions) ->
                            // 计算该模式总时长占总时长的比例
                            modeSessions.sumOf { it.durationMinutes }.toFloat() / totalMinutes
                        }
                } else {
                    mapOf("NONE" to 1.0f) // 今日无数据，显示为“无”
                }

                // 将模式名转换为 UI 友好的中文显示
                val modeDistributionUI = modeDistributionRaw.mapKeys { (key, _) ->
                    when(key.uppercase()) {
                        "OFFICE" -> "办公模式"
                        "REST" -> "休息模式"
                        "ENTERTAINMENT" -> "娱乐模式"
                        else -> if (key.isNotEmpty()) key else "其他模式"
                    }
                }

                // --- 4. 计算健康分各项子分数 ---
                // 久坐控制分：满分30，每久坐1次扣5分，最多扣30分
                val penaltySedentary = (sedentaryCount * 5).coerceAtMost(30)
                val scoreSedentary = (30 - penaltySedentary).coerceAtLeast(0)

                // 坐姿时长分：满分20，每30分钟得2分，最多20分
                val scoreDuration = (totalMinutes / 30.0 * 2.0).toInt().coerceAtMost(20)

                // 活跃切换分（姿势变化次数）：满分20，每次变化得0.5分，最多20分
                val totalChanges = todaySessions.sumOf { it.postureChangeCount }
                val scoreActivity = (totalChanges * 0.5).toInt().coerceAtMost(20)

                // 姿态评估分：根据平均角度打分
                // 加权平均角度：每个会话的平均角度乘以该会话时长，再除以总时长
                val weightedAngleSum = todaySessions.sumOf { (it.averageAngle ?: 90) * it.durationMinutes }
                val avgAngle = if (totalMinutes > 0) weightedAngleSum / totalMinutes else 90
                val scorePosture = when(avgAngle) {
                    in 95..115 -> 30      // 理想角度范围
                    in 90..94, in 116..125 -> 25 // 稍偏
                    else -> 15             // 偏差过大
                }

                // 总分 = 四项分数之和，限制在 0~100 之间
                val totalScore = (scoreSedentary + scoreDuration + scoreActivity + scorePosture).coerceIn(0, 100)

                // --- 5. 生成本地健康建议（备用，当 AI 不可用时显示）---
                val fallbackAdvice = StringBuilder()
                if (sedentaryCount > 2) {
                    fallbackAdvice.append("久坐次数过多，请务必每小时起身活动。")
                } else if (totalChanges < 5 && totalMinutes > 60) {
                    fallbackAdvice.append("坐姿过于僵硬，试着多调节椅背角度。")
                } else if (totalScore >= 90) {
                    fallbackAdvice.append("完美！您的坐姿习惯非常健康。")
                } else {
                    fallbackAdvice.append("表现良好，保持现在的节奏。")
                }

                // --- 6. 更新 UI 状态流 ---
                // HealthData 是 UI 层的数据类，包含报告页面所需的所有展示数据
                _healthData.value = HealthData(
                    totalSittingTime = totalHoursStr,
                    postureScore = totalScore,
                    sedentaryReminders = sedentaryCount,
                    healthAdvice = fallbackAdvice.toString(),
                    modeDistribution = modeDistributionUI,
                    weeklyData = weeklyHours.toList() // 转换为 List<Float>
                )

                // HealthScoreDetail 用于健康分详情页面
                _healthScoreDetail.value = HealthScoreDetail(
                    totalScore = totalScore,
                    ranking = "击败${(totalScore * 0.95).toInt()}%用户", // 模拟排名
                    components = listOf(
                        ScoreComponent("久坐控制", scoreSedentary, 30),
                        ScoreComponent("坐姿时长", scoreDuration, 20),
                        ScoreComponent("活跃切换", scoreActivity, 20),
                        ScoreComponent("姿态评估", scorePosture, 30)
                    ),
                    suggestion = fallbackAdvice.toString()
                )

                // --- 7. 保存请求数据，供后续 fetchAIAnalysis 使用 ---
                pendingRequestData = HealthReportRequest(
                    totalHours = (totalMinutes / 60f),
                    sedentaryCount = sedentaryCount,
                    postureScore = totalScore,
                    modeDistribution = modeDistributionRaw, // 原始英文模式名，用于后端
                    weeklyTrend = weeklyHours.toList()
                )
            }
        }
    }

    /**
     * 调用后端 AI 接口获取健康分析建议。
     * 仅在 pendingRequestData 存在且不在加载状态时执行。
     * 更新 _isAiLoading 和 _aiAdvice 状态流。
     */
    fun fetchAIAnalysis() {
        // 如果没有数据或正在加载中，直接返回
        if (pendingRequestData == null || _isAiLoading.value) return

        viewModelScope.launch {
            _isAiLoading.value = true
            try {
                // 调用 Retrofit 接口，analyzeReport 是挂起函数
                val response = NetworkModule.getApiService().analyzeReport(pendingRequestData!!)
                if (response.isSuccessful && response.body() != null) {
                    _aiAdvice.value = response.body()!!.advice
                } else {
                    _aiAdvice.value = "AI 服务繁忙，请稍后再试 (Code: ${response.code()})"
                }
            } catch (e: Exception) {
                Log.e("ReportVM", "AI Error", e)
                _aiAdvice.value = "网络连接失败，无法获取 AI 分析。"
            } finally {
                _isAiLoading.value = false
            }
        }
    }
}