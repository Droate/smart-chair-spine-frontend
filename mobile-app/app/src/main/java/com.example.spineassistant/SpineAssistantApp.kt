package com.example.spineassistant

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.spineassistant.communication.BluetoothClientManager
import com.example.spineassistant.data.AppDatabase
import com.example.spineassistant.data.FeedbackEntity
import com.example.spineassistant.models.CustomPreset
import com.example.spineassistant.models.DeviceStatus
import com.example.spineassistant.models.FirmwareInfo
import com.example.spineassistant.network.ChatControlRequest
import com.example.spineassistant.network.NetworkModule
import com.example.spineassistant.network.UserFeedbackRequest
import com.example.spineassistant.ui.*
import com.example.spineassistant.viewmodel.ReportViewModel // 🔥🔥 关键：显式导入 VM
import com.example.spineassistant.workers.SyncFeedbackWorker
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.spineassistant.protocol.ControlCommand

/**
 * 整个应用的根 Composable 函数，包含导航图和全局状态管理。
 * 它负责：
 * - 初始化网络模块（用于自动发现后端 IP）
 * - 蓝牙管理器的实例化与生命周期管理
 * - 本地预设（Preset）列表的维护
 * - 智能调节流程的状态跟踪
 * - 各屏幕的导航配置
 * - 全局 Snackbar 的显示
 */
