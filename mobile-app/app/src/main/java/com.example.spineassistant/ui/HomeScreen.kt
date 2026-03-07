package com.example.spineassistant.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spineassistant.models.CustomPreset
import com.example.spineassistant.models.DeviceStatus
import com.spineassistant.models.ChairState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    chairState: ChairState,
    deviceStatus: DeviceStatus,
    customPresets: List<CustomPreset>,
    activePresetId: String?,
    onModeChange: (preset: CustomPreset) -> Unit,
    onNavigateToHeight: () -> Unit,
    onNavigateToAngle: () -> Unit,
    onNavigateToCustomPresets: () -> Unit,
    onSmartAdjust: (String, Float) -> Unit,
    onRequestMoreAdjust: () -> Unit,
    onSaveNewPreset: (String) -> Unit,
    onToggleDeviceConnection: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    var showFeedbackSheet by remember { mutableStateOf(false) }

    // 🔥 修复：添加滚动状态
    val scrollState = rememberScrollState()

    // 多轮对话式反馈弹窗
    if (showFeedbackSheet) {
        FeedbackBottomSheet(
            onDismiss = { showFeedbackSheet = false },
            onApplySmartAdjust = { area, intensity -> onSmartAdjust(area, intensity) },
            onRequestMoreAdjust = { onRequestMoreAdjust() },
            onSaveNewPreset = { name -> onSaveNewPreset(name) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智脊助手", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White),
                actions = {
                    IconButton(onClick = onToggleDeviceConnection) {
                        Icon(if (deviceStatus.isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled, "蓝牙连接", tint = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.fillMaxWidth()) {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, "首页") }, label = { Text("首页") }, selected = true, onClick = {})
                NavigationBarItem(icon = { Icon(Icons.Default.BarChart, "报告") }, label = { Text("报告") }, selected = false, onClick = onNavigateToReport)
                NavigationBarItem(icon = { Icon(Icons.Default.Person, "我的") }, label = { Text("我的") }, selected = false, onClick = onNavigateToProfile)
            }
        }
    ) { innerPadding ->
        // 🔥 修复：在 Column 上应用 verticalScroll
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState) // 👈 关键修改：允许垂直滚动
                .padding(16.dp)
        ) {
            // Dashboard
            val activePreset = activePresetId?.let { id -> customPresets.find { it.id == id } }
            ChairStatusDashboard(
                chairState = chairState,
                isConnected = deviceStatus.isConnected,
                activePreset = activePreset,
                modifier = Modifier.clickable { onToggleDeviceConnection() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (deviceStatus.isConnected) {
                OccupancyStatusCard(isOccupied = chairState.isOccupied)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 可视化卡片
            Card(
                modifier = Modifier.fillMaxWidth().height(280.dp).padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F7))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        PremiumChairVisualization(chairState = chairState)
                    }
                    if (deviceStatus.isConnected) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).clickable { showFeedbackSheet = true }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoFixHigh, "微调", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("不舒服?", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // 快捷模式标题
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("快捷模式", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onNavigateToCustomPresets, modifier = Modifier.height(36.dp)) { Text("管理", fontSize = 14.sp) }
            }

            // 自定义模式提示
            if (activePreset != null && activePreset.id !in listOf("office", "rest", "entertainment")) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("当前正在运行: ${activePreset.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            // 模式按钮
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                customPresets.filter { it.id in listOf("office", "rest", "entertainment") }.forEach { preset ->
                    val isSelected = activePresetId == preset.id
                    Button(
                        onClick = { onModeChange(preset) },
                        enabled = deviceStatus.isConnected,
                        modifier = Modifier.weight(1f).padding(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                            contentColor = if (isSelected) Color.White else Color.Black
                        )
                    ) {
                        Text(preset.name, fontSize = 13.sp)
                    }
                }
            }

            // 调节按钮
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onNavigateToHeight, enabled = deviceStatus.isConnected, modifier = Modifier.weight(1f).padding(4.dp)) { Text("高度") }
                OutlinedButton(onClick = onNavigateToAngle, enabled = deviceStatus.isConnected, modifier = Modifier.weight(1f).padding(4.dp)) { Text("角度") }
            }

            // 底部留白，防止内容贴底
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
