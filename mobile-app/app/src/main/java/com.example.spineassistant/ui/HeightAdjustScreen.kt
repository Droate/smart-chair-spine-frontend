package com.example.spineassistant

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.spineassistant.models.ChairState
import com.example.spineassistant.ui.PremiumChairVisualization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeightAdjustScreen(
    chairState: ChairState,
    isAutoAdjust: Boolean,
    onHeightChangeImmediate: (Float) -> Unit,
    onFinalHeightSet: (Float) -> Unit,
    onAutoAdjustToggle: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    // 🔥 修复: 确保初始值落在新的合法范围内 (350-600)，防止 Slider 崩溃
    var tempHeight by remember {
        mutableStateOf(chairState.height.toFloat().coerceIn(350f, 600f))
    }

    val previewState = remember(tempHeight, chairState) {
        chairState.copy(height = tempHeight.toInt())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调节高度") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {

            Card(
                modifier = Modifier.fillMaxWidth().height(280.dp).padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F7))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PremiumChairVisualization(chairState = previewState)
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("座椅高度", style = MaterialTheme.typography.titleMedium)
                    Text("目标高度: ${tempHeight.roundToInt()} mm", style = MaterialTheme.typography.bodyLarge)

                    Slider(
                        value = tempHeight,
                        onValueChange = { tempHeight = it },
                        onValueChangeFinished = { /* NO-OP */ },
                        // 🔥 核心修改: 调整为标准办公椅范围 350-600mm
                        valueRange = 350f..600f,
                        enabled = !isAutoAdjust
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // 🔥 核心修改: 更新标签
                        Text("350mm"); Text("600mm")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("智能自适应")
                Switch(checked = isAutoAdjust, onCheckedChange = onAutoAdjustToggle)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        onFinalHeightSet(tempHeight)
                        onCancel()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("完成") }
            }
        }
    }
}
