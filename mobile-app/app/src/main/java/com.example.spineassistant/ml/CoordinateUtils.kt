package com.example.spineassistant.ml

import android.graphics.PointF
import androidx.camera.core.ImageProxy

/**
 * 坐标转换工具
 * 负责将 AI 分析结果 (通常基于较小的分析分辨率，如 480x640)
 * 映射到 屏幕显示分辨率 (如 1080x2400)
 */
object CoordinateUtils {

    fun mapPoint(
        x: Float,
        y: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        isFrontCamera: Boolean = false // 我们目前主要用后置，暂时设为false
    ): PointF {
        // 计算缩放比例
        // CameraX 的分析流通常是旋转过的，这里做简化处理：
        // 假设 analysis image 是横向的 (480x640)，而屏幕是竖向的
        // 在实际工程中，这里需要处理 rotationDegrees。
        // 为了 v1.6 演示稳定性，我们假设用户竖持手机，且相机输出也是竖向兼容的比例。

        val scaleX = targetWidth.toFloat() / sourceWidth
        val scaleY = targetHeight.toFloat() / sourceHeight

        // 保持比例缩放 (Center Crop 模式)
        val scale = maxOf(scaleX, scaleY)

        // 计算偏移量以居中
        val offsetX = (targetWidth - sourceWidth * scale) / 2
        val offsetY = (targetHeight - sourceHeight * scale) / 2

        var mappedX = x * scale + offsetX
        val mappedY = y * scale + offsetY

        // 如果是前置摄像头，需要镜像翻转 X 轴
        if (isFrontCamera) {
            mappedX = targetWidth - mappedX
        }

        return PointF(mappedX, mappedY)
    }
}
