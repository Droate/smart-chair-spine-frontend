package com.example.spineassistant.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spineassistant.models.FirmwareInfo
import com.example.spineassistant.network.MultiSceneRecResponse
import com.example.spineassistant.network.NetworkModule
import com.example.spineassistant.network.SingleModeRecResponse
import com.example.spineassistant.network.UserProfileRequest
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    autoPopupEnabled: Boolean,
    firmwareInfo: FirmwareInfo,
    currentAppMode: String,
    onAutoPopupToggle: (Boolean) -> Unit,
    onNavigateToFirmwareUpdate: () -> Unit,
    onApplyRecommendation: (List<SingleModeRecResponse>) -> Unit,
    // 🔥 新增回调：跳转到 AI 坐姿监测页面
    onNavigateToPostureMonitor: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToReport: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var heightInput by remember { mutableStateOf("175") }
    var weightInput by remember { mutableStateOf("70") }
    var isExpanded by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var recommendationResult by remember { mutableStateOf<MultiSceneRecResponse?>(null) }

    // 推荐结果弹窗 (保持不变)
    if (recommendationResult != null) {
        AlertDialog(
            onDismissRequest = { recommendationResult = null },
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("AI 全场景智能配置") },
            text = {
                Column {
                    Text("基于您的身体数据 (${heightInput}cm / ${weightInput}kg)，\nAI 为您规划了以下专属坐姿：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("模式", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                        Text("座高", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("角度", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    recommendationResult!!.recommendations.forEach { rec ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val modeName = when(rec.mode) {
                                "OFFICE" -> "办公"
                                "REST" -> "休息"
                                "ENTERTAINMENT" -> "娱乐"
                                else -> rec.mode
                            }
                            Text(modeName, modifier = Modifier.weight(1.2f))
                            Text("${rec.heightMm}mm", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                            Text("${rec.angleDeg}°", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("点击应用将自动更新您的快捷模式预设。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onApplyRecommendation(recommendationResult!!.recommendations)
                        recommendationResult = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("一键全部应用") }
            },
            dismissButton = {
                TextButton(onClick = { recommendationResult = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.fillMaxWidth()) {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, "首页") }, label = { Text("首页") }, selected = false, onClick = onNavigateToHome)
                NavigationBarItem(icon = { Icon(Icons.Default.BarChart, "报告") }, label = { Text("报告") }, selected = false, onClick = onNavigateToReport)
                NavigationBarItem(icon = { Icon(Icons.Default.Person, "我的") }, label = { Text("我的") }, selected = true, onClick = { })
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 1. 身体档案卡片 (保持不变)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    onClick = { isExpanded = !isExpanded }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(if (isExpanded) "编辑身体档案" else "我的身体档案", style = MaterialTheme.typography.titleLarge)
                                Text(if (isExpanded) "完善数据以获得 AI 推荐" else "点击展开以编辑", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                        }
                        if (isExpanded) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                OutlinedTextField(value = heightInput, onValueChange = { heightInput = it.filter { c -> c.isDigit() } }, label = { Text("身高 (cm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = weightInput, onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("体重 (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val h = heightInput.toIntOrNull()
                                    val w = weightInput.toFloatOrNull()
                                    if (h != null && w != null) {
                                        isSyncing = true
                                        scope.launch {
                                            try {
                                                // user_id 可以不传，后端会从 Token 解析
                                                val request = UserProfileRequest(userId = "", heightCm = h, weightKg = w, currentMode = currentAppMode)
                                                // 使用 getApiService()
                                                val response = NetworkModule.getApiService().getRecommendation(request)
                                                if (response.isSuccessful && response.body() != null) {
                                                    recommendationResult = response.body()
                                                } else {
                                                    // 401 错误会在这里捕获
                                                    val msg = if(response.code() == 401) "认证过期，请重新登录" else "获取失败: ${response.code()}"
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    if(response.code() == 401) onLogout() // 自动触发登出
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_LONG).show()
                                                e.printStackTrace()
                                            } finally {
                                                isSyncing = false
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "请输入有效的数值", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isSyncing
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("云端计算中...")
                                } else {
                                    Icon(Icons.Default.CloudSync, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("上传并获取智能推荐")
                                }
                            }
                        }
                    }
                }
            }

            // 2. 其他功能列表
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("自动弹窗", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = autoPopupEnabled, onCheckedChange = onAutoPopupToggle)
                    }
                }
            }

            // 🔥 新增：功能列表添加 "AI 坐姿监测"
            // 注意这里使用的是 listOf 遍历生成，我们要在 list 里加一项
            listOf(
                "AI 坐姿监测 (Beta)" to Icons.Default.CameraFront, // 🔥 新增项
                "使用帮助" to Icons.Default.Help,
                "意见反馈" to Icons.Default.Feedback,
                "关于APP" to Icons.Default.Info,
                "固件更新" to Icons.Default.SystemUpdate,
                "设置" to Icons.Default.Settings
            ).forEach { (title, icon) ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = {
                            if (title == "固件更新") onNavigateToFirmwareUpdate()
                            if (title == "AI 坐姿监测 (Beta)") onNavigateToPostureMonitor() // 🔥 触发回调
                        }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            if (title == "固件更新" && firmwareInfo.hasUpdate) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(containerColor = Color(0xFFFF9800)) { Text("新", fontSize = 10.sp) }
                            }
                            if (title.contains("Beta")) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("Labs", fontSize = 10.sp, color = Color.White) }
                            }
                        }
                    }
                }
            }

            // 3. 退出登录按钮
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("退出登录")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
