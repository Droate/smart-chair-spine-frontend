package com.example.spineassistant.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScanScreen(
    scannedDevices: List<BluetoothDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onNavigateToQrScan: () -> Unit, // 🔥 新增参数
    onBack: () -> Unit
) {
    // 页面进入时开始扫描，退出时停止
    DisposableEffect(Unit) {
        onStartScan()
        onDispose { onStopScan() }
    }

    // 扫描动画状态
    val infiniteTransition = rememberInfiniteTransition(label = "Radar")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Scale"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                // 🔥 新增：右上角扫码按钮
                actions = {
                    IconButton(onClick = onNavigateToQrScan) {
                        Icon(Icons.Default.QrCodeScanner, "扫码连接", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部雷达扫描区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                // 绘制雷达波纹
                Canvas(modifier = Modifier.size(50.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = radarAlpha),
                        radius = size.width / 2 * radarScale,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
                Icon(
                    Icons.Default.BluetoothSearching,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 设备列表
            if (scannedDevices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在搜索附近的设备...", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // 分组：已配对
                    val paired = scannedDevices.filter { it.bondState == BluetoothDevice.BOND_BONDED }
                    if (paired.isNotEmpty()) {
                        item {
                            SectionHeader("已配对设备")
                        }
                        items(paired) { device ->
                            DeviceItem(device, onConnectDevice)
                        }
                    }

                    // 分组：可用设备
                    val available = scannedDevices.filter { it.bondState != BluetoothDevice.BOND_BONDED }
                    if (available.isNotEmpty()) {
                        item {
                            SectionHeader("可用设备")
                        }
                        items(available) { device ->
                            DeviceItem(device, onConnectDevice)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onConnect: (BluetoothDevice) -> Unit) {
    ListItem(
        headlineContent = { Text(device.name ?: "未知设备", fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(device.address, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(Icons.Default.Bluetooth, null, tint = Color.Gray)
        },
        trailingContent = {
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                TextButton(onClick = { onConnect(device) }) {
                    Text("连接")
                }
            } else {
                IconButton(onClick = { onConnect(device) }) {
                    Icon(Icons.Default.Link, "连接")
                }
            }
        },
        modifier = Modifier.clickable { onConnect(device) }
    )
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
