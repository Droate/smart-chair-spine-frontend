package com.example.spineassistant.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.spineassistant.data.AppDatabase
import com.example.spineassistant.data.SitSessionEntity
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.spineassistant.models.ChairState
import com.spineassistant.protocol.ControlCommand
import com.spineassistant.protocol.StateUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow

/**
 * 蓝牙客户端管理器，负责：
 * - 扫描附近的蓝牙设备
 * - 连接/断开座椅设备
 * - 发送控制指令（调节高度、角度、应用预设等）
 * - 接收座椅状态更新并更新本地状态流
 * - 自动记录入座会话（开始时间、结束时间、平均角度、姿势变化次数等）
 * - 支持断线自动重连（指数退避策略）
 */
class BluetoothClientManager(private val context: Context) {

    // 标准 SPP UUID，用于与蓝牙串口设备通信
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val TAG = "BTClient-v1.5"

    // 获取系统蓝牙适配器
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // 连接线程和已连接线程的引用
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    // 上一次成功连接的设备（用于自动重连）
    private var lastConnectedDevice: BluetoothDevice? = null

    // 是否为用户主动断开（若是，则不自动重连）
    private var isUserDisconnect = false

    // 自动重连任务（协程 Job）
    private var reconnectJob: Job? = null

    // Jackson ObjectMapper，用于 JSON 序列化/反序列化
    private val mapper = jacksonObjectMapper().apply {
        configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // 本地数据库实例
    private val database = AppDatabase.getDatabase(context)
    private val sitSessionDao = database.sitSessionDao()

    // 连接状态流（对外只读）
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    // 当前椅子状态流（对外只读）
    private val _chairStateFlow = MutableStateFlow(ChairState.default("client-default"))
    val chairStateFlow = _chairStateFlow.asStateFlow()

    // 扫描到的设备列表流（对外只读）
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    // 会话统计变量（用于计算平均角度、姿势变化次数）
    private var sessionAngleSum: Long = 0          // 当前会话角度总和
    private var sessionSampleCount: Int = 0        // 当前会话角度采样次数
    private var lastStableAngle: Int = -1          // 上一次稳定的角度（用于检测变化）
    private var sessionPostureChanges: Int = 0     // 当前会话中姿势变化次数（角度变化 >5°）
    private var _currentMode: String = "OFFICE"    // 当前选择的模式（用于记录会话模式）

    /**
     * 连接状态枚举
     */
    enum class ConnectionState {
        Disconnected,   // 未连接
        Scanning,       // 正在扫描设备
        Connecting,     // 正在连接
        Connected       // 已连接
    }

    /**
     * 蓝牙扫描广播接收器
     * 监听 ACTION_FOUND 和 ACTION_DISCOVERY_FINISHED
     */
    private val scanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // 发现新设备
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val currentList = _scannedDevices.value
                        // 避免重复添加相同地址的设备
                        if (currentList.none { d -> d.address == it.address }) {
                            val newList = currentList + it
                            _scannedDevices.value = newList
                        }
                    }
                }
                // 扫描结束
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "扫描周期结束")
                    // 如果当前状态还是 Scanning，则置为 Disconnected（表示没有连接上任何设备）
                    if (_connectionState.value == ConnectionState.Scanning) {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                }
            }
        }
    }

    /**
     * 设置当前模式（用于记录会话属于哪个模式）
     */
    fun setCurrentMode(modeName: String) {
        _currentMode = modeName
        Log.d(TAG, "当前模式已更新为: $_currentMode")
    }

    /**
     * 更新座椅高度
     * 1. 更新本地状态流
     * 2. 发送控制指令给设备
     */
    fun updateHeight(newHeight: Int) {
        _chairStateFlow.value = _chairStateFlow.value.copy(height = newHeight)
        val cmd = ControlCommand(deviceId = "app", command = ControlCommand.CommandType.ADJUST_HEIGHT, parameters = mapOf("height" to newHeight))
        sendCommand(cmd)
    }

    /**
     * 更新座椅角度
     */
    fun updateAngle(newAngle: Int) {
        _chairStateFlow.value = _chairStateFlow.value.copy(angle = newAngle)
        val cmd = ControlCommand(deviceId = "app", command = ControlCommand.CommandType.ADJUST_ANGLE, parameters = mapOf("angle" to newAngle))
        sendCommand(cmd)
    }

    /**
     * 应用预设（如办公模式、休息模式）
     * 同时设置当前模式
     */
    fun applyPreset(presetName: String) {
        setCurrentMode(presetName)
        val cmd = ControlCommand(deviceId = "app", command = ControlCommand.CommandType.APPLY_PRESET, parameters = mapOf("presetName" to presetName))
        sendCommand(cmd)
    }

    /**
     * 开始扫描附近的蓝牙设备
     * - 注册广播接收器
     * - 更新状态为 Scanning
     * - 先添加已配对设备到列表
     * - 启动发现（discovery）
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(scanReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败: ${e.message}")
        }
        _connectionState.value = ConnectionState.Scanning
        val pairedDevices = bluetoothAdapter.bondedDevices.toList()
        _scannedDevices.value = pairedDevices  // 先显示已配对设备

        // 如果已经在扫描，先取消
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        bluetoothAdapter.startDiscovery()
        Log.d(TAG, "开始全网扫描...")
    }

    /**
     * 停止扫描
     * - 取消发现
     * - 注销广播接收器
     * - 如果状态还是 Scanning，设为 Disconnected
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter.cancelDiscovery()
        }
        try {
            context.unregisterReceiver(scanReceiver)
        } catch (e: Exception) { }

        if (_connectionState.value == ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * 连接指定的蓝牙设备
     * - 停止扫描
     * - 取消正在进行的重连任务
     * - 记录设备用于后续重连
     * - 启动 ConnectThread
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        stopScan()
        reconnectJob?.cancel()
        isUserDisconnect = false
        lastConnectedDevice = device
        Log.d(TAG, "准备连接到: ${device.name} (${device.address})")

        _connectionState.value = ConnectionState.Connecting
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    /**
     * 通过 MAC 地址直接连接（用于扫码直连）
     */
    @SuppressLint("MissingPermission")
    fun connectByMacAddress(mac: String) {
        // 检查蓝牙地址是否有效
        if (bluetoothAdapter == null || !BluetoothAdapter.checkBluetoothAddress(mac)) {
            Log.e(TAG, "无效的蓝牙地址: $mac")
            return
        }
        try {
            val device = bluetoothAdapter.getRemoteDevice(mac)
            Log.i(TAG, "扫码直连: 获取到设备对象 $mac，发起连接...")
            connectToDevice(device)
        } catch (e: Exception) {
            Log.e(TAG, "获取设备失败: ${e.message}")
        }
    }

    /**
     * 尝试自动重连（指数退避）
     * 当连接意外断开时调用，最多尝试5次，等待时间依次为 1秒, 2秒, 4秒, 8秒, 16秒
     */
    private fun attemptReconnect() {
        val device = lastConnectedDevice ?: return
        if (isUserDisconnect) return  // 用户主动断开，不重连

        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            Log.w(TAG, "检测到意外断开，准备自动重连...")
            _connectionState.value = ConnectionState.Connecting

            var attempt = 1
            val maxAttempts = 5
            while (attempt <= maxAttempts && !isUserDisconnect) {
                // 指数退避：2^(attempt-1) * 1000 ms
                val delayMs = (2.0.pow(attempt - 1) * 1000).toLong()
                Log.d(TAG, "⏳ 重连尝试 $attempt/$maxAttempts: 等待 ${delayMs}ms...")
                delay(delayMs)

                if (isUserDisconnect) break

                try {
                    Log.i(TAG, "🔄 发起重连...")
                    connectToDevice(device)  // 重新调用连接方法
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "重连发起失败: ${e.message}")
                }
                attempt++
            }
        }
    }

    /**
     * 发送控制命令给设备
     * 仅在连接状态下有效，在 IO 线程中执行序列化和发送
     */
    fun sendCommand(command: ControlCommand) {
        if (_connectionState.value != ConnectionState.Connected) {
            Log.w(TAG, "⚠️ 尝试发送指令，但未连接")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = mapper.writeValueAsString(command)
                val finalData = json + "\n"  // 添加换行符作为消息分隔符
                Log.d(TAG, ">>> 准备发送: $json")
                connectedThread?.write(finalData.toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "❌ 序列化或发送过程异常: ${e.message}")
            }
        }
    }

    /**
     * 主动断开连接
     * 标记 isUserDisconnect = true，阻止自动重连
     */
    fun disconnect() {
        Log.d(TAG, "主动断开连接")
        isUserDisconnect = true
        reconnectJob?.cancel()
        connectThread?.cancel()
        connectedThread?.cancel()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * 连接线程：负责建立蓝牙 Socket 连接
     */
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        // 懒加载创建蓝牙 Socket，使用不安全连接（不配对也可连接）
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            // 取消正在进行的发现（提高连接成功率）
            bluetoothAdapter?.cancelDiscovery()
            try {
                Log.d(TAG, "开始 Socket 连接...")
                mmSocket?.connect()
                Log.d(TAG, "Socket 连接成功！")
            } catch (connectException: IOException) {
                Log.e(TAG, "❌ Socket 连接失败: ${connectException.message}")
                try { mmSocket?.close() } catch (closeException: IOException) { }
                _connectionState.value = ConnectionState.Disconnected
                if (!isUserDisconnect) {
                    attemptReconnect()  // 连接失败时尝试重连
                }
                return
            }
            mmSocket?.also { manageMyConnectedSocket(it) }
        }

        fun cancel() {
            try { mmSocket?.close() } catch (e: IOException) { }
        }
    }

    /**
     * 管理已连接的 Socket：创建 ConnectedThread 并更新状态
     */
    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        Log.d(TAG, "管理已连接的 Socket，启动 IO 线程")
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
        _connectionState.value = ConnectionState.Connected

        reconnectJob?.cancel()  // 连接成功，取消重连任务

        // 连接后立即请求一次设备状态
        CoroutineScope(Dispatchers.IO).launch {
            delay(500)
            sendCommand(ControlCommand(deviceId = "app", command = ControlCommand.CommandType.REQUEST_STATE))
        }
    }

    /**
     * 处理收到的 JSON 消息（由 ConnectedThread 调用）
     * 目前只处理 STATE_UPDATE 类型消息
     */
    private fun handleIncomingMessage(json: String) {
        try {
            if (json.contains("STATE_UPDATE")) {
                val update = mapper.readValue<StateUpdate>(json)
                val newState = update.chairState
                val oldState = _chairStateFlow.value

                // 更新本地椅子状态流
                _chairStateFlow.value = newState

                // 如果有人入座，处理实时数据（角度累加、姿势变化检测）
                if (newState.isOccupied) {
                    processRealTimeData(newState)
                }

                // 异步处理入座/离座变化（用于会话记录）
                CoroutineScope(Dispatchers.IO).launch {
                    processOccupancyChange(oldState.isOccupied, newState.isOccupied)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析消息失败: ${e.message}")
        }
    }

    /**
     * 处理实时数据：累加角度、检测姿势变化（角度变化超过5°视为一次姿势变化）
     */
    private fun processRealTimeData(state: ChairState) {
        sessionAngleSum += state.angle
        sessionSampleCount++

        if (lastStableAngle == -1) {
            lastStableAngle = state.angle
        } else {
            if (abs(state.angle - lastStableAngle) > 5) {
                sessionPostureChanges++
                lastStableAngle = state.angle
            }
        }
    }

    /**
     * 处理入座/离座变化，用于开始/结束会话记录
     */
    private suspend fun processOccupancyChange(wasOccupied: Boolean, isOccupied: Boolean) {
        // 无人 -> 有人：开始新会话
        if (!wasOccupied && isOccupied) {
            // 重置会话统计变量
            sessionAngleSum = 0
            sessionSampleCount = 0
            lastStableAngle = -1
            sessionPostureChanges = 0

            // 检查是否有未结束的旧会话（异常情况），若有则强制结束
            val existingSession = sitSessionDao.getOngoingSession()
            if (existingSession != null) {
                endSession(existingSession)
            }

            Log.i(TAG, "开始新会话，当前模式: $_currentMode")
            sitSessionDao.insert(SitSessionEntity(
                startTime = System.currentTimeMillis(),
                mode = _currentMode
            ))
        }

        // 有人 -> 无人：结束当前会话
        if (wasOccupied && !isOccupied) {
            val ongoingSession = sitSessionDao.getOngoingSession()
            if (ongoingSession != null) {
                endSession(ongoingSession)
            }
        }
    }

    /**
     * 结束会话：计算时长、平均角度、久坐标记等，更新数据库
     */
    private suspend fun endSession(session: SitSessionEntity) {
        val now = System.currentTimeMillis()
        val durationMillis = now - session.startTime
        val durationMinutes = (durationMillis / 1000 / 60).toInt()
        val averageAngle = if (sessionSampleCount > 0) (sessionAngleSum / sessionSampleCount).toInt() else null

        if (durationMinutes >= 0) {
            val updatedSession = session.copy(
                endTime = now,
                durationMinutes = durationMinutes,
                wasCountedAsSedentary = durationMinutes > 45,  // 超过45分钟记为久坐
                averageAngle = averageAngle,
                postureChangeCount = sessionPostureChanges
            )
            sitSessionDao.update(updatedSession)
            Log.i(TAG, "结束会话: ${durationMinutes}分钟, 模式: ${session.mode}")
        }
    }

    /**
     * 已连接线程：负责读写蓝牙 Socket
     * 使用缓冲区 + 按行分割（换行符 \n）处理消息粘包
     */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val buffer = ByteArray(1024) // 1KB 缓冲区
        private val messageHandlerScope = CoroutineScope(Dispatchers.IO)

        override fun run() {
            Log.d(TAG, "✅ 读写线程已启动 (v1.5 字节流模式)")
            var messageBuffer = "" // 用于拼接不完整的消息

            while (true) {
                try {
                    val numBytes = mmInStream.read(buffer)  // 阻塞读取

                    if (numBytes > 0) {
                        val readData = String(buffer, 0, numBytes, Charset.defaultCharset())
                        messageBuffer += readData

                        // 处理可能的多条消息（按换行符分割）
                        while (messageBuffer.contains("\n")) {
                            val newlineIndex = messageBuffer.indexOf('\n')
                            val completeMessage = messageBuffer.substring(0, newlineIndex).trim()

                            if (completeMessage.isNotEmpty()) {
                                // 在 IO 线程中处理消息（避免阻塞读循环）
                                messageHandlerScope.launch {
                                    handleIncomingMessage(completeMessage)
                                }
                            }

                            // 移除已处理的消息
                            messageBuffer = messageBuffer.substring(newlineIndex + 1)
                        }
                    } else if (numBytes == -1) {
                        // read() 返回 -1 表示流已关闭，对端断开连接
                        Log.w(TAG, "🔌 对端关闭了连接 (read -1)。")
                        onDisconnect()
                        break
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "❌ Socket 读取异常，连接中断: ${e.message}")
                    onDisconnect()
                    break
                }
            }
            Log.d(TAG, "读写线程已结束。")
        }

        /**
         * 断开连接时的清理工作
         */
        private fun onDisconnect() {
            if (_connectionState.value == ConnectionState.Disconnected) return // 防止重复调用

            Log.i(TAG, "执行断开清理...")
            _connectionState.value = ConnectionState.Disconnected
            _chairStateFlow.value = _chairStateFlow.value.copy(isConnected = false)

            cancel() // 关闭资源

            if (!isUserDisconnect) {
                attemptReconnect()  // 非用户主动断开，尝试重连
            }
        }

        /**
         * 向输出流写入数据
         */
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
                mmOutStream.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Socket 写入异常: ${e.message}")
                onDisconnect()
            }
        }

        /**
         * 关闭所有流和 Socket
         */
        fun cancel() {
            try {
                mmInStream.close()
                Log.d(TAG, "InputStream 已关闭")
            } catch (e: IOException) {
                Log.e(TAG, "关闭 InputStream 失败", e)
            }
            try {
                mmOutStream.close()
                Log.d(TAG, "OutputStream 已关闭")
            } catch (e: IOException) {
                Log.e(TAG, "关闭 OutputStream 失败", e)
            }
            try {
                mmSocket.close()
                Log.d(TAG, "Socket 已关闭")
            } catch (e: IOException) {
                Log.e(TAG, "关闭 Socket 失败", e)
            }
        }
    }
}