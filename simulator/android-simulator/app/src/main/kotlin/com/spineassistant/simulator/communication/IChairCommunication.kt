package com.spineassistant.simulator.communication

import com.spineassistant.models.ChairState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通用通信接口
 * 无论是 WebSocket 还是 Bluetooth，都必须遵守这个标准
 */
interface IChairCommunication {
    // 连接状态流 (UI 监听这个)
    val connectionState: StateFlow<ConnectionState>

    // 收到消息流 (UI 监听这个来解析指令)
    val incomingMessages: SharedFlow<String>

    // 调试日志流
    val debugLog: SharedFlow<String>

    // 启动服务/连接
    fun start()

    // 断开连接/停止服务
    fun stop()

    // 发送状态更新
    fun sendState(state: ChairState)
}
