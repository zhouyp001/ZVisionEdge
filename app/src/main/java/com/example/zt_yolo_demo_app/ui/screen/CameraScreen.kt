package com.example.zt_yolo_demo_app.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.ui.graphics.Color as ComposeColor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.zt_yolo_demo_app.camera.CameraController
import com.example.zt_yolo_demo_app.detection.ConfigLoader
import com.example.zt_yolo_demo_app.detection.Detection
import com.example.zt_yolo_demo_app.detection.PerfStats
import com.example.zt_yolo_demo_app.detection.PerformanceMonitor
import com.example.zt_yolo_demo_app.detection.YoloDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraOn by remember { mutableStateOf(false) }
    var detectionOn by remember { mutableStateOf(false) }
    var modelLoading by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var detections by remember { mutableStateOf(listOf<Detection>()) }
    val previewView = remember {
        PreviewView(context).also {
            it.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val overlayLayout = remember {
        CameraOverlayLayout(context).also {
            it.addView(previewView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
    }
    val yoloDetector = remember { YoloDetector(context) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val processing = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val frameSkipCounter = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val perfMonitor = remember { PerformanceMonitor(context) }
    var perfStats by remember { mutableStateOf(PerfStats(0, 0f, 0, 0, 0)) }
    val cameraController = remember {
        CameraController(context, lifecycleOwner, previewView)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            cameraOn = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraController.stop()
            yoloDetector.close()
        }
    }

    // Periodic performance stats refresh
    LaunchedEffect(Unit) {
        while (true) {
            perfStats = perfMonitor.getStats()
            delay(500)
        }
    }

    // Load config from assets/config.yml
    val config = remember { ConfigLoader.load(context) }
    val frameSkip = config.frameSkip // 0=每帧处理, 1=隔1帧, 2=隔2帧...

    // Quick check before copying frame data — avoids unnecessary allocations
    DisposableEffect(cameraController) {
        cameraController.shouldCapture = {
            if (!detectionOn || processing.get()) {
                false
            } else if (frameSkipCounter.incrementAndGet() > frameSkip) {
                frameSkipCounter.set(0)
                processing.set(true)
                true
            } else {
                false
            }
        }
        cameraController.onFrameAvailable = { frameData ->
            scope.launch {
                try {
                    val t0 = android.os.SystemClock.elapsedRealtime()
                    val bitmap = PreprocessorHelper.yuvToRgbBitmap(frameData)
                    val results = yoloDetector.detect(bitmap)
                    bitmap.recycle()
                    val elapsed = android.os.SystemClock.elapsedRealtime() - t0

                    detections = results
                    overlayLayout.setBoxes(results)
                    if (results.isNotEmpty()) {
                        Log.i("YOLO", results.joinToString(", ") { "${it.label}:%.0f%%".format(it.confidence * 100) })
                    }
                    perfMonitor.recordFrame(elapsed)
                } finally {
                    processing.set(false)
                }
            }
        }
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Preview area with overlay (fills entire screen)
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { overlayLayout },
                modifier = Modifier.fillMaxSize()
            )

            // Performance stats overlay (on top of everything)
            Column(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopStart)
                    .padding(start = 8.dp, top = 40.dp)
                    .background(ComposeColor.Black.copy(alpha = 0.5f))
                    .padding(6.dp)
            ) {
                Text(
                    text = "帧数:${perfStats.frameCount}  FPS:%.1f".format(perfStats.fps),
                    color = ComposeColor.White,
                    fontSize = 12.sp
                )
                Text(
                    text = "推理:%dms  CPU:%d%%".format(perfStats.inferenceMs, perfStats.cpuPercent),
                    color = ComposeColor.White,
                    fontSize = 12.sp
                )
                Text(
                    text = "内存:%d%%".format(perfStats.systemMemPercent),
                    color = ComposeColor.White,
                    fontSize = 12.sp
                )
            }
        }

        // Button row overlaid at bottom
        Row(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    if (cameraOn) {
                        cameraController.stop()
                        cameraOn = false
                        detectionOn = false
                        detections = emptyList()
                        overlayLayout.clear()
                    } else {
                        if (hasPermission) {
                            cameraController.start()
                            cameraOn = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(if (cameraOn) "关闭摄像头" else "开启摄像头")
            }

            Button(
                onClick = {
                    if (detectionOn) {
                        detectionOn = false
                        detections = emptyList()
                        overlayLayout.clear()
                    } else {
                        if (!yoloDetector.isLoaded()) {
                            modelLoading = true
                            scope.launch {
                                yoloDetector.load()
                                withContext(Dispatchers.Main) {
                                    modelLoading = false
                                    detectionOn = true
                                    frameSkipCounter.set(frameSkip) // capture first frame immediately
                                }
                            }
                        } else {
                            detectionOn = true
                            frameSkipCounter.set(frameSkip) // capture first frame immediately
                        }
                    }
                },
                enabled = cameraOn && !modelLoading,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    when {
                        modelLoading -> "加载模型中..."
                        detectionOn -> "停止识别"
                        else -> "开始识别"
                    }
                )
            }
        }
    }
}

