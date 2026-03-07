package com.example.spineassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spineassistant.models.CustomPreset // 🔥 1. 导入 CustomPreset
import com.spineassistant.models.ChairState

@Composable
fun ChairStatusDashboard(
    chairState: ChairState,
    isConnected: Boolean,

    // 🔥 2. 参数变更：使用统一的 Preset 对象
    activePreset: CustomPreset?,

    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Settings else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "设备就绪" else "设备未连接",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Gray
                    )
                }

                // 🔥 3. 核心逻辑重写：根据传入的 Preset 对象显示标签
                if (isConnected) {
                    val (modeText, badgeColor) = if (activePreset != null) {
                        // 如果有激活的预设
                        when (activePreset.id) {
                            "office", "rest", "entertainment" -> activePreset.name to MaterialTheme.colorScheme.primary
                            else -> activePreset.name to MaterialTheme.colorScheme.tertiary // 自定义模式用特殊颜色
                        }
                    } else {
                        // 如果没有任何预设被激活
                        "默认模式" to Color.Gray
                    }

                    Surface(
                        color = badgeColor,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = modeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 核心指标栏 (保持不变)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusItem(
                    label = "高度",
                    value = if (isConnected) "${chairState.height}mm" else "--",
                    active = isConnected
                )
                StatusDivider()
                StatusItem(
                    label = "椅背",
                    value = if (isConnected) "${chairState.angle}°" else "--",
                    active = isConnected
                )
                StatusDivider()
                StatusItem(
                    label = "入座",
                    value = if (isConnected) (if (chairState.isOccupied) "有人" else "空闲") else "--",
                    valueColor = if (isConnected && chairState.isOccupied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                    active = isConnected
                )
            }
        }
    }
}

// ... StatusItem 和 StatusDivider 保持不变 ...
@Composable
private fun StatusItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    active: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = if (active) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (active) valueColor else Color.Gray)
    }
}

@Composable
private fun StatusDivider() {
    Box(modifier = Modifier.height(30.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
