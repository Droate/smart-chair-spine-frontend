package com.spineassistant.simulator.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.spineassistant.protocol.WebSocketMessage

val mapper: ObjectMapper = jacksonObjectMapper().apply {
    registerModule(JavaTimeModule())  // 必须手动注册，支持 Instant
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)  // 时间格式为 ISO 字符串
}

fun WebSocketMessage.toJson(): String = mapper.writeValueAsString(this)

inline fun <reified T> String.fromJson(): T? = try {
    mapper.readValue(this)
} catch (e: Exception) {
    null
}

fun String.toWebSocketMessage(): WebSocketMessage? = fromJson()