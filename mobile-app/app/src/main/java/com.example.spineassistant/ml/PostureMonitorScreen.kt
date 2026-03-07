package com.example.spineassistant.ui

import android.Manifest
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.spineassistant.ml.NormalizedPoint
import com.example.spineassistant.ml.PostureAnalyzer
import com.example.spineassistant.ml.PostureResult
import com.example.spineassistant.ml.PostureState
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureMonitorScreen(
    onBack: () -> Unit,
    onPostureWarning: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var calibrationTrigger by remember { mutableLongStateOf(0L) }

    var currentPostureState by remember { mutableStateOf(PostureState.INITIALIZING) }

    var pitch by remember { mutableIntStateOf(0) }
    var yaw by remember { mutableIntStateOf(0) }
    var roll by remember { mutableIntStateOf(0) }
    var facePoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var debugInfoStr by remember { mutableStateOf("") }

    var sourceSize by remember { mutableStateOf(Size(480, 640)) }
    var targetSize by remember { mutableStateOf(Size(0, 0)) }

    LaunchedEffect(currentPostureState) {
        if (currentPostureState == PostureState.FORWARD_HEAD || currentPostureState == PostureState.FATIGUE) {
            onPostureWarning()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        if (!it) onBack()
    }
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D 头部姿态监测") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT }) { Icon(Icons.Default.Cameraswitch, "切换", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = 0.5f), titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { innerPadding -> // 🟢 Scaffold 提供的 Padding
        if (hasPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(innerPadding) // 🟢🟢🟢 修复报错：在这里应用 innerPadding，避免内容被 TopAppBar 遮挡
            ) {
                PostureCameraPreview(
                    lensFacing = lensFacing,
                    calibrationTrigger = calibrationTrigger,
                    modifier = Modifier.fillMaxSize().onSizeChanged { targetSize = Size(it.width, it.height) },
                    onAnalyzerResult = { result, width, height ->
                        if (result.state == PostureState.CALIBRATING) Toast.makeText(context, "✅ 3D 基准已校准！", Toast.LENGTH_SHORT).show()
                        currentPostureState = result.state
                        errorMsg = result.errorMessage
                        if (result.state != PostureState.ERROR && result.state != PostureState.INITIALIZING) {
                            pitch = result.pitchAngle
                            yaw = result.yawAngle
                            roll = result.rollAngle
                            facePoints = result.faceLandmarks
                            debugInfoStr = result.debugInfo
                            sourceSize = Size(width, height)
                        }
                    }
                )

                if (currentPostureState == PostureState.INITIALIZING) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在加载 3D 模型...", color = Color.White)
                        }
                    }
                } else if (currentPostureState == PostureState.ERROR) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(Icons.Default.Error, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("初始化失败: $errorMsg", color = Color.White)
                        }
                    }
                } else {
                    // 当有人物时，绘制骨架连线（如果 facePoints 为空，内部自动不绘制）
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (targetSize.width > 0 && facePoints.isNotEmpty()) {
                            val scaleX = targetSize.width.toFloat() / sourceSize.width
                            val scaleY = targetSize.height.toFloat() / sourceSize.height

                            fun mapPoint(np: NormalizedPoint): Offset {
                                val x = np.x * sourceSize.width * scaleX
                                val y = np.y * sourceSize.height * scaleY
                                return Offset(x, y)
                            }

                            if (facePoints.size >= 5) {
                                val nose = mapPoint(facePoints[0])
                                val leftEye = mapPoint(facePoints[1])
                                val rightEye = mapPoint(facePoints[2])
                                val chin = mapPoint(facePoints[3])

                                val paintColor = when(currentPostureState) {
                                    PostureState.FORWARD_HEAD -> Color.Red
                                    PostureState.DISTRACTED -> Color.Yellow
                                    PostureState.FATIGUE -> Color(0xFFFFA500)
                                    else -> Color.Green
                                }

                                drawLine(color = paintColor, start = leftEye, end = rightEye, strokeWidth = 8f, cap = StrokeCap.Round)
                                val midEye = Offset((leftEye.x + rightEye.x) / 2, (leftEye.y + rightEye.y) / 2)
                                drawLine(color = paintColor, start = midEye, end = chin, strokeWidth = 8f, cap = StrokeCap.Round)
                                drawCircle(Color.Cyan.copy(alpha=0.6f), 10f, nose)
                            }
                        }
                    }

                    Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).padding(horizontal = 24.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when(currentPostureState) {
                                    PostureState.FORWARD_HEAD -> MaterialTheme.colorScheme.errorContainer
                                    PostureState.DISTRACTED -> Color(0xFFFFF9C4)
                                    PostureState.FATIGUE -> Color(0xFFFFE0B2)
                                    PostureState.UNKNOWN -> Color.LightGray.copy(alpha = 0.8f) // 无人时变成灰色
                                    else -> Color(0xFFE8F5E9)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (emoji, text) = when(currentPostureState) {
                                        PostureState.FORWARD_HEAD -> "🚫" to "不良坐姿"
                                        PostureState.DISTRACTED -> "👀" to "注意力不集中"
                                        PostureState.FATIGUE -> "😫" to "疲劳"
                                        PostureState.UNKNOWN -> "🫥" to "未检测到人物"
                                        else -> "🧘" to "坐姿良好"
                                    }
                                    Text(emoji, fontSize = 36.sp)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (debugInfoStr.isEmpty()) "等待数据..." else debugInfoStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.DarkGray,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // 无人时禁用校准按钮，防止点错
                        Button(
                            onClick = { calibrationTrigger = System.currentTimeMillis() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = currentPostureState != PostureState.UNKNOWN
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重置 3D 基准面", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostureCameraPreview(
    lensFacing: Int,
    calibrationTrigger: Long,
    modifier: Modifier = Modifier,
    onAnalyzerResult: (PostureResult, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analyzer = remember { PostureAnalyzer(context) { result -> onAnalyzerResult(result, 480, 640) } }

    LaunchedEffect(calibrationTrigger) { if (calibrationTrigger > 0) analyzer.triggerCalibration() }

    AndroidView(
        factory = { ctx -> PreviewView(ctx) },
        modifier = modifier,
        update = { previewView ->
            val executor = Executors.newSingleThreadExecutor()
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(executor, analyzer)
                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                } catch (e: Exception) { Log.e("PoseCam", "Bind failed", e) }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}
