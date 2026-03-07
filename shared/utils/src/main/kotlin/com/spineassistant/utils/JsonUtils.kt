package com.spineassistant.utils

object JsonUtils {
    // 简单的JSON工具类，稍后可以扩展
    fun toJson(obj: Any): String {
        // 暂时返回简单字符串，稍后添加Jackson实现
        return obj.toString()
    }
}
