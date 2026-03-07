package com.example.spineassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spineassistant.viewmodel.ReportViewModel // 🔥 必须导入

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToHealthScoreDetail: () -> Unit,
    viewModel: ReportViewModel = viewModel()
) {
    val healthData by viewModel.healthData.collectAsState()
    val healthScoreDetail by viewModel.healthScoreDetail.collectAsState()
    val aiAdvice by viewModel.aiAdvice.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康数据中心", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.fillMaxWidth()) {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, "首页") }, label = { Text("首页") }, selected = false, onClick = onNavigateToHome)
                NavigationBarItem(icon = { Icon(Icons.Default.BarChart, "报告") }, label = { Text("报告") }, selected = true, onClick = { })
                NavigationBarItem(icon = { Icon(Icons.Default.Person, "我的") }, label = { Text("我的") }, selected = false, onClick = onNavigateToProfile)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard("总时长", healthData.totalSittingTime, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        modifier = Modifier.weight(1f).clickable { onNavigateToHealthScoreDetail() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp).fillMaxWidth()
                        ) {
                            Text("健康分", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${healthScoreDetail.totalScore}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    StatCard("久坐", "${healthData.sedentaryReminders}次", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📅 7天坐姿时长趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val maxVal = healthData.weeklyData.maxOrNull() ?: 1f
                            healthData.weeklyData.forEachIndexed { index, value ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val barHeight = if (maxVal > 0) (value / maxVal) else 0f
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .fillMaxHeight(barHeight.coerceAtLeast(0.05f))
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(if (index == 6) MaterialTheme.colorScheme.primary else Color.LightGray)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (index == 6) "今" else "${index + 1}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📊 今日模式分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (healthData.modeDistribution.isEmpty()) {
                            Text("暂无数据", color = Color.Gray)
                        } else {
                            healthData.modeDistribution.forEach { (mode, pct) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val color = when(mode) {
                                        "办公模式" -> Color.Blue
                                        "休息模式" -> Color.Green
                                        "娱乐模式" -> Color(0xFFFF9800)
                                        else -> Color.Gray
                                    }
                                    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(mode, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                    Text("${(pct * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                LinearProgressIndicator(
                                    progress = pct,
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = when(mode) {
                                        "办公模式" -> Color.Blue
                                        "休息模式" -> Color.Green
                                        "娱乐模式" -> Color(0xFFFF9800)
                                        else -> Color.Gray
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (aiAdvice != null) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (aiAdvice != null) "✨ AI 专家深度点评" else "💡 基础健康建议",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = aiAdvice ?: healthData.healthAdvice,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (aiAdvice == null) {
                            Button(
                                onClick = { viewModel.fetchAIAnalysis() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isAiLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isAiLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI 正在思考中...")
                                } else {
                                    Icon(Icons.Default.AutoAwesome, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("获取 AI 深度分析")
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.fetchAIAnalysis() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("重新分析")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