/** Custom FrameLayout that wraps PreviewView and draws detection boxes on top. */
private class CameraOverlayLayout(context: Context) : android.widget.FrameLayout(context) {

    private val boxPaint = Paint().apply {
        color = AndroidColor.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = AndroidColor.RED
        textSize = 36f
        isAntiAlias = true
    }
    private var boxes: List<Detection> = emptyList()

    fun setBoxes(detections: List<Detection>) {
        boxes = detections
        postInvalidate()
    }

    fun clear() {
        boxes = emptyList()
        postInvalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (boxes.isEmpty()) return

        val scaleX = width / 640f
        val scaleY = height / 640f

        for (det in boxes) {
            val left = det.x1 * scaleX
            val top = det.y1 * scaleY
            val right = det.x2 * scaleX
            val bottom = det.y2 * scaleY

            canvas.drawRect(left, top, right, bottom, boxPaint)
            val label = if (det.label.isNotEmpty()) {
                "%s %.0f%%".format(det.label, det.confidence * 100)
            } else {
                "%.0f%%".format(det.confidence * 100)
            }
            canvas.drawText(label, left, top - 4f, textPaint)
        }
    }
}

// Helper: direct YUV→RGB conversion + scale, avoids JPEG round-trip bottleneck
private object PreprocessorHelper {

    private const val TARGET_SIZE = 640

    fun yuvToRgbBitmap(frame: com.example.zt_yolo_demo_app.camera.FrameData): Bitmap {
        val srcW = frame.width
        val srcH = frame.height

        val bitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
        val rowPixels = IntArray(TARGET_SIZE)

        val uPixelStride = frame.uPixelStride
        val vPixelStride = frame.vPixelStride
        val semiPlanar = frame.vBytes.isEmpty() || vPixelStride == 2

        for (dstY in 0 until TARGET_SIZE) {
            val srcY = (dstY * srcH / TARGET_SIZE).coerceIn(0, srcH - 1)
            val yRowStart = srcY * frame.yRowStride
            val uvRow = srcY / 2
            val uRowStart = uvRow * frame.uRowStride
            val vRowStart = uvRow * frame.vRowStride

            for (dstX in 0 until TARGET_SIZE) {
                val srcX = (dstX * srcW / TARGET_SIZE).coerceIn(0, srcW - 1)
                val uvX = srcX / 2

                val y = frame.yBytes[yRowStart + srcX].toInt() and 0xFF
                val u: Int
                val v: Int

                if (semiPlanar) {
                    // U and V interleaved in uBytes: UVUVUV... (NV12) or VUVUVU... (NV21)
                    val uvIndex = uRowStart + uvX * 2
                    if (frame.vBytes.isEmpty()) {
                        // Only uBytes has data, layout is UV or VU pairs
                        u = frame.uBytes[uvIndex].toInt() and 0xFF
                        v = frame.uBytes[uvIndex + 1].toInt() and 0xFF
                    } else {
                        // NV21 style: uBytes has U, vBytes has V (but interleaved)
                        // Actually if vPixelStride==2, vBytes may contain VU pairs
                        u = frame.uBytes[uRowStart + uvX * uPixelStride].toInt() and 0xFF
                        v = frame.vBytes[vRowStart + uvX * vPixelStride].toInt() and 0xFF
                    }
                } else {
                    // Planar: separate U and V buffers, pixelStride=1
                    u = frame.uBytes[uRowStart + uvX].toInt() and 0xFF
                    v = frame.vBytes[vRowStart + uvX].toInt() and 0xFF
                }

                // BT.601 YUV→RGB integer conversion
                val yv = y - 16
                val r = ((yv * 298 + (v - 128) * 409 + 128) shr 8).coerceIn(0, 255)
                val g = ((yv * 298 - (u - 128) * 100 - (v - 128) * 208 + 128) shr 8).coerceIn(0, 255)
                val b = ((yv * 298 + (u - 128) * 516 + 128) shr 8).coerceIn(0, 255)

                rowPixels[dstX] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(rowPixels, 0, TARGET_SIZE, 0, dstY, TARGET_SIZE, 1)
        }
        return bitmap
    }
}
