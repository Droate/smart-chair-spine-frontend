package com.spineassistant.simulator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.spineassistant.models.ChairState
import com.spineassistant.simulator.communication.BluetoothServerManager
import com.spineassistant.simulator.communication.ConnectionState
import com.spineassistant.simulator.communication.IChairCommunication
import com.spineassistant.simulator.websocket.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

const val USE_BLUETOOTH = true
const val HEIGHT_SPEED = 5
const val ANGLE_SPEED = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChairSimulatorScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("SimPrefs", Context.MODE_PRIVATE) }

    // 🔥 安全获取震动服务
    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    val savedMac = prefs.getString("SAVED_MAC", "") ?: ""
    var deviceMacInput by remember { mutableStateOf(TextFieldValue(text = savedMac, selection = TextRange(savedMac.length))) }

    val defaultState = remember { ChairState.default("simulator-001") }
    var currentChairState by remember { mutableStateOf(defaultState.copy(height = 450)) }
    var targetChairState by remember { mutableStateOf(defaultState.copy(height = 450)) }

    // --- 模拟器本地物理状态 ---
    var isOccupied by remember { mutableStateOf(false) }
    // 🔥 [v1.7] 靠背压力 (0-100)
    var backPressure by remember { mutableFloatStateOf(0f) }
    // 🔥 [v1.7] 异常计时相关
    var abnormalPostureStartTime by remember { mutableLongStateOf(0L) }
    var lastAlarmTime by remember { mutableLongStateOf(0L) } // 上一次报警时间，用于控制频率

    var simulatorPanelExpanded by remember { mutableStateOf(true) }
    val debugLogs = remember { mutableStateListOf<String>() }

    val mainScrollState = rememberScrollState()
    val logScrollState = rememberScrollState()

    var showQrCode by remember { mutableStateOf(false) }
    var isVibratingUI by remember { mutableStateOf(false) }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else emptyArray()

    val communicationManager: IChairCommunication = remember {
        if (USE_BLUETOOTH) BluetoothServerManager(context) else WebSocketManager()
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.entries.all { it.value }) {
            debugLogs.add("[权限] 蓝牙权限已获取，正在启动服务...")
            coroutineScope.launch { communicationManager.start() }
        } else {
            debugLogs.add("[权限] 警告: 未授予必要的蓝牙权限！")
        }
    }

    val connectionState = communicationManager.connectionState.collectAsState().value
    val isConnected = connectionState == ConnectionState.Connected

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        coroutineScope.launch(Dispatchers.Main) {
            debugLogs.add("[$time] $msg")
        }
    }

    @SuppressLint("MissingPermission")
    fun triggerVibration() {
        coroutineScope.launch {
            try {
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // 震动 500ms
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    }
                } else {
                    addLog("⚠️ 设备不支持震动")
                }
            } catch (e: Exception) {
                addLog("❌ 震动失败: ${e.message}")
            }

            // UI 变红 500ms
            withContext(Dispatchers.Main) {
                isVibratingUI = true
                // 只有不是连续报警时才打印日志，避免刷屏，或者修改日志内容
                // addLog("📳 执行震动反馈")
            }

            delay(500)

            withContext(Dispatchers.Main) {
                isVibratingUI = false
            }
        }
    }

    fun onMacInputChange(newValue: TextFieldValue) {
        val rawInput = newValue.text.uppercase().replace(Regex("[^0-9A-F]"), "")
        val truncated = if (rawInput.length > 12) rawInput.substring(0, 12) else rawInput
        val formatted = StringBuilder()
        for (i in truncated.indices) {
            formatted.append(truncated[i])
            if ((i + 1) % 2 == 0 && i != truncated.lastIndex) formatted.append(":")
        }
        val finalStr = formatted.toString()
        deviceMacInput = TextFieldValue(finalStr, TextRange(finalStr.length))
        prefs.edit().putString("SAVED_MAC", finalStr).apply()
    }

    LaunchedEffect(Unit) {
        var lastLogTime = 0L
        while (true) {
            // [v1.7] 更新 ChairState
            val stateChanged = currentChairState != targetChairState ||
                    currentChairState.isOccupied != isOccupied ||
                    currentChairState.backPressure != backPressure.toInt()

            if(stateChanged) {
                var newState = currentChairState.copy(
                    isOccupied = isOccupied,
                    backPressure = backPressure.toInt()
                )
                val currentTime = System.currentTimeMillis()

                // 模拟物理移动
                if (newState.height != targetChairState.height) {
                    val diff = targetChairState.height - newState.height
                    val step = if (diff > 0) HEIGHT_SPEED else -HEIGHT_SPEED
                    newState = newState.copy(height = if (abs(diff) <= HEIGHT_SPEED) targetChairState.height else newState.height + step)
                }
                if (newState.angle != targetChairState.angle) {
                    val diff = targetChairState.angle - newState.angle
                    val step = if (diff > 0) ANGLE_SPEED else -ANGLE_SPEED
                    newState = newState.copy(angle = if (abs(diff) <= ANGLE_SPEED) targetChairState.angle else newState.angle + step)
                }

                currentChairState = newState
                if (currentTime - lastLogTime > 500 || currentChairState == targetChairState) lastLogTime = currentTime
            }

            // 🔥 [v1.7] IoT 闭环报警系统
            val currentTime = System.currentTimeMillis()

            // 逻辑：必须有人入座
            if (isOccupied) {
                // 如果压力过小 (前倾)
                if (backPressure < 30) {
                    // 如果是刚开始异常，记录开始时间
                    if (abnormalPostureStartTime == 0L) {
                        abnormalPostureStartTime = currentTime
                    }
                    // 如果持续异常超过 5秒 (5000ms)
                    else if (currentTime - abnormalPostureStartTime > 5000) {
                        // 进入“持续报警”模式
                        // 检查距离上次报警是否超过 3秒 (3000ms) - 实现脉冲式报警
                        if (currentTime - lastAlarmTime > 3000) {
                            addLog("⚠️ [IoT] 持续前倾(压力低) -> 触发报警循环")
                            triggerVibration() // 调用核心报警功能
                            lastAlarmTime = currentTime
                        }
                    }
                } else {
                    // 压力恢复正常，重置所有计时器
                    abnormalPostureStartTime = 0L
                    lastAlarmTime = 0L // 重置报警计时，确保下次异常从头开始算
                }
            } else {
                // 没人坐，重置
                abnormalPostureStartTime = 0L
                lastAlarmTime = 0L
            }

            delay(50)
        }
    }

    LaunchedEffect(currentChairState) { if (isConnected) communicationManager.sendState(currentChairState) }
    LaunchedEffect(Unit) { communicationManager.debugLog.collect { addLog(it) } }

    LaunchedEffect(Unit) {
        val mapper = jacksonObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        communicationManager.incomingMessages.collect { json ->
            try {
                if (json.contains("ALERT_VIBRATION")) {
                    triggerVibration()
                } else {
                    val map = try { mapper.readValue<Map<String, Any>>(json) } catch (e: Exception) { null }
                    if (map != null) {
                        val command = map["command"]?.toString()
                        val params = map["parameters"] as? Map<String, Any> ?: emptyMap()
                        addLog("CMD: $command")

                        when (command) {
                            "ALERT_VIBRATION", "CommandType.ALERT_VIBRATION" -> triggerVibration()

                            "REQUEST_STATE" -> communicationManager.sendState(currentChairState)
                            "ADJUST_HEIGHT", "CommandType.ADJUST_HEIGHT" -> {
                                val hRaw = params["height"]
                                val h = hRaw.toString().toDoubleOrNull()?.toInt()
                                if (h != null) targetChairState = targetChairState.copy(height = h.coerceIn(350, 600))
                            }
                            "ADJUST_ANGLE", "CommandType.ADJUST_ANGLE" -> {
                                val aRaw = params["angle"]
                                val a = aRaw.toString().toDoubleOrNull()?.toInt()
                                if (a != null) targetChairState = targetChairState.copy(angle = a.coerceIn(90, 135))
                            }
                            "APPLY_PRESET", "CommandType.APPLY_PRESET" -> {
                                val presetName = params["presetName"]?.toString()
                                when(presetName) {
                                    "ZERO_GRAVITY" -> targetChairState = targetChairState.copy(height = 400, angle = 120)
                                    "OFFICE" -> targetChairState = targetChairState.copy(height = 480, angle = 95)
                                    "ENTERTAINMENT" -> targetChairState = targetChairState.copy(height = 420, angle = 110)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { addLog("💥 异常: ${e.message}") }
        }
    }

    fun onManualControl(newState: ChairState) { targetChairState = newState }
    DisposableEffect(Unit) { onDispose { communicationManager.stop() } }
    LaunchedEffect(debugLogs.size) { if (debugLogs.isNotEmpty()) logScrollState.animateScrollTo(logScrollState.maxValue) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("座椅模拟器 ", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = { IconButton(onClick = { showQrCode = !showQrCode }) { Icon(Icons.Default.Info, "二维码", tint = Color.White) } }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(mainScrollState)) {
            if (showQrCode) {
                QrCodeCard(deviceName = "SmartChairSimulator", macInput = deviceMacInput, onMacChange = { onMacInputChange(it) })
            }

            Card(modifier = Modifier.fillMaxWidth().height(280.dp).padding(16.dp)) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(if (isVibratingUI) Color.Red.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    PremiumChairVisualization(chairState = currentChairState)
                    if (isVibratingUI) {
                        Text("📳 震动提醒中...", color = Color.Red, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    if (!isConnected) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            Text("等待连接...", color = Color.White)
                        }
                    }
                }
            }

            // 🔥 [v1.7] 物理环境模拟卡片，增加压力传感器滑块
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("🛡️ 物理环境模拟", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(text = if (isOccupied) "状态: 用户已坐下" else "状态: 空闲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Switch(checked = isOccupied, onCheckedChange = { isOccupied = it }, thumbContent = { if (isOccupied) Text("👤", fontSize = 10.sp) })
                    }

                    if (isOccupied) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("靠背压力传感器: ${backPressure.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Slider(
                            value = backPressure,
                            onValueChange = { backPressure = it },
                            valueRange = 0f..100f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("离开椅背(前倾)", fontSize = 10.sp, color = Color.Red)
                            Text("紧贴椅背", fontSize = 10.sp, color = Color.Green)
                        }

                        if (backPressure < 30) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("⚠️ 检测到背部离开，5秒后将触发持续报警", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable { simulatorPanelExpanded = !simulatorPanelExpanded }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("控制面板", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Icon(if (simulatorPanelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                    }
                    if (simulatorPanelExpanded) {
                        HorizontalDivider()
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (USE_BLUETOOTH) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bluetoothPermissions.isNotEmpty()) permissionLauncher.launch(bluetoothPermissions)
                                            else coroutineScope.launch { communicationManager.start() }
                                        } else coroutineScope.launch { communicationManager.start() }
                                    },
                                    enabled = !isConnected
                                ) { Text("启动服务") }
                                Button(onClick = { communicationManager.stop() }, enabled = isConnected) { Text("断开") }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("目标高度: ${targetChairState.height}")
                            Slider(value = targetChairState.height.toFloat(), onValueChange = { onManualControl(targetChairState.copy(height = it.roundToInt())) }, valueRange = 350f..600f)
                            Text("目标角度: ${targetChairState.angle}")
                            Slider(value = targetChairState.angle.toFloat(), onValueChange = { onManualControl(targetChairState.copy(angle = it.roundToInt())) }, valueRange = 90f..135f)
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("调试信息", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(debugLogs.joinToString("\n")))
                            Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                        }) { Text("复制", color = Color.Cyan) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2D2D2D)).verticalScroll(logScrollState).padding(8.dp)) {
                            Text(text = if (debugLogs.isEmpty()) "暂无日志..." else debugLogs.joinToString("\n"), color = Color(0xFF00FF00), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
