package com.example.zt_yolo_demo_app.camera

import android.content.Context
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer

data class FrameData(
    val yBytes: ByteArray,
    val uBytes: ByteArray,
    val vBytes: ByteArray,
    val width: Int,
    val height: Int,
    val yRowStride: Int,
    val yPixelStride: Int,
    val uRowStride: Int,
    val uPixelStride: Int,
    val vRowStride: Int,
    val vPixelStride: Int
)

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var isStarted = false
    var onFrameAvailable: ((FrameData) -> Unit)? = null
    var shouldCapture: (() -> Boolean)? = null

    init {
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    fun start() {
        if (isStarted) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindUseCases()
            isStarted = true
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (!isStarted) return
        cameraProvider?.unbindAll()
        isStarted = false
    }

    fun isRunning(): Boolean = isStarted

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(Size(1280, 1280))
            .setTargetRotation(displayRotation)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    val image = imageProxy.image
                    if (image != null) {
                        if (shouldCapture?.invoke() == true) {
                            val frameData = copyImageData(image)
                            onFrameAvailable?.invoke(frameData)
                        }
                        image.close()
                    }
                    imageProxy.close()
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    }

    private fun copyImageData(image: android.media.Image): FrameData {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer: ByteBuffer = yPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vBuffer: ByteBuffer = vPlane.buffer

        val yBytes = ByteArray(yBuffer.remaining())
        val uBytes = ByteArray(uBuffer.remaining())
        val vBytes = ByteArray(vBuffer.remaining())

        yBuffer.rewind(); yBuffer.get(yBytes)
        uBuffer.rewind(); uBuffer.get(uBytes)
        vBuffer.rewind(); vBuffer.get(vBytes)

        return FrameData(
            yBytes, uBytes, vBytes,
            image.width, image.height,
            yPlane.rowStride, yPlane.pixelStride,
            uPlane.rowStride, uPlane.pixelStride,
            vPlane.rowStride, vPlane.pixelStride
        )
    }
}
