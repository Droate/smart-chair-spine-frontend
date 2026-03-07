package com.example.spineassistant

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPresetScreen(
    customPresets: List<CustomPreset>,
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
                        "保存自定义模式",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("模式名称") },
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
                                    val newPreset = CustomPreset(
                                        id = System.currentTimeMillis().toString(),
                                        name = presetName.text,
                                        backAngle = 105f,
                                        seatAngle = 8f,
                                        height = 50f
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

    // 删除确认对话框
    presetToDelete?.let { presetId ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("删除模式") },
            text = { Text("确定要删除这个自定义模式吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(presetId)
                        presetToDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 应用确认对话框
    presetToApply?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToApply = null },
            title = { Text("应用模式") },
            text = { Text("确定要应用「${preset.name}」模式吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyPreset(preset)
                        presetToApply = null
                    }
                ) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToApply = null }) {
                    Text("取消")
                }
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
        if (customPresets.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "暂无自定义模式",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "点击右下角 + 按钮创建新模式",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                items(customPresets) { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    preset.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row {
                                    IconButton(
                                        onClick = { presetToApply = preset },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "应用模式",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { presetToDelete = preset.id },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除模式",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("椅背: ${preset.backAngle}°")
                                Text("坐垫: ${preset.seatAngle}°")
                                Text("高度: ${preset.height}cm")
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewCustomPresetScreen() {
    val samplePresets = listOf(
        CustomPreset("1", "午休小睡", 120f, 12f, 45f),
        CustomPreset("2", "观影模式", 110f, 10f, 48f),
        CustomPreset("3", "阅读姿势", 100f, 5f, 50f)
    )
    CustomPresetScreen(
        customPresets = samplePresets,
        onAddPreset = {},
        onDeletePreset = {},
        onApplyPreset = {},
        onBack = {}
    )
}