package com.example.spineassistant.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.spineassistant.data.AppDatabase
import com.example.spineassistant.network.NetworkModule
import com.example.spineassistant.network.UserFeedbackRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class SyncFeedbackWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val dao = AppDatabase.getDatabase(context).feedbackDao()
    private val mapper = jacksonObjectMapper()

    override suspend fun doWork(): Result {
        Log.i("SyncWorker", "开始同步反馈数据...")

        // 🔥 确保 NetworkModule 已初始化 (Worker 运行在后台，可能 App 已被杀掉重启)
        try {
            NetworkModule.init(applicationContext)
        } catch (e: Exception) {
            // 忽略重复初始化异常
        }

        try {
            // 1. 获取本地未同步的数据
            val unsyncedList = dao.getUnsyncedFeedbacks()
            if (unsyncedList.isEmpty()) {
                Log.i("SyncWorker", "没有需要同步的数据")
                return Result.success()
            }

            val syncedIds = mutableListOf<Long>()

            // 2. 逐条上传
            for (feedback in unsyncedList) {
                // 解析 finalStateJson: {"height": 750, "angle": 100}
                val stateMap: Map<String, Any> = try {
                    mapper.readValue(feedback.finalStateJson)
                } catch (e: Exception) {
                    emptyMap()
                }

                val finalHeight = stateMap["height"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                val finalAngle = stateMap["angle"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0

                // 构造请求对象 (这里暂时用假的用户ID，后续应从 UserPreferences 获取)
                val request = UserFeedbackRequest(
                    userId = "user-local-001", // 注意：后端鉴权后，这个字段其实被忽略了，主要靠 Token
                    heightCm = 175,
                    weightKg = 70f,
                    finalHeightMm = finalHeight,
                    finalAngleDeg = finalAngle,
                    problemArea = feedback.problemArea,
                    currentMode = feedback.mode
                )

                try {
                    // 🔥 核心修改：使用 getApiService() 方法
                    val response = NetworkModule.getApiService().uploadFeedback(request)
                    if (response.isSuccessful) {
                        syncedIds.add(feedback.id)
                    } else {
                        Log.e("SyncWorker", "上传失败 ID ${feedback.id}: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("SyncWorker", "网络异常 ID ${feedback.id}: ${e.message}")
                    // 网络异常时不中断循环，尝试下一个，或者直接返回 Retry
                }
            }

            // 3. 更新本地数据库状态
            if (syncedIds.isNotEmpty()) {
                dao.markAsSynced(syncedIds)
                Log.i("SyncWorker", "成功同步 ${syncedIds.size} 条记录")
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e("SyncWorker", "同步任务崩溃", e)
            return Result.failure()
        }
    }
}