@OptIn(ExperimentalMaterial3Api::class) // 标记使用实验性的 Material 3 API
@SuppressLint("StateFlowValueCalledInComposition") // 忽略在 Composable 中直接调用 StateFlow.value 的 lint 警告（我们通过 collectAsState 安全使用）
@Composable
fun SpineAssistantApp() {
    // 获取当前的 Android Context（用于 Toast、数据库、网络初始化等）
    val context = LocalContext.current

    // LaunchedEffect 是一个 Compose 副作用 API，用于在组合进入时执行一次挂起操作。
    // 传入 Unit 作为 key，表示它只会执行一次，不会在重组时重复执行。
    LaunchedEffect(Unit) {
        NetworkModule.init(context) // 初始化网络模块（NSD 服务发现）
    }

    // 导航控制器，用于在不同屏幕之间跳转
    val navController = rememberNavController()
    // 获取一个协程作用域，可以在 Composable 中启动协程（例如点击事件中执行挂起函数）
    val scope = rememberCoroutineScope()

    // SnackbarHostState 用于控制 Snackbar 的显示（例如 showSnackbar 方法）
    val snackbarHostState = remember { SnackbarHostState() }

    // 创建蓝牙管理器实例，remember 保证它在重组时不会被重新创建
    val bluetoothManager = remember { BluetoothClientManager(context) }
    // 获取本地 Room 数据库的反馈表 DAO
    val feedbackDao = remember { AppDatabase.getDatabase(context).feedbackDao() }

    // 从蓝牙管理器的 StateFlow 中收集状态，collectAsState 会将 Flow 转换为 Compose 可观察的 State，
    // 当 Flow 有新数据时，使用这些状态的 Composable 会自动重组。
    val chairState by bluetoothManager.chairStateFlow.collectAsState()
    val connectionState by bluetoothManager.connectionState.collectAsState()
    val scannedDevices by bluetoothManager.scannedDevices.collectAsState()

    // 获取报告页面的 ViewModel（viewModel() 会返回已存在的实例或创建新实例，并在配置变更后保留）
    val reportViewModel: ReportViewModel = viewModel()
    // 收集 ViewModel 中的健康数据流
    val healthData by reportViewModel.healthData.collectAsState()
    val healthScoreDetail by reportViewModel.healthScoreDetail.collectAsState()

    // 预设列表（包括系统预设和用户自定义预设）
    // mutableStateListOf 创建一个可观察的列表，当列表内容变化时，所有引用它的 Composable 都会重组。
    val presets = remember {
        mutableStateListOf(
            CustomPreset("office", "办公模式", 95f, 5f, 480f),
            CustomPreset("rest", "休息模式", 120f, 12f, 420f),
            CustomPreset("entertainment", "娱乐模式", 110f, 10f, 440f),
            CustomPreset(System.currentTimeMillis().toString(), "我的观影", 115f, 10f, 430f)
        )
    }

    // 当前激活的预设 ID（用于高亮显示和判断当前模式）
    var activePresetId by remember { mutableStateOf<String?>(null) }
    // 智能调节流程中记录的上次问题区域（例如“腰部支撑”）
    var lastProblemArea by remember { mutableStateOf("") }
    // 智能调节循环次数（用户点击“还不够”的次数）
    var currentAdjustLoopCount by remember { mutableIntStateOf(0) }
    // 上次反馈时用户选择的不适强度（1-5）
    var lastInitialIntensity by remember { mutableFloatStateOf(0f) }
    // 固件信息（目前为静态占位）
    var firmwareInfo by remember { mutableStateOf(FirmwareInfo()) }

    // 决定应用的起始页面：如果已有有效的 Token 则进入主页，否则进入登录页
    val startDest = try {
        NetworkModule.init(context) // 再次确保初始化（虽然上面已调用，但无副作用）
        if (NetworkModule.getTokenManager().hasToken()) "home" else "login"
    } catch (e: Exception) {
        Log.e("AppStart", "网络模块初始化异常", e)
        "login"
    }

    fun executeAiControl(userInput: String, onReplyReceived: (String) -> Unit) {
        if (connectionState != BluetoothClientManager.ConnectionState.Connected) {
            onReplyReceived("抱歉，请先连接座椅蓝牙再使用此功能。")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // 1. 获取当前状态发给后端
                val request = ChatControlRequest(
                    userInput = userInput,
                    currentHeight = chairState.height,
                    currentAngle = chairState.angle
                )

                // 2. 请求 LangChain 接口
                val response = NetworkModule.getApiService().chatControlChair(request)

                if (response.isSuccessful && response.body() != null) {
                    val chatResponse = response.body()!!

                    // 3. 把大模型的文字回传给 UI
                    launch(Dispatchers.Main) {
                        onReplyReceived(chatResponse.reply)
                    }

                    // 4. 解析并执行动作 (Action)
                    chatResponse.actions.forEach { action ->
                        try {
                            when (action.command) {
                                "APPLY_PRESET" -> {
                                    val presetName = action.parameters["presetName"]?.toString() ?: ""
                                    // 模拟你在 HomeScreen 里的模式切换逻辑
                                    val targetPreset = presets.find { it.id.equals(presetName, ignoreCase = true) }
                                    if (targetPreset != null) {
                                        activePresetId = targetPreset.id
                                        bluetoothManager.setCurrentMode(targetPreset.id.uppercase())
                                        bluetoothManager.updateHeight(targetPreset.height.toInt())
                                        delay(200) // 防止粘包
                                        bluetoothManager.updateAngle(targetPreset.backAngle.toInt())
                                    }
                                }
                                "ADJUST_HEIGHT" -> {
                                    val h = (action.parameters["height"] as? Double)?.toInt()
                                    if (h != null) bluetoothManager.updateHeight(h)
                                }
                                "ADJUST_ANGLE" -> {
                                    val a = (action.parameters["angle"] as? Double)?.toInt()
                                    if (a != null) bluetoothManager.updateAngle(a)
                                }
                                "ALERT_VIBRATION" -> {
                                    val cmd = ControlCommand(
                                        deviceId = "app",
                                        command = ControlCommand.CommandType.ALERT_VIBRATION
                                    )
                                    bluetoothManager.sendCommand(cmd)
                                }
                            }
                            delay(300) // 多条指令间的缓冲
                        } catch (e: Exception) {
                            Log.e("AiControl", "指令解析失败: ${action.command}", e)
                        }
                    }
                } else {
                    launch(Dispatchers.Main) { onReplyReceived("服务器开小差了，请稍后再试。") }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) { onReplyReceived("网络异常: ${e.message}") }
            }
        }
    }

    // ==================== 内部辅助函数 ====================

    /**
     * 上传隐式反馈（手动调节后自动上传，用于云端模型训练）。
     * 参数：
     * - finalHeight: 最终确定的高度（mm）
     * - finalAngle: 最终确定的角度（°）
     * - currentMode: 当前模式（如 OFFICE）
     */
    fun uploadImplicitFeedback(
        finalHeight: Int,
        finalAngle: Int,
        currentMode: String
    ) {
        // 注：这里硬编码了身高体重，实际应从用户档案获取，此处为简化演示
        val userHeight = 175
        val userWeight = 70f
        val userId = NetworkModule.getTokenManager().getToken() ?: "demo_user"

        // 在 IO 线程中执行网络请求，避免阻塞主线程
        scope.launch(Dispatchers.IO) {
            try {
                val feedbackReq = UserFeedbackRequest(
                    userId = userId,
                    heightCm = userHeight,
                    weightKg = userWeight,
                    finalHeightMm = finalHeight,
                    finalAngleDeg = finalAngle,
                    problemArea = "MANUAL_ADJUST", // 标记为手动调节
                    currentMode = currentMode
                )

                Log.d("Feedback", "📤 正在上传手动调节数据: Mode=$currentMode, H=$finalHeight, A=$finalAngle")

                // 调用 Retrofit API 上传反馈
                val response = NetworkModule.getApiService().uploadFeedback(feedbackReq)
                if (response.isSuccessful) {
                    Log.d("Feedback", "✅ 手动调节数据已上传云端，模型将自动进化！")
                } else {
                    Log.e("Feedback", "❌ 上传失败: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("Feedback", "❌ 网络异常: ${e.message}", e)
            }
        }
    }

    /**
     * 检查手动调节是否偏离当前激活的预设。
     * 如果偏离超过阈值（高度 ±20mm，角度 ±5°），弹出 Snackbar 询问是否更新预设。
     * 参数：
     * - newValue: 新的数值（高度或角度）
     * - type: "height" 或 "angle"，表示调节的是高度还是角度
     */
    fun checkForDeviationAndNotify(newValue: Float, type: String) {
        val activePreset = activePresetId?.let { id -> presets.find { it.id == id } }
        if (activePreset == null) return

        // 判断是否偏离阈值
        val isDeviated = when (type) {
            "height" -> abs(activePreset.height - newValue) > 20
            "angle" -> abs(activePreset.backAngle - newValue) > 5
            else -> false
        }

        if (isDeviated) {
            // 启动协程显示 Snackbar（showSnackbar 是挂起函数）
            scope.launch {
                // showSnackbar 会在底部显示提示条，并返回用户点击的结果（ActionPerformed 或 Dismissed）
                val result = snackbarHostState.showSnackbar(
                    message = "检测到您手动调整了'${activePreset.name}'",
                    actionLabel = "更新预设",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val index = presets.indexOf(activePreset)
                    if (index != -1) {
                        // 根据调节类型更新预设的对应字段
                        val updatedPreset = when (type) {
                            "height" -> activePreset.copy(height = newValue)
                            "angle" -> activePreset.copy(backAngle = newValue)
                            else -> activePreset
                        }
                        presets[index] = updatedPreset // 更新列表，触发 UI 重组
                        Toast.makeText(context, "'${activePreset.name}' 已更新！", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ==================== UI 骨架 ====================
    // Scaffold 提供 Material Design 布局骨架，这里我们只使用它的 snackbarHost 插槽。
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { contentPadding ->
        // NavHost 是导航组件容器，负责根据当前目的地显示对应的 Composable
        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(contentPadding) // 应用 Scaffold 的 padding 避免被遮挡
        ) {
            // ----- 登录页面 -----
            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        // 登录成功后跳转到主页，并清除返回栈中的登录页（防止按返回键回到登录页）
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            // ----- 主页（仪表盘） -----
            composable("home") {
                /**
                 * 执行智能调节的内部函数（由 FeedbackBottomSheet 调用）
                 * - area: 问题区域（如“腰部支撑”）
                 * - intensity: 不适强度（1-5）
                 */
                fun executeSmartAdjust(area: String, intensity: Float) {
                    scope.launch {
                        // 智能调节时，暂时取消当前预设的激活状态（进入临时调节模式）
                        activePresetId = null
                        lastProblemArea = area
                        lastInitialIntensity = intensity
                        currentAdjustLoopCount++

                        // 根据问题区域执行相应的调节动作（通过蓝牙发送指令）
                        when(area) {
                            "腰部支撑" -> bluetoothManager.updateAngle((chairState.angle - (intensity * 2).toInt()).coerceAtLeast(90))
                            "坐垫高度" -> bluetoothManager.updateHeight((chairState.height - (intensity * 10).toInt()).coerceAtLeast(350))
                            "椅背角度" -> bluetoothManager.updateAngle((chairState.angle + (intensity * 3).toInt()).coerceAtMost(135))
                            "久坐疲劳" -> bluetoothManager.applyPreset("REST")
                        }
                    }
                }

                // 调用主页 UI 组件，传递各种回调
                HomeScreen(
                    chairState = chairState,
                    deviceStatus = DeviceStatus(isConnected = connectionState == BluetoothClientManager.ConnectionState.Connected),
                    customPresets = presets,
                    activePresetId = activePresetId,
                    // 点击预设模式按钮
                    onModeChange = { preset ->
                        activePresetId = preset.id
                        bluetoothManager.setCurrentMode(preset.id.uppercase())

                        // 先调高度，再调角度，间隔 100ms 避免指令冲突
                        scope.launch {
                            bluetoothManager.updateHeight(preset.height.toInt())
                            delay(100)
                            bluetoothManager.updateAngle(preset.backAngle.toInt())
                        }
                    },
                    // 智能反馈：第一次调节
                    onSmartAdjust = { area, intensity ->
                        currentAdjustLoopCount = 0
                        executeSmartAdjust(area, intensity)
                    },
                    // 智能反馈：再次微调（点击“还不够”）
                    onRequestMoreAdjust = {
                        if (lastProblemArea.isNotEmpty()) executeSmartAdjust(lastProblemArea, 1.0f)
                    },
                    // 智能反馈：保存为新预设（点击“舒服多了”后）
                    onSaveNewPreset = { name ->
                        val newPreset = CustomPreset(
                            System.currentTimeMillis().toString(),
                            name,
                            chairState.angle.toFloat(),
                            0f,
                            chairState.height.toFloat()
                        )
                        presets.add(newPreset)
                        activePresetId = newPreset.id
                        bluetoothManager.setCurrentMode(newPreset.id.uppercase())

                        scope.launch {
                            // 将本次智能调节过程保存为本地反馈记录（用于后续同步到云端）
                            val stateJson = jacksonObjectMapper().writeValueAsString(mapOf(
                                "height" to chairState.height,
                                "angle" to chairState.angle
                            ))
                            val feedback = FeedbackEntity(
                                problemArea = lastProblemArea,
                                initialIntensity = lastInitialIntensity,
                                adjustLoopCount = currentAdjustLoopCount,
                                finalStateJson = stateJson,
                                wasSavedAsPreset = true,
                                isSynced = false,
                                mode = activePresetId?.uppercase() ?: "CUSTOM"
                            )
                            feedbackDao.insert(feedback)

                            // 触发后台同步 Worker：将未上传的反馈数据上传到云端
                            // OneTimeWorkRequestBuilder 创建一次性任务
                            // Constraints 指定任务运行条件（需要网络）
                            val workRequest = OneTimeWorkRequestBuilder<SyncFeedbackWorker>()
                                .setConstraints(
                                    Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build()
                                )
                                .build()
                            WorkManager.getInstance(context).enqueue(workRequest)

                            // 重置临时状态
                            currentAdjustLoopCount = 0
                            lastProblemArea = ""
                        }
                    },
                    onNavigateToHeight = { navController.navigate("height") },
                    onNavigateToAngle = { navController.navigate("angle") },
                    onNavigateToCustomPresets = { navController.navigate("customPresets") },
                    onNavigateToReport = { navController.navigate("report") },
                    onNavigateToProfile = { navController.navigate("profile") },
                    onToggleDeviceConnection = {
                        if (connectionState == BluetoothClientManager.ConnectionState.Connected) {
                            bluetoothManager.disconnect()
                        } else {
                            navController.navigate("scan") // 未连接则进入扫描页面
                        }
                    },
                    onExecuteAiControl = { input, callback -> executeAiControl(input, callback) }
                )
            }

            // ----- 设备扫描页面（蓝牙）-----
            composable("scan") {
                DeviceScanScreen(
                    scannedDevices = scannedDevices,
                    onStartScan = { bluetoothManager.startScan() },
                    onStopScan = { bluetoothManager.stopScan() },
                    onConnectDevice = { device ->
                        bluetoothManager.connectToDevice(device)
                        navController.popBackStack() // 连接后返回上一页
                    },
                    onNavigateToQrScan = {
                        navController.navigate("qr_scan") // 跳转到二维码扫描页
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // ----- 二维码扫描页面（用于快速连接模拟器）-----
            composable("qr_scan") {
                QrScanScreen(
                    onMacFound = { mac ->
                        bluetoothManager.connectByMacAddress(mac)
                        // 扫码连接后直接返回主页，并清除返回栈中的扫码页，防止按返回键回到扫码页
                        navController.popBackStack("home", inclusive = false)
                        Toast.makeText(context, "正在连接: $mac", Toast.LENGTH_SHORT).show()
                    },
                    onClose = { navController.popBackStack() }
                )
            }

            // ----- AI 坐姿监测页面（基于摄像头）-----
            composable("posture_monitor") {
                PostureMonitorScreen(
                    onBack = { navController.popBackStack() },
                    onPostureWarning = {
                        // 当检测到不良姿态时，通过蓝牙发送震动报警指令
                        scope.launch {
                            if (connectionState == BluetoothClientManager.ConnectionState.Connected) {
                                val cmd = ControlCommand(
                                    deviceId = "app",
                                    command = ControlCommand.CommandType.ALERT_VIBRATION,
                                    parameters = emptyMap()
                                )
                                bluetoothManager.sendCommand(cmd)
                            }
                        }
                    }
                )
            }

            // ----- 高度调节页面 -----
            composable("height") {
                HeightAdjustScreen(
                    chairState = chairState,
                    isAutoAdjust = false,
                    onHeightChangeImmediate = { /* No-op */ }, // 不需要实时响应，只保留滑动效果
                    onFinalHeightSet = { finalHeight ->
                        bluetoothManager.updateHeight(finalHeight.toInt())
                        checkForDeviationAndNotify(finalHeight, "height")
                        activePresetId?.let { modeId ->
                            uploadImplicitFeedback(
                                finalHeight = finalHeight.toInt(),
                                finalAngle = chairState.angle,
                                currentMode = modeId.uppercase()
                            )
                        }
                    },
                    onAutoAdjustToggle = { }, // 未实现自动调节切换
                    onCancel = { navController.popBackStack() }
                )
            }

            // ----- 角度调节页面 -----
            composable("angle") {
                AngleAdjustScreen(
                    chairState = chairState,
                    isCushionFollow = true,
                    onAngleChangeImmediate = { /* No-op */ },
                    onFinalAngleSet = { finalAngle ->
                        bluetoothManager.updateAngle(finalAngle.toInt())
                        checkForDeviationAndNotify(finalAngle, "angle")
                        activePresetId?.let { modeId ->
                            uploadImplicitFeedback(
                                finalHeight = chairState.height,
                                finalAngle = finalAngle.toInt(),
                                currentMode = modeId.uppercase()
                            )
                        }
                    },
                    onCushionFollowToggle = { }, // 未实现坐垫联动切换
                    onCancel = { navController.popBackStack() }
                )
            }

            // ----- 报告页面（健康数据统计）-----
            composable("report") {
                ReportScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToHome = { navController.navigate("home") },
                    onNavigateToProfile = { navController.navigate("profile") },
                    onNavigateToHealthScoreDetail = { navController.navigate("health_detail") },
                    viewModel = reportViewModel
                )
            }

            // ----- 健康分详情页面 -----
            composable("health_detail") {
                HealthScoreDetailScreen(healthScoreDetail, { navController.popBackStack() })
            }

            // ----- 个人资料页面 -----
            composable("profile") {
                ProfileScreen(
                    autoPopupEnabled = true,
                    firmwareInfo = firmwareInfo,
                    currentAppMode = activePresetId?.uppercase() ?: "OFFICE",
                    onAutoPopupToggle = {},
                    onNavigateToFirmwareUpdate = { },
                    onApplyRecommendation = { recList ->
                        // 应用云端返回的多场景推荐值，更新本地预设
                        scope.launch {
                            recList.forEach { rec ->
                                val targetId = rec.mode.lowercase()
                                val index = presets.indexOfFirst { it.id == targetId }
                                if (index != -1) {
                                    val oldPreset = presets[index]
                                    presets[index] = oldPreset.copy(
                                        height = rec.heightMm.toFloat(),
                                        backAngle = rec.angleDeg.toFloat()
                                    )
                                }
                            }
                            // 如果当前有激活的预设，立即应用其更新后的值
                            val currentPreset = presets.find { it.id == activePresetId }
                            if (currentPreset != null) {
                                bluetoothManager.updateHeight(currentPreset.height.toInt())
                                bluetoothManager.updateAngle(currentPreset.backAngle.toInt())
                            }
                            // 跳回主页，并清除返回栈中的 profile 页面
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                            Toast.makeText(context, "已为您更新所有场景的预设配置！", Toast.LENGTH_LONG).show()
                        }
                    },
                    onNavigateToPostureMonitor = {
                        navController.navigate("posture_monitor")
                    },
                    onLogout = {
                        try {
                            NetworkModule.getTokenManager().clearToken()
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                            Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateToHome = { navController.navigate("home") },
                    onNavigateToReport = { navController.navigate("report") }
                )
            }

            // ----- 自定义预设管理页面 -----
            composable("customPresets") {
                CustomPresetScreen(
                    presets,
                    chairState,
                    { presets.add(it) },
                    { id -> presets.removeIf { it.id == id } },
                    { preset -> },
                    { navController.popBackStack() }
                )
            }
        }
    }
}