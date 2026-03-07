package com.spineassistant.simulator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.spineassistant.models.ChairState

/**
 * 🎨 旗舰级人体工学椅可视化组件
 * v1.3 Update: 适配标准办公椅高度 (350-600mm)
 */
@Composable
fun PremiumChairVisualization(chairState: ChairState) {
    val animSpec = tween<Float>(durationMillis = 800, easing = FastOutSlowInEasing)
    val animatedHeight by animateFloatAsState(targetValue = chairState.height.toFloat(), animationSpec = animSpec, label = "H")
    val animatedAngle by animateFloatAsState(targetValue = chairState.angle.toFloat(), animationSpec = animSpec, label = "A")

    // 材质颜色
    val metallicGradient = Brush.linearGradient(
        colors = listOf(Color(0xFFCFD8DC), Color(0xFFECEFF1), Color(0xFFB0BEC5), Color(0xFF607D8B)),
        start = Offset(0f, 0f),
        end = Offset(100f, 100f)
    )
    val meshColor = Color(0xFF37474F)
    val frameColor = Color(0xFF263238)
    val plasticColor = Color(0xFF455A64)

    // 🔥 核心修改: 映射公式调整为 350mm - 600mm (行程 250mm)
    // 之前是 (h - 600) / 300
    val liftProgress = ((animatedHeight - 350f) / 250f).coerceIn(0f, 1f)

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(240.dp, 300.dp)) {
            val cx = size.width / 2
            val bottomY = size.height - 80f

            scale(scale = 1.3f, pivot = Offset(cx, bottomY - 100f)) {

                // --- 1. 动态高度 ---
                val rodMaxExt = 50f
                val currentRodExt = rodMaxExt * liftProgress
                val baseHeight = 40f
                val seatMechY = bottomY - baseHeight - currentRodExt

                // --- 2. 阴影 ---
                val shadowR = 70f - (liftProgress * 15f)
                drawOval(
                    color = Color.Black.copy(alpha = 0.2f),
                    topLeft = Offset(cx - shadowR, bottomY - 5f),
                    size = Size(shadowR * 2, 10f)
                )

                // --- 3. 五星脚架 ---
                val legW = 100f
                drawLine(metallicGradient, Offset(cx, bottomY - 20f), Offset(cx - legW / 2, bottomY + 5f), 10f, StrokeCap.Round)
                drawLine(metallicGradient, Offset(cx, bottomY - 20f), Offset(cx + legW / 2, bottomY + 5f), 10f, StrokeCap.Round)
                drawLine(metallicGradient, Offset(cx, bottomY - 20f), Offset(cx, bottomY + 15f), 10f, StrokeCap.Round)

                drawCircle(Color.Black, 6f, Offset(cx - legW / 2, bottomY + 11f))
                drawCircle(Color.Black, 6f, Offset(cx + legW / 2, bottomY + 11f))
                drawCircle(Color.Black, 6f, Offset(cx, bottomY + 21f))

                // --- 4. 气压杆 ---
                drawRect(Color.Black, topLeft = Offset(cx - 8f, bottomY - 40f), size = Size(16f, 30f))
                drawRect(metallicGradient, topLeft = Offset(cx - 5f, seatMechY + 25f), size = Size(10f, bottomY - 40f - (seatMechY + 25f)))

                // --- 5. 机械底盘 ---
                drawRoundRect(Color.Black, topLeft = Offset(cx - 25f, seatMechY), size = Size(50f, 25f), cornerRadius = CornerRadius(5f, 5f))
                // 调节杆
                drawLine(Color.Gray, Offset(cx - 10f, seatMechY + 12f), Offset(cx - 45f, seatMechY + 8f), 4f, StrokeCap.Round)
                drawCircle(Color.Black, 5f, Offset(cx - 45f, seatMechY + 8f))

                // --- 6. 坐垫 ---
                val seatPath = Path().apply {
                    moveTo(cx + 40f, seatMechY)
                    quadraticBezierTo(cx + 45f, seatMechY - 15f, cx + 40f, seatMechY - 20f)
                    lineTo(cx - 35f, seatMechY - 20f)
                    cubicTo(cx - 55f, seatMechY - 20f, cx - 55f, seatMechY + 5f, cx - 35f, seatMechY + 5f)
                    lineTo(cx + 40f, seatMechY)
                    close()
                }
                drawPath(seatPath, meshColor)
                drawPath(seatPath, frameColor, style = Stroke(2f))

                // --- 7. 椅背 & 模拟人像 ---
                val pivotX = cx + 25f
                val pivotY = seatMechY + 10f
                val rotation = (animatedAngle - 90f)

                rotate(degrees = rotation, pivot = Offset(pivotX, pivotY)) {
                    // 连接支架
                    val spinePath = Path().apply {
                        moveTo(pivotX, pivotY)
                        quadraticBezierTo(pivotX + 20f, pivotY - 60f, pivotX + 10f, pivotY - 120f)
                        lineTo(pivotX, pivotY - 120f)
                        quadraticBezierTo(pivotX + 10f, pivotY - 60f, pivotX - 10f, pivotY)
                        close()
                    }
                    drawPath(spinePath, frameColor)

                    // 网布椅面
                    val backMeshPath = Path().apply {
                        val topY = pivotY - 140f
                        val bottomY = pivotY - 20f
                        val backX = pivotX + 15f

                        moveTo(backX, bottomY)
                        cubicTo(
                            backX - 25f, bottomY - 40f,
                            backX + 10f, topY + 40f,
                            backX - 5f, topY
                        )
                        lineTo(backX + 5f, topY)
                        cubicTo(
                            backX + 20f, topY + 40f,
                            backX - 10f, bottomY - 40f,
                            backX + 10f, bottomY
                        )
                        close()
                    }
                    drawPath(backMeshPath, meshColor)
                    drawPath(backMeshPath, frameColor, style = Stroke(2f))

                    // 头枕
                    drawRoundRect(plasticColor, topLeft = Offset(pivotX - 10f, pivotY - 160f), size = Size(30f, 18f), cornerRadius = CornerRadius(5f, 5f))
                    drawLine(Color.Gray, Offset(pivotX + 5f, pivotY - 140f), Offset(pivotX + 5f, pivotY - 150f), 4f)

                    // --- 模拟小人 ---
                    if (chairState.isOccupied) {
                        // 躯干
                        drawLine(Color.Blue, Offset(pivotX + 5f, pivotY - 20f), Offset(pivotX, pivotY - 100f), 8f, StrokeCap.Round)
                        // 头部
                        drawCircle(Color.Blue, 15f, Offset(pivotX, pivotY - 120f))
                    }

                    // 扶手
                    val armX = pivotX
                    val armY = pivotY - 40f
                    drawLine(frameColor, Offset(armX, armY), Offset(armX - 10f, armY - 20f), 8f, StrokeCap.Round)
                    drawRoundRect(plasticColor, topLeft = Offset(armX - 40f, armY - 25f), size = Size(35f, 6f), cornerRadius = CornerRadius(3f, 3f))
                }
            }
        }
    }
}
