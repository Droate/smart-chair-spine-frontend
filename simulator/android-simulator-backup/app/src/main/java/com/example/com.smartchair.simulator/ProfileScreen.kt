package com.example.spineassistant
import androidx.compose.foundation.background  // 添加这行！
import androidx.compose.ui.unit.sp            // 添加这行！
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    autoPopupEnabled: Boolean,
    firmwareInfo: FirmwareInfo,
    onAutoPopupToggle: (Boolean) -> Unit,
    onNavigateToFirmwareUpdate: () -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToReport: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("我的", style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
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
                    selected = false,
                    onClick = onNavigateToHome
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
                    selected = true,
                    onClick = { }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "用户头像",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                "访客",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "点击登录以同步数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 核心功能开关
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("自动弹窗", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = autoPopupEnabled,
                            onCheckedChange = {
                                onAutoPopupToggle(it)
                                println("自动弹窗开关: $it")
                            }
                        )
                    }
                }
            }

            // 功能列表
            listOf(
                "使用帮助" to Icons.Default.Help,
                "意见反馈" to Icons.Default.Feedback,
                "关于APP" to Icons.Default.Info,
                "固件更新" to Icons.Default.SystemUpdate,  // 新增固件更新入口
                "设置" to Icons.Default.Settings
            ).forEach { (title, icon) ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = {
                            println("点击了: $title")
                            if (title == "固件更新") {
                                onNavigateToFirmwareUpdate()
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = title, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(title, style = MaterialTheme.typography.bodyLarge)

                            // 固件更新有提示
                            if (title == "固件更新" && firmwareInfo.hasUpdate) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = Color(0xFFFF9800)
                                ) {
                                    Text("新", fontSize = 10.sp)
                                }
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
fun PreviewProfileScreen() {
    ProfileScreen(
        autoPopupEnabled = true,
        firmwareInfo = FirmwareInfo(),
        onAutoPopupToggle = {},
        onNavigateToFirmwareUpdate = {},
        onBack = {},
        onNavigateToHome = {},
        onNavigateToReport = {}
    )
}