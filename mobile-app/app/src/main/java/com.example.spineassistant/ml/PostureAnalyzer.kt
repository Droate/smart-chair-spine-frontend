package com.example.spineassistant.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.io.IOException
import kotlin.math.abs

enum class PostureState {
    UNKNOWN,      // 无人/未检测到
    NORMAL,
    FORWARD_HEAD, // 乌龟颈/前倾
    DISTRACTED,   // 分心
    FATIGUE,      // 疲劳
    CALIBRATING,  // 校准中
    INITIALIZING, // 初始化中
    ERROR         // 错误
}

data class PostureResult(
    val state: PostureState,
    val pitchAngle: Int = 0,
    val yawAngle: Int = 0,
    val rollAngle: Int = 0,
    val faceLandmarks: List<NormalizedPoint> = emptyList(),
    val errorMessage: String? = null,
    val debugInfo: String = ""
)

data class NormalizedPoint(val x: Float, val y: Float, val z: Float)

class PostureAnalyzer(
    private val context: Context,
    private val onResult: (PostureResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var faceLandmarker: FaceLandmarker? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // === 状态变量 ===
    private var isCalibrating = false
    private var baselinePitch = 0f
    private var baselineZ = 0f

    // 平滑系数
    private val SMOOTH_ALPHA = 0.3f
    private var smoothPitch = 0f
    private var smoothYaw = 0f
    private var smoothRoll = 0f
    private var smoothZ = 0f

    // 防抖计数器
    private var abnormalFrameCount = 0
    private val TRIGGER_COUNT = 10

    init {
        notifyResult(PostureResult(PostureState.INITIALIZING))
        Thread { setupFaceLandmarker() }.start()
    }

    private fun notifyResult(result: PostureResult) {
        mainHandler.post { onResult(result) }
    }

    private fun setupFaceLandmarker() {
        val modelName = "face_landmarker.task"
        try {
            val list = context.assets.list("")
            if (list == null || !list.contains(modelName)) {
                val msg = "❌ 错误: 在 assets 中找不到 '$modelName'"
                Log.e("PostureAnalyzer", msg)
                notifyResult(PostureResult(PostureState.ERROR, errorMessage = msg))
                return
            }
        } catch (e: IOException) {
            notifyResult(PostureResult(PostureState.ERROR, errorMessage = "Assets 读取失败: ${e.message}"))
            return
        }

        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelName)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener { e -> Log.e("MediaPipe", "Runtime Error: ${e.message}") }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d("PostureAnalyzer", "✅ 模型加载成功")

        } catch (e: Exception) {
            Log.e("PostureAnalyzer", "初始化崩溃", e)
            notifyResult(PostureResult(PostureState.ERROR, errorMessage = "模型加载崩溃: ${e.message}"))
        }
    }

    fun triggerCalibration() {
        isCalibrating = true
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }

        val frameTime = SystemClock.uptimeMillis()

        imageProxy.use { proxy ->
            try {
                val bitmap = proxy.toBitmap()
                val matrix = Matrix().apply {
                    postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                    postScale(-1f, 1f)
                }
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                val mpImage = BitmapImageBuilder(rotatedBitmap).build()
                faceLandmarker?.detectAsync(mpImage, frameTime)

            } catch (e: Exception) {
                Log.e("Analyze", "图像处理失败", e)
            }
        }
    }

    private fun returnLivestreamResult(result: FaceLandmarkerResult, input: MPImage) {
        // 🟢🟢🟢 修复核心逻辑：当无人时，发射 UNKNOWN 状态并清空特征点 🟢🟢🟢
        if (result.faceLandmarks().isEmpty()) {
            abnormalFrameCount = 0 // 防抖归零
            Log.i("PostureTest", "【实测抓帧】最终输出状态: UNKNOWN (无人)")
            notifyResult(
                PostureResult(
                    state = PostureState.UNKNOWN,
                    faceLandmarks = emptyList(), // 清空 UI 上的连线
                    debugInfo = "等待目标入画..."
                )
            )
            return
        }

        val landmarks = result.faceLandmarks()[0]

        val noseTip = landmarks[1]
        val noseRoot = landmarks[8]
        val leftEye = landmarks[33]
        val rightEye = landmarks[263]

        val rawPitch = (noseTip.y() - noseRoot.y()) * 1500
        val midEyeX = (leftEye.x() + rightEye.x()) / 2
        val rawYaw = (noseTip.x() - midEyeX) * 1500
        val rawRoll = (leftEye.y() - rightEye.y()) * 1500
        val rawZ = noseTip.z()

        smoothPitch = (rawPitch * SMOOTH_ALPHA) + (smoothPitch * (1 - SMOOTH_ALPHA))
        smoothYaw = (rawYaw * SMOOTH_ALPHA) + (smoothYaw * (1 - SMOOTH_ALPHA))
        smoothRoll = (rawRoll * SMOOTH_ALPHA) + (smoothRoll * (1 - SMOOTH_ALPHA))
        smoothZ = (rawZ * SMOOTH_ALPHA) + (smoothZ * (1 - SMOOTH_ALPHA))

        val uiPoints = listOf(1, 33, 263, 152).map {
            NormalizedPoint(landmarks[it].x(), landmarks[it].y(), landmarks[it].z())
        }

        if (isCalibrating) {
            baselinePitch = smoothPitch
            baselineZ = smoothZ
            isCalibrating = false
            notifyResult(
                PostureResult(
                    PostureState.CALIBRATING,
                    smoothPitch.toInt(),
                    smoothYaw.toInt(),
                    smoothRoll.toInt(),
                    uiPoints,
                    debugInfo = "✅ 校准完成"
                )
            )
            return
        }

        var state = PostureState.NORMAL
        var debugReason = "正常"

        val pitchDiff = smoothPitch - baselinePitch
        val zDiff = smoothZ - baselineZ

        val PITCH_THRESHOLD = 25
        val Z_THRESHOLD = -0.01f
        val YAW_THRESHOLD = 40
        val ROLL_THRESHOLD = 25

        if (pitchDiff > PITCH_THRESHOLD) {
            state = PostureState.FORWARD_HEAD
            debugReason = "低头 (Diff: ${pitchDiff.toInt()} > $PITCH_THRESHOLD)"
        } else if (zDiff < Z_THRESHOLD) {
            state = PostureState.FORWARD_HEAD
            debugReason = "前伸 (Z-Diff: ${String.format("%.2f", zDiff)} < $Z_THRESHOLD)"
        } else if (abs(smoothYaw) > YAW_THRESHOLD) {
            state = PostureState.DISTRACTED
            debugReason = "转头"
        } else if (abs(smoothRoll) > ROLL_THRESHOLD) {
            state = PostureState.FATIGUE
            debugReason = "歪头"
        }

        val debugStr = "Pitch: ${smoothPitch.toInt()} (基准:${baselinePitch.toInt()})\n" +
                "Z-Depth: ${String.format("%.2f", smoothZ)} (基准:${String.format("%.2f", baselineZ)})\n" +
                "判定: $debugReason"

        if (state != PostureState.NORMAL) {
            abnormalFrameCount++
        } else {
            abnormalFrameCount = 0
        }

        val finalState = if (abnormalFrameCount >= TRIGGER_COUNT) state else PostureState.NORMAL

        // 专用于论文实测的日志埋点
        Log.i("PostureTest", "【实测抓帧】最终输出状态: ${finalState.name}")

        notifyResult(
            PostureResult(
                finalState,
                smoothPitch.toInt(),
                smoothYaw.toInt(),
                smoothRoll.toInt(),
                uiPoints,
                debugInfo = debugStr
            )
        )
    }
}
