package com.zhouyp.visionedge.detection

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

class YoloDetector(private val context: Context) {

    private var session: OrtSession? = null
    private var environment: OrtEnvironment? = null
    private var modelFile: File? = null

    fun isLoaded(): Boolean = session != null

    fun load() {
        if (session != null) return

        if (modelFile == null) {
            modelFile = copyModelFromAssets()
        }

        environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        // NNAPI disabled for initial testing; re-enable after CPU path is verified
        session = environment!!.createSession(modelFile!!.absolutePath, options)
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val ortSession = session ?: return emptyList()
        val env = environment ?: return emptyList()

        val inputSize = Preprocessor.INPUT_SIZE

        val pixelCount = inputSize * inputSize
        val chwArray = FloatArray(3 * pixelCount)
        val gOffset = pixelCount
        val bOffset = 2 * pixelCount

        val rowPixels = IntArray(inputSize)
        for (y in 0 until inputSize) {
            bitmap.getPixels(rowPixels, 0, inputSize, 0, y, inputSize, 1)
            val rowBase = y * inputSize
            for (x in 0 until inputSize) {
                val pixel = rowPixels[x]
                val i = rowBase + x
                chwArray[i] = (pixel shr 16 and 0xFF) / 255.0f
                chwArray[gOffset + i] = (pixel shr 8 and 0xFF) / 255.0f
                chwArray[bOffset + i] = (pixel and 0xFF) / 255.0f
            }
        }

        val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chwArray), shape)

        val inputs = mapOf("images" to tensor)
        val output = ortSession.run(inputs)
        tensor.close()

        val resultTensor = output.first().value as OnnxTensor
        val floatBuffer = resultTensor.floatBuffer
        val rawOutput = FloatArray(floatBuffer.remaining())
        floatBuffer.get(rawOutput)
        output.close()

        return Postprocessor.process(rawOutput)
    }

    fun close() {
        session?.close()
        environment?.close()
        session = null
        environment = null
    }

    private fun copyModelFromAssets(): File {
        val modelName = "yolov8n.onnx"
        val destFile = File(context.filesDir, modelName)
        if (destFile.exists()) return destFile

        context.assets.open(modelName).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }
}
