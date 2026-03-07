package com.example.spineassistant

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.spineassistant.models.FirmwareInfo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareUpdateScreen(
    firmwareInfo: FirmwareInfo,
    onUpdateFirmware: (Float) -> Unit,
    onBack: () -> Unit
) {
    var isUpdating by remember { mutableStateOf(false) }
    var updateCompleted by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(firmwareInfo.updateProgress) }

    // 模拟更新过程
    LaunchedEffect(isUpdating) {
        if (isUpdating) {
            for (i in 0..100) {
                currentProgress = i / 100f
                onUpdateFirmware(currentProgress)
                delay(50) // 模拟下载延迟
            }
            isUpdating = false
            updateCompleted = true
        }
    }

    // 更新完成对话框
    if (updateCompleted) {
        Dialog(onDismissRequest = { updateCompleted = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "更新成功",
                        tint = Color.Green,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "固件更新成功",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "新版本 ${firmwareInfo.latestVersion} 已安装",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { updateCompleted = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("固件更新") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            // 当前版本信息
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "当前版本信息",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("当前版本")
                        Text(
                            firmwareInfo.currentVersion,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("最新版本")
                        Text(
                            firmwareInfo.latestVersion,
                            fontWeight = FontWeight.Bold,
                            color = if (firmwareInfo.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (firmwareInfo.hasUpdate) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "有更新",
                                tint = Color(0xFFFF9800),  // 使用橙色十六进制值
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "发现新版本，建议更新",
                                color = Color(0xFFFF9800)  // 使用橙色十六进制值
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 更新进度显示
            if (isUpdating) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "更新进度",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LinearProgressIndicator(
                            progress = currentProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "${(currentProgress * 100).toInt()}%",
                            modifier = Modifier.align(Alignment.End)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "正在下载更新... 请勿关闭应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 操作按钮
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (firmwareInfo.hasUpdate && !isUpdating) {
                    Button(
                        onClick = { isUpdating = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("下载并安装更新")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { /* 模拟检查更新 */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("稍后提醒我")
                    }
                } else if (!firmwareInfo.hasUpdate) {
                    Button(
                        onClick = { /* 模拟检查更新 */ },
                        enabled = !isUpdating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text("检查更新")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "当前已是最新版本",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 更新说明
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "更新说明",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "• 优化座椅控制算法\n• 修复已知的连接问题\n• 提升电池管理效率\n• 新增自定义模式保存功能",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFirmwareUpdateScreen() {
    FirmwareUpdateScreen(
        firmwareInfo = FirmwareInfo(),
        onUpdateFirmware = {},
        onBack = {}
    )
}