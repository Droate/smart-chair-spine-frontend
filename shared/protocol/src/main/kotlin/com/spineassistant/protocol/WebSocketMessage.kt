package com.spineassistant.protocol

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.spineassistant.models.ChairState
import java.util.*

/**
 * WebSocket消息基类
 * 修复：将 Instant 改为 Long，解决 Android 端 Jackson 序列化崩溃的问题
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ControlCommand::class, name = "CONTROL_COMMAND"),
    JsonSubTypes.Type(value = StateUpdate::class, name = "STATE_UPDATE"),
    JsonSubTypes.Type(value = ErrorResponse::class, name = "ERROR_RESPONSE"),
    JsonSubTypes.Type(value = ConnectionRequest::class, name = "CONNECTION_REQUEST"),
    JsonSubTypes.Type(value = ConnectionResponse::class, name = "CONNECTION_RESPONSE"),
    JsonSubTypes.Type(value = Heartbeat::class, name = "HEARTBEAT")
)
sealed class WebSocketMessage(
    val messageId: String = UUID.randomUUID().toString(),
    // 🔴 关键修改：Instant -> Long (使用毫秒时间戳)
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType
) {
    enum class MessageType {
        CONTROL_COMMAND,
        STATE_UPDATE,
        ERROR_RESPONSE,
        CONNECTION_REQUEST,
        CONNECTION_RESPONSE,
        HEARTBEAT
    }
}

/**
 * 控制指令消息（APP → 模拟器）
 */
data class ControlCommand(
    val deviceId: String,
    val command: CommandType,
    val parameters: Map<String, Any> = emptyMap(),
    val userId: String? = null
) : WebSocketMessage(type = MessageType.CONTROL_COMMAND) {

    enum class CommandType {
        ADJUST_HEIGHT,      // 调整高度
        ADJUST_ANGLE,       // 调整角度
        ADJUST_LUMBAR,      // 调整腰部支撑
        SET_HEATING,        // 设置加热
        SET_MASSAGE,        // 设置按摩
        REQUEST_STATE,      // 请求状态
        APPLY_PRESET,       // 应用预设
        SAVE_PRESET,        // 保存预设
        REBOOT,             // 重启设备

        // 🔥 新增：震动报警指令 (v1.6 Feature)
        ALERT_VIBRATION
    }

    companion object {
        fun adjustHeight(deviceId: String, height: Int): ControlCommand {
            return ControlCommand(
                deviceId = deviceId,
                command = CommandType.ADJUST_HEIGHT,
                parameters = mapOf("height" to height)
            )
        }

        fun adjustAngle(deviceId: String, angle: Int): ControlCommand {
            return ControlCommand(
                deviceId = deviceId,
                command = CommandType.ADJUST_ANGLE,
                parameters = mapOf("angle" to angle)
            )
        }

        // 🔥 便捷方法
        fun alertVibration(deviceId: String): ControlCommand {
            return ControlCommand(
                deviceId = deviceId,
                command = CommandType.ALERT_VIBRATION
            )
        }
    }
}

/**
 * 状态更新消息（模拟器 → APP）
 */
data class StateUpdate(
    val deviceId: String,
    val chairState: ChairState,
    val updateType: UpdateType = UpdateType.FULL
) : WebSocketMessage(type = MessageType.STATE_UPDATE) {

    enum class UpdateType {
        FULL,      // 完整状态更新
        PARTIAL,   // 部分状态更新
        REAL_TIME  // 实时传感器数据
    }
}

/**
 * 错误响应消息
 */
data class ErrorResponse(
    val requestId: String,
    val errorCode: ErrorCode,
    val errorMessage: String,
    val details: Map<String, Any> = emptyMap()
) : WebSocketMessage(type = MessageType.ERROR_RESPONSE) {

    enum class ErrorCode {
        DEVICE_NOT_FOUND,
        INVALID_COMMAND,
        DEVICE_OFFLINE,
        INVALID_PARAMETERS,
        INTERNAL_ERROR
    }
}

/**
 * 连接请求消息
 */
data class ConnectionRequest(
    val deviceId: String,
    val clientType: ClientType,
    val clientVersion: String,
    val capabilities: List<String> = emptyList()
) : WebSocketMessage(type = MessageType.CONNECTION_REQUEST) {

    enum class ClientType {
        MOBILE_APP,
        SIMULATOR,
        WEB_CLIENT
    }
}

/**
 * 连接响应消息
 */
data class ConnectionResponse(
    val connectionId: String,
    val serverVersion: String,
    val supportedCommands: List<ControlCommand.CommandType>,
    val sessionTimeout: Long = 300000 // 5分钟
) : WebSocketMessage(type = MessageType.CONNECTION_RESPONSE)

/**
 * 心跳消息
 */
data class Heartbeat(
    val connectionId: String,
    // 🔴 关键修改：Instant -> Long
    val heartbeatTimestamp: Long = System.currentTimeMillis(),
    val status: String = "OK"
) : WebSocketMessage(type = MessageType.HEARTBEAT)
