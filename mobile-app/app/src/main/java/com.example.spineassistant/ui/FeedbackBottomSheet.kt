package com.example.spineassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

// 定义反馈流程的三个阶段
enum class FeedbackPhase {
    INITIAL,    // 初始阶段：选择问题
    ADJUSTING,  // 调节中：等待用户验证
    SUCCESS     // 成功：保存为新模式
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(
    onDismiss: () -> Unit,
    // 阶段1: 应用初次调节
    onApplySmartAdjust: (problemArea: String, intensity: Float) -> Unit,
    // 阶段2: 用户觉得不够，请求再次微调
    onRequestMoreAdjust: () -> Unit,
    // 阶段3: 用户确认舒适，保存为新模式
    onSaveNewPreset: (name: String) -> Unit
) {
    // 内部状态管理
    var currentPhase by remember { mutableStateOf(FeedbackPhase.INITIAL) }

    // 记录用户的初始选择，用于后续的多轮调节
    var selectedArea by remember { mutableStateOf("腰部支撑") }
    var discomfortLevel by remember { mutableStateOf(3f) }

    // 保存模式时的输入框
    var newPresetName by remember { mutableStateOf(TextFieldValue()) }

    val problemAreas = listOf("腰部支撑", "坐垫高度", "椅背角度", "久坐疲劳")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {

            // --- 动态内容区域 ---
            when (currentPhase) {

                // === 阶段 1: 初始选择 ===
                FeedbackPhase.INITIAL -> {
                    Header(icon = Icons.Default.AutoFixHigh, title = "坐姿智能微调")
                    Text("系统将根据反馈微调座椅，直到您感觉舒适。", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("哪里感觉不舒服？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Column {
                        problemAreas.chunked(2).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowItems.forEach { area ->
                                    val isSelected = selectedArea == area
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedArea = area },
                                        label = { Text(area) },
                                        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null) } } else null,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("不适程度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when(discomfortLevel.toInt()) {
                                1 -> "轻微"
                                2 -> "有点"
                                3 -> "明显"
                                4 -> "难受"
                                else -> "严重"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(value = discomfortLevel, onValueChange = { discomfortLevel = it }, valueRange = 1f..5f, steps = 3)

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            onApplySmartAdjust(selectedArea, discomfortLevel)
                            currentPhase = FeedbackPhase.ADJUSTING // 进入下一阶段
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("生成并执行方案")
                    }
                }

                // === 阶段 2: 验证与微调 ===
                FeedbackPhase.ADJUSTING -> {
                    Header(icon = Icons.Default.Tune, title = "正在为您优化...")
                    Text("座椅已进行微调，请感受一下当前的状态。", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(32.dp))

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("针对「$selectedArea」的调节已生效", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Text("现在的感觉如何？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("先这样吧") }

                        OutlinedButton(
                            onClick = { onRequestMoreAdjust() }, // 再次调节，保持在当前界面
                            modifier = Modifier.weight(1f)
                        ) { Text("还不够") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { currentPhase = FeedbackPhase.SUCCESS }, // 成功，进入保存阶段
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.ThumbUp, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("舒服多了！")
                    }
                }

                // === 阶段 3: 成功与保存 ===
                FeedbackPhase.SUCCESS -> {
                    Header(icon = Icons.Default.SentimentSatisfiedAlt, title = "太好了！")
                    Text("我们找到了适合您的舒适姿势。建议保存为新模式，方便日后一键调用。", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("为新模式起个名字") },
                        placeholder = { Text("例如：我的护腰模式") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            val finalName = if (newPresetName.text.isBlank()) "智能优化模式" else newPresetName.text
                            onSaveNewPreset(finalName)
                            onDismiss() // 完成流程
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存并应用")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("仅本次应用，不保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(8.dp))
}
