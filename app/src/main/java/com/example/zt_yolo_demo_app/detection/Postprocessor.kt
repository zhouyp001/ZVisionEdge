package com.example.zt_yolo_demo_app.detection

object Postprocessor {

    private const val CONFIDENCE_THRESHOLD = 0.80f
    private const val IOU_THRESHOLD = 0.45f
    private const val INPUT_SIZE = 640f
    private const val NUM_CLASSES = 80

    fun process(output: FloatArray): List<Detection> {
        val numDetections = output.size / (4 + NUM_CLASSES)
        val detections = mutableListOf<Detection>()

        for (d in 0 until numDetections) {
            val cx = output[d]
            val cy = output[numDetections + d]
            val w  = output[2 * numDetections + d]
            val h  = output[3 * numDetections + d]

            var bestClassId = -1
            var bestScore = 0f
            for (c in 0 until NUM_CLASSES) {
                val score = output[(4 + c) * numDetections + d]
                if (score > bestScore) {
                    bestScore = score
                    bestClassId = c
                }
            }

            if (bestScore < CONFIDENCE_THRESHOLD) continue

            // ONNX模型输出已经是像素坐标[0,640]，不需要再乘INPUT_SIZE
            val x1 = (cx - w / 2).coerceIn(0f, INPUT_SIZE)
            val y1 = (cy - h / 2).coerceIn(0f, INPUT_SIZE)
            val x2 = (cx + w / 2).coerceIn(0f, INPUT_SIZE)
            val y2 = (cy + h / 2).coerceIn(0f, INPUT_SIZE)

            detections.add(Detection(
                x1 = x1,
                y1 = y1,
                x2 = x2,
                y2 = y2,
                confidence = bestScore,
                classId = bestClassId,
                label = CocoClasses.names.getOrElse(bestClassId) { "" }
            ))
        }

        return nmsPerClass(detections)
    }

    /** Apply NMS independently per class so different classes don't suppress each other. */
    private fun nmsPerClass(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val grouped = detections.groupBy { it.classId }
        val result = mutableListOf<Detection>()
        for ((_, classBoxes) in grouped) {
            result.addAll(nms(classBoxes))
        }
        return result
    }

    private fun nms(boxes: List<Detection>): List<Detection> {
        val sorted = boxes.sortedByDescending { it.confidence }
        val kept = mutableListOf<Detection>()

        for (box in sorted) {
            if (kept.none { computeIoU(box, it) > IOU_THRESHOLD }) {
                kept.add(box)
            }
        }
        return kept
    }

    private fun computeIoU(a: Detection, b: Detection): Float {
        val interX1 = maxOf(a.x1, b.x1)
        val interY1 = maxOf(a.y1, b.y1)
        val interX2 = minOf(a.x2, b.x2)
        val interY2 = minOf(a.y2, b.y2)
        if (interX2 <= interX1 || interY2 <= interY1) return 0f
        val interArea = (interX2 - interX1) * (interY2 - interY1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        return interArea / (areaA + areaB - interArea)
    }
}
