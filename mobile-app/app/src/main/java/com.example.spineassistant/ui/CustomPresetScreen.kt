package com.example.spineassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.spineassistant.models.CustomPreset
import com.spineassistant.models.ChairState // 导入 shared 中的 ChairState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPresetScreen(
    customPresets: List<CustomPreset>,
    currentChairState: ChairState, // 👈 新增：传入当前椅子状态
    onAddPreset: (CustomPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onApplyPreset: (CustomPreset) -> Unit,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf(TextFieldValue()) }
    var presetToDelete by remember { mutableStateOf<String?>(null) }
    var presetToApply by remember { mutableStateOf<CustomPreset?>(null) }

    // 添加预设对话框
    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "保存当前状态为模式",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 显示即将保存的数据预览
                    Text(
                        "将保存: 高度 ${currentChairState.height}mm, 角度 ${currentChairState.angle}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("模式名称 (如: 专注阅读)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (presetName.text.isNotBlank()) {
                                    // 👈 逻辑优化：使用 currentChairState 的真实数据
                                    val newPreset = CustomPreset(
                                        id = System.currentTimeMillis().toString(),
                                        name = presetName.text,
                                        backAngle = currentChairState.angle.toFloat(),
                                        seatAngle = 0f, // 暂时默认为0，如果shared有对应字段请修改
                                        height = currentChairState.height.toFloat()
                                    )
                                    onAddPreset(newPreset)
                                    presetName = TextFieldValue()
                                    showAddDialog = false
                                }
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    // ... 删除确认对话框 (代码保持不变) ...
    presetToDelete?.let { presetId ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("删除模式") },
            text = { Text("确定要删除这个自定义模式吗？") },
            confirmButton = {
                Button(onClick = { onDeletePreset(presetId); presetToDelete = null }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) { Text("取消") }
            }
        )
    }

    // ... 应用确认对话框 (代码保持不变) ...
    presetToApply?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToApply = null },
            title = { Text("应用模式") },
            text = { Text("确定要应用「${preset.name}」模式吗？") },
            confirmButton = {
                Button(onClick = { onApplyPreset(preset); presetToApply = null }) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToApply = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义模式") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加模式")
            }
        }
    ) { innerPadding ->
        // ... 列表显示逻辑 (基本保持不变，为了节省篇幅略去未改动部分，直接用你原有的即可) ...
        if (customPresets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("暂无自定义模式", style = MaterialTheme.typography.titleMedium)
                Text("调整好座椅后，点击 + 号保存当前状态", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                items(customPresets) { preset ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = { presetToApply = preset }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.PlayArrow, "应用", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { presetToDelete = preset.id }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("椅背: ${preset.backAngle.toInt()}°") // 转为 Int 显示更整洁
                                Text("高度: ${preset.height.toInt()}mm")
                            }
                        }
                    }
                }
            }
        }
    }
}
