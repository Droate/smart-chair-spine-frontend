package com.example.spineassistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.spineassistant.ui.theme.SpineAssistantTheme

@Composable
fun SpineAssistantApp() {
    val navController = rememberNavController()

    // 全局状态管理
    var chairState by remember {
        mutableStateOf(
            ChairState(
                currentMode = ChairMode.OFFICE,
                height = 48f,
                backAngle = 98f,
                seatAngle = 3f,
                isCushionFollow = true,
                isAutoAdjust = false
            )
        )
    }

    val healthData = remember { mutableStateOf(HealthData()) }
    val healthScoreDetail = remember { mutableStateOf(HealthScoreDetail()) }
    var deviceStatus by remember { mutableStateOf(DeviceStatus()) }
    var firmwareInfo by remember { mutableStateOf(FirmwareInfo()) }
    var customPresets by remember {
        mutableStateOf(
            listOf(
                CustomPreset("1", "午休小睡", 120f, 12f, 45f),
                CustomPreset("2", "观影模式", 110f, 10f, 48f)
            )
        )
    }

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var currentRating by remember { mutableStateOf(0) }
    var autoPopupEnabled by remember { mutableStateOf(true) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    // 舒适度反馈弹窗
    if (showFeedbackDialog) {
        ComfortFeedbackDialog(
            rating = currentRating,
            onRatingChange = { currentRating = it },
            onSubmit = {
                println("提交舒适度评分: $currentRating 星")
                showFeedbackDialog = false
                currentRating = 0
            },
            onDismiss = {
                showFeedbackDialog = false
                currentRating = 0
            }
        )
    }

    // 保存自定义模式弹窗
    if (showSavePresetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { androidx.compose.material3.Text("保存自定义模式") },
            text = {
                var presetName by remember { mutableStateOf("") }
                Column {
                    androidx.compose.material3.Text("请输入模式名称：")
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { androidx.compose.material3.Text("例如：午休小睡") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        // 这里可以添加保存逻辑
                        showSavePresetDialog = false
                    }
                ) {
                    androidx.compose.material3.Text("保存")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showSavePresetDialog = false }
                ) {
                    androidx.compose.material3.Text("取消")
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // 主页
        composable("home") {
            HomeScreen(
                chairState = chairState,
                deviceStatus = deviceStatus,
                customPresets = customPresets,
                onModeChange = { mode ->
                    chairState = chairState.copy(currentMode = mode)
                },
                onNavigateToHeight = { navController.navigate("height") },
                onNavigateToAngle = { navController.navigate("angle") },
                onNavigateToCustomPresets = { navController.navigate("customPresets") },
                onShowFeedback = { showFeedbackDialog = true },
                onSaveAsCustomPreset = { showSavePresetDialog = true },
                onNavigateToReport = { navController.navigate("report") },
                onNavigateToProfile = { navController.navigate("profile") },
                onToggleDeviceConnection = {
                    deviceStatus = deviceStatus.copy(
                        isConnected = !deviceStatus.isConnected
                    )
                }
            )
        }

        // 报告页面
        composable("report") {
            ReportScreen(
                healthData = healthData.value,
                healthScoreDetail = healthScoreDetail.value,
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.navigate("home") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToHealthScoreDetail = { navController.navigate("healthScoreDetail") }
            )
        }

        // 我的页面
        composable("profile") {
            ProfileScreen(
                autoPopupEnabled = autoPopupEnabled,
                firmwareInfo = firmwareInfo,
                onAutoPopupToggle = { autoPopupEnabled = it },
                onNavigateToFirmwareUpdate = { navController.navigate("firmwareUpdate") },
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.navigate("home") },
                onNavigateToReport = { navController.navigate("report") }
            )
        }

        // 调节高度页面
        composable("height") {
            HeightAdjustScreen(
                chairState = chairState,
                onHeightChange = { newHeight ->
                    chairState = chairState.copy(height = newHeight)
                },
                onAutoAdjustToggle = { enabled ->
                    chairState = chairState.copy(isAutoAdjust = enabled)
                },
                onSave = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        // 调节角度页面
        composable("angle") {
            AngleAdjustScreen(
                chairState = chairState,
                onBackAngleChange = { newAngle ->
                    var newSeatAngle = chairState.seatAngle
                    if (chairState.isCushionFollow) {
                        newSeatAngle = newAngle * 0.1f
                    }
                    chairState = chairState.copy(
                        backAngle = newAngle,
                        seatAngle = newSeatAngle
                    )
                },
                onSeatAngleChange = { newAngle ->
                    chairState = chairState.copy(seatAngle = newAngle)
                },
                onCushionFollowToggle = { enabled ->
                    chairState = chairState.copy(isCushionFollow = enabled)
                },
                onModeChange = { mode ->
                    when (mode) {
                        ChairMode.OFFICE -> {
                            chairState = chairState.copy(
                                backAngle = 98f,
                                seatAngle = 3f
                            )
                        }
                        ChairMode.REST -> {
                            chairState = chairState.copy(
                                backAngle = 120f,
                                seatAngle = 12f
                            )
                        }
                        ChairMode.ENTERTAINMENT -> {
                            chairState = chairState.copy(
                                backAngle = 105f,
                                seatAngle = 8f
                            )
                        }
                    }
                },
                onSave = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }

        // 新增：自定义模式管理页面
        composable("customPresets") {
            CustomPresetScreen(
                customPresets = customPresets,
                onAddPreset = { newPreset ->
                    customPresets = customPresets + newPreset
                },
                onDeletePreset = { presetId ->
                    customPresets = customPresets.filter { it.id != presetId }
                },
                onApplyPreset = { preset ->
                    chairState = chairState.copy(
                        backAngle = preset.backAngle,
                        seatAngle = preset.seatAngle,
                        height = preset.height
                    )
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 新增：健康分详情页面
        composable("healthScoreDetail") {
            HealthScoreDetailScreen(
                healthScoreDetail = healthScoreDetail.value,
                onBack = { navController.popBackStack() }
            )
        }

        // 新增：固件更新页面
        composable("firmwareUpdate") {
            FirmwareUpdateScreen(
                firmwareInfo = firmwareInfo,
                onUpdateFirmware = { progress ->
                    firmwareInfo = firmwareInfo.copy(updateProgress = progress)
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSpineAssistantApp() {
    SpineAssistantTheme {
        SpineAssistantApp()
    }
}