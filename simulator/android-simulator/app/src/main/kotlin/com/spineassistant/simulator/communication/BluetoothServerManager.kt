package com.spineassistant.simulator.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log // 新增：用于打印到 Logcat
import com.spineassistant.models.ChairState
import com.spineassistant.protocol.StateUpdate
import com.spineassistant.simulator.websocket.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat // 新增：用于格式化毫秒时间
import java.util.Date // 新增
import java.util.Locale // 新增
import java.util.UUID

/**
 * 蓝牙服务端管理器 (用于真机)
 * 模拟器作为服务端，等待 APP 连接
 */
class BluetoothServerManager(
    private val context: Context
) : IChairCommunication {

    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // 标准 SPP UUID
    private val NAME = "SmartChairSimulator"

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var acceptThread: AcceptThread? = null
    private var connectedThread: ConnectedThread? = null

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    override val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val _debugLog = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 100)
    override val debugLog: SharedFlow<String> = _debugLog.asSharedFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 🟢 新增：用于格式化带毫秒的时间戳
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // 🟢 新增：用于计算通信耗时的变量
    private var lastReceiveTimeMs: Long = 0L

    @SuppressLint("MissingPermission")
    override fun start() {
        if (bluetoothAdapter == null) {
            log("错误: 设备不支持蓝牙")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            log("错误: 蓝牙未开启，请先开启蓝牙")
            return
        }

        log("启动蓝牙服务，等待连接...")
        _connectionState.value = ConnectionState.Connecting

        if (acceptThread == null || !acceptThread!!.isAlive) {
            acceptThread = AcceptThread().apply { start() }
        }
    }

    override fun stop() {
        log("停止蓝牙服务")
        acceptThread?.cancel()
        acceptThread = null
        connectedThread?.cancel()
        connectedThread = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun sendState(state: ChairState) {
        if (_connectionState.value != ConnectionState.Connected) return

        val update = StateUpdate(
            deviceId = "simulator-bt-001",
            chairState = state
        )
        val json = update.toJson() + "\n"
        connectedThread?.write(json.toByteArray())

        // 🟢 新增：计算并打印从收到指令到发出状态的通信处理耗时
        if (lastReceiveTimeMs > 0) {
            val responseTime = System.currentTimeMillis() - lastReceiveTimeMs
            log("⏱️【通信耗时】收到指令并完成状态回传耗时: ${responseTime} ms")
            lastReceiveTimeMs = 0L // 重置，准备下一次计算
        }
    }

    // 🟢 修改：原封不动保留你的输出文字，但加上毫秒时间戳和 Logcat 打印
    private fun log(msg: String) {
        val timestamp = timeFormatter.format(Date())
        val logMessage = "[$timestamp] $msg"
        _debugLog.tryEmit(logMessage) // 依然输出到你的模拟器UI
        Log.d("BluetoothTest", logMessage) // 同步输出到 Android Studio，方便过滤和统计
    }

    @SuppressLint("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(NAME, MY_UUID)
        }

        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    log("监听 Socket 接受失败: ${e.message}")
                    shouldLoop = false
                    null
                }
                socket?.also {
                    log("✅ 已接受连接: ${it.remoteDevice.name}")
                    manageMyConnectedSocket(it)
                    mmServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                log("关闭 ServerSocket 失败: ${e.message}")
            }
        }
    }

    private fun manageMyConnectedSocket(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
        _connectionState.value = ConnectionState.Connected
    }

    // --- 🔹【最终修复】改造 ConnectedThread，放弃 readLine() 🔹 ---
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val buffer = ByteArray(1024) // 1KB 的缓冲区

        override fun run() {
            log("读写线程已启动 (新版)，等待指令...")
            var numBytes: Int // 从 read() 返回的字节数
            var messageBuffer = "" // 用于拼接不完整的消息

            while (true) {
                try {
                    // read() 是一个阻塞方法，会等待直到有数据可读
                    numBytes = mmInStream.read(buffer)
                    // 🔴【关键日志 1】检查是否真的读到了数据
                    log("Read: $numBytes bytes")

                    if (numBytes > 0) {
                        // 将读到的字节转换为字符串，并拼接到消息缓冲区
                        val readData = String(buffer, 0, numBytes, Charset.defaultCharset())
                        messageBuffer += readData

                        // 🔴【关键日志 2】查看当前缓冲区内容
                        log("Buffer: '$messageBuffer'")

                        // 循环处理，因为一次可能收到多条消息，或者半条消息
                        while (messageBuffer.contains("\n")) {
                            val newlineIndex = messageBuffer.indexOf("\n")
                            // 提取一条完整的消息 (从开头到换行符)
                            val completeMessage = messageBuffer.substring(0, newlineIndex)

                            // 🔴【关键日志 3】确认提取到的完整消息
                            log("Found Msg: '$completeMessage'")

                            // 🟢 新增：记录收到完整指令的毫秒级时间戳，用于计算耗时
                            lastReceiveTimeMs = System.currentTimeMillis()

                            // 发射消息到上层
                            coroutineScope.launch {
                                _incomingMessages.emit(completeMessage)
                            }

                            // 从缓冲区移除已处理的消息 (包括换行符)
                            messageBuffer = messageBuffer.substring(newlineIndex + 1)
                        }
                    } else if (numBytes == -1) {
                        // read() 返回 -1 表示流已关闭
                        log("输入流已关闭 (read -1)，对端断开连接。")
                        break
                    }
                } catch (e: IOException) {
                    // 发生 IO 异常，通常意味着连接被非正常关闭
                    log("连接断开 (IOEx): ${e.message}")
                    break // 退出循环
                }
            }

            // 只要循环结束，就意味着连接已断开，清理状态
            _connectionState.value = ConnectionState.Disconnected
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
                mmOutStream.flush() // 强制将缓冲区数据发送出去
            } catch (e: IOException) {
                log("发送数据失败: ${e.message}")
                // 发送失败也意味着连接可能已断开
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        fun cancel() {
            try {
                mmInStream.close()
                mmOutStream.close()
                mmSocket.close()
            } catch (e: IOException) {
                log("关闭 Socket 失败")
            }
        }
    }
}
