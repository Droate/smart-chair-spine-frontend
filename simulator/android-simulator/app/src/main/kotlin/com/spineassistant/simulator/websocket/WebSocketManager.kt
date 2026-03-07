package com.spineassistant.simulator.websocket // 建议改为 com.spineassistant.simulator.communication

import com.spineassistant.models.ChairState
import com.spineassistant.protocol.Heartbeat
import com.spineassistant.protocol.StateUpdate
import com.spineassistant.simulator.communication.IChairCommunication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import com.spineassistant.simulator.communication.ConnectionState

// 增加实现接口声明
class WebSocketManager(
    private val url: String = "wss://echo.websocket.org"
) : IChairCommunication { // <--- 关键修改：实现接口

    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val _incomingMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    override val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _debugLog = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 100)
    override val debugLog: SharedFlow<String> = _debugLog.asSharedFlow()

    private var heartbeatJob: Job? = null

    // 将 connect 改名为 start 以匹配接口
    override fun start() {
        connect()
    }

    // 将 disconnect 改名为 stop 以匹配接口
    override fun stop() {
        disconnect()
    }

    // 内部连接逻辑保持不变
    private fun connect() {
        log("正在连接 WebSocket (电脑调试模式): $url")
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.Connected
                log("WebSocket 连接成功")
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // log("收到: $text") // 防止日志刷屏，可注释
                _incomingMessages.tryEmit(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                log("WebSocket 正在关闭: $code $reason")
                cleanup()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log("WebSocket 连接失败: ${t.message}")
                _connectionState.value = ConnectionState.Disconnected
                cleanup()
            }
        })
    }

    private fun disconnect() {
        log("主动断开 WebSocket")
        cleanup()
        webSocket?.close(1000, "Manual close")
        webSocket = null
    }

    // 接口实现：发送状态
    override fun sendState(state: ChairState) {
        val update = StateUpdate(
            deviceId = "simulator-001",
            chairState = state
        )
        sendMessage(update.toJson())
    }

    private fun sendHeartbeat() {
        val heartbeat = Heartbeat(connectionId = "simulator-connection")
        sendMessage(heartbeat.toJson())
    }

    private fun sendMessage(message: String) {
        webSocket?.send(message) ?: log("未连接，发送失败")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(10000)
                sendHeartbeat()
            }
        }
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun log(message: String) {
        _debugLog.tryEmit(message)
    }
}
