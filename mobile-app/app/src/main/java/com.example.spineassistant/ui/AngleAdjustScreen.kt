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
import com.spineassistant.models.ChairState
import kotlin.math.roundToInt
import com.example.spineassistant.ui.PremiumChairVisualization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AngleAdjustScreen(
    chairState: ChairState,
    isCushionFollow: Boolean,
    onAngleChangeImmediate: (Float) -> Unit, // 同样，保留但设为“哑”回调
    onFinalAngleSet: (Float) -> Unit,
    onCushionFollowToggle: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var tempAngle by remember { mutableStateOf(chairState.angle.toFloat()) }

    val previewState = remember(tempAngle, chairState) {
        chairState.copy(angle = tempAngle.toInt())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调节角度") },
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("椅背角度", style = MaterialTheme.typography.titleMedium)
                    Text("目标角度: ${tempAngle.roundToInt()}°", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = tempAngle,
                        // 🔥 1. 只更新本地状态
                        onValueChange = { tempAngle = it },
                        // 🔥 2. 拖动结束时不做任何事
                        onValueChangeFinished = { /* NO-OP */ },
                        valueRange = 90f..135f
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("90°"); Text("135°")
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("坐垫联动")
                Switch(checked = isCushionFollow, onCheckedChange = onCushionFollowToggle)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        // 🔥 3. 只有点击"完成"时，才触发最终确认
                        onFinalAngleSet(tempAngle)
                        onCancel()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("完成") }
            }
        }
    }
}
