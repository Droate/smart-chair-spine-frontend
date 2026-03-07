package com.example.spineassistant

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AngleAdjustScreen(
    chairState: ChairState,
    onBackAngleChange: (Float) -> Unit,
    onSeatAngleChange: (Float) -> Unit,
    onCushionFollowToggle: (Boolean) -> Unit,
    onModeChange: (ChairMode) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "调节角度",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 快捷模式
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "快捷模式",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            "办公" to ChairMode.OFFICE,
                            "休息" to ChairMode.REST,
                            "娱乐" to ChairMode.ENTERTAINMENT
                        ).forEach { (text, mode) ->
                            FilterChip(
                                selected = chairState.currentMode == mode,
                                onClick = { onModeChange(mode) },
                                label = { Text(text) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 角度微调
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "角度微调",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 椅背角度
                    Text(
                        "椅背角度: ${chairState.backAngle.toInt()}°",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = chairState.backAngle,
                        onValueChange = { newAngle ->
                            onBackAngleChange(newAngle)
                            // 如果坐垫随动开启，自动计算坐垫角度
                            if (chairState.isCushionFollow) {
                                val newSeatAngle = newAngle * 0.1f
                                onSeatAngleChange(newSeatAngle.coerceIn(0f, 15f))
                            }
                        },
                        valueRange = 90f..135f,
                        steps = 44,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("90°")
                        Text("135°")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 坐垫角度
                    Text(
                        "坐垫角度: ${chairState.seatAngle.toInt()}°",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = chairState.seatAngle,
                        onValueChange = onSeatAngleChange,
                        valueRange = 0f..15f,
                        steps = 14,
                        enabled = !chairState.isCushionFollow,  // 随动开启时禁用
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0°")
                        Text("15°")
                    }
                }
            }

            // 新增：坐垫随动开关
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "坐垫随动设置",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "坐垫随动",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "开启后坐垫角度随椅背自动调整",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = chairState.isCushionFollow,
                            onCheckedChange = onCushionFollowToggle
                        )
                    }

                    // 显示随动公式
                    if (chairState.isCushionFollow) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "随动公式：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "坐垫角度 = 椅背角度 × 0.1",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewAngleAdjustScreen() {
    AngleAdjustScreen(
        chairState = ChairState(),
        onBackAngleChange = {},
        onSeatAngleChange = {},
        onCushionFollowToggle = {},
        onModeChange = {},
        onSave = {},
        onCancel = {}
    )
}