package com.example.spineassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable  // 添加这行！
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    chairState: ChairState,
    deviceStatus: DeviceStatus,
    customPresets: List<CustomPreset>,
    onModeChange: (ChairMode) -> Unit,
    onNavigateToHeight: () -> Unit,
    onNavigateToAngle: () -> Unit,
    onNavigateToCustomPresets: () -> Unit,
    onShowFeedback: () -> Unit,
    onSaveAsCustomPreset: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onToggleDeviceConnection: () -> Unit
) {
    var adjustingMode by remember { mutableStateOf<ChairMode?>(null) }

    LaunchedEffect(adjustingMode) {
        if (adjustingMode != null) {
            delay(2000)  // 简化的 delay 调用
            adjustingMode = null
            onShowFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "智脊助手",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                    label = { Text("首页") },
                    selected = true,
                    onClick = { }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "报告") },
                    label = { Text("报告") },
                    selected = false,
                    onClick = onNavigateToReport
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                    label = { Text("我的") },
                    selected = false,
                    onClick = onNavigateToProfile
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 设备连接状态（可点击切换）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { onToggleDeviceConnection() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("设备状态", style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (deviceStatus.isConnected) Color.Green else Color.Red,
                                    RoundedCornerShape(4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (deviceStatus.isConnected) "已连接：${deviceStatus.deviceName}" else "未连接",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (deviceStatus.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 座椅可视化区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    ChairVisualization(chairState = chairState)
                }
            }

            // 快捷模式区（包含自定义模式）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "快捷模式",
                    style = MaterialTheme.typography.titleMedium
                )
                if (customPresets.isNotEmpty()) {
                    TextButton(
                        onClick = onNavigateToCustomPresets,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("管理", fontSize = 14.sp)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 内置模式
                listOf(
                    "办公模式" to ChairMode.OFFICE,
                    "休息模式" to ChairMode.REST,
                    "娱乐模式" to ChairMode.ENTERTAINMENT
                ).forEach { (text, mode) ->
                    val isAdjusting = adjustingMode == mode
                    Button(
                        onClick = {
                            adjustingMode = mode
                            onModeChange(mode)
                        },
                        enabled = !isAdjusting && deviceStatus.isConnected,
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                    ) {
                        Text(
                            if (isAdjusting) "调节中..." else text,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 自定义模式按钮（如果有自定义模式）
            if (customPresets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    customPresets.take(2).forEach { preset ->
                        OutlinedButton(
                            onClick = { /* 这里后面会实现应用自定义模式 */ },
                            enabled = deviceStatus.isConnected,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) {
                            Text(preset.name, fontSize = 14.sp)
                        }
                    }
                    // 如果只有1个自定义模式，用占位按钮
                    if (customPresets.size == 1) {
                        OutlinedButton(
                            onClick = onNavigateToCustomPresets,
                            enabled = false,
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        ) {
                            Text("+ 添加", fontSize = 14.sp)
                        }
                    }
                }
            }

            // 手动调节入口
            Text(
                "手动调节",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onNavigateToHeight,
                    enabled = deviceStatus.isConnected,
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                ) {
                    Text("调节高度")
                }
                OutlinedButton(
                    onClick = onNavigateToAngle,
                    enabled = deviceStatus.isConnected,
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                ) {
                    Text("调节角度")
                }
            }

            // 新增：保存为自定义模式按钮
            Button(
                onClick = onSaveAsCustomPreset,
                enabled = deviceStatus.isConnected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = "保存",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存为自定义模式")
            }

            // 新增：用户反馈常驻按钮
            Button(
                onClick = onShowFeedback,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(
                    Icons.Default.Feedback,
                    contentDescription = "反馈",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("用户反馈")
            }
        }
    }
}

@Composable
fun ChairVisualization(chairState: ChairState) {
    Box(
        modifier = Modifier
            .size(150.dp)
            .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .align(Alignment.BottomCenter)
                .background(Color.Gray)
        )

        Box(
            modifier = Modifier
                .width(80.dp)
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    when (chairState.currentMode) {
                        ChairMode.OFFICE -> Color.Blue.copy(alpha = 0.7f)
                        ChairMode.REST -> Color.Green.copy(alpha = 0.7f)
                        ChairMode.ENTERTAINMENT -> Color.Yellow.copy(alpha = 0.7f)
                    },
                    RoundedCornerShape(4.dp)
                )
        )

        Text(
            text = when (chairState.currentMode) {
                ChairMode.OFFICE -> "办公模式"
                ChairMode.REST -> "休息模式"
                ChairMode.ENTERTAINMENT -> "娱乐模式"
            },
            modifier = Modifier.align(Alignment.TopCenter),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewHomeScreen() {
    HomeScreen(
        chairState = ChairState(),
        deviceStatus = DeviceStatus(),
        customPresets = listOf(
            CustomPreset("1", "午休小睡", 120f, 12f, 45f)
        ),
        onModeChange = {},
        onNavigateToHeight = {},
        onNavigateToAngle = {},
        onNavigateToCustomPresets = {},
        onShowFeedback = {},
        onSaveAsCustomPreset = {},
        onNavigateToReport = {},
        onNavigateToProfile = {},
        onToggleDeviceConnection = {}
    )
}