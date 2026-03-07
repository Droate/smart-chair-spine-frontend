package com.spineassistant.models

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 座椅状态
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChairState(
    // 设备信息
    val deviceId: String,
    val deviceName: String = "智能座椅",

    // 控制状态
    val height: Int = 450,           // 高度 (mm)
    val angle: Int = 95,             // 角度 (度)
    val lumbarSupport: Int = 50,     // 腰部支撑 (0-100)
    val tilt: Int = 0,               // 倾斜角度 (-15, 0, 15)
    val heatingLevel: Int = 0,       // 加热等级 (0-3)
    val massageIntensity: Int = 0,   // 按摩强度 (0-100)
    val massageMode: String = "NONE", // 按摩模式

    val isOccupied: Boolean = false, // 是否有人入座

    // 传感器数据
    val temperature: Double = 25.0,
    val humidity: Double = 50.0,
    // 🔥 [v1.7 新增] 靠背压力传感器 (0-100)
    // 0 = 完全离开椅背 (前倾/乌龟颈)
    // 100 = 紧贴椅背 (健康坐姿)
    val backPressure: Int = 0,

    val pressureMap: List<Int> = emptyList(),

    // 设备状态
    val isConnected: Boolean = false,
    val batteryLevel: Int = 100,
    val powerMode: PowerMode = PowerMode.AC,

    // 错误信息
    val errorCode: Int? = null,
    val errorMessage: String? = null
) {
    enum class PowerMode {
        AC,
        BATTERY,
        CHARGING
    }

    enum class MassageMode {
        NONE,
        WAVE,
        PULSE,
        KNOCK
    }

    companion object {
        fun default(deviceId: String): ChairState {
            return ChairState(
                deviceId = deviceId,
                deviceName = "智能座椅-$deviceId"
            )
        }
    }
}
