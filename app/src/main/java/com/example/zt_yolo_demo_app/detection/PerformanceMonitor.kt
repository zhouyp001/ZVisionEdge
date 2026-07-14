package com.example.zt_yolo_demo_app.detection

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import java.io.File
import java.util.LinkedList

data class PerfStats(
    val frameCount: Int,
    val fps: Float,
    val inferenceMs: Int,
    val cpuPercent: Int,
    val systemMemPercent: Int
)

class PerformanceMonitor(private val context: Context) {

    private var frameCount = 0
    private val recentTimestamps = LinkedList<Long>()
    private var lastInferenceMs = 0

    // Process CPU tracking via /proc/self/stat
    private var lastProcessCpuTime = 0L
    private var lastCpuSampleTime = 0L

    fun recordFrame(inferenceTimeMs: Long) {
        frameCount++
        lastInferenceMs = inferenceTimeMs.toInt()
        val now = SystemClock.elapsedRealtime()
        recentTimestamps.add(now)
        while (recentTimestamps.size > 10) {
            recentTimestamps.removeFirst()
        }
    }

    fun getStats(): PerfStats {
        val fps = if (recentTimestamps.size >= 2) {
            val span = recentTimestamps.last() - recentTimestamps.first()
            if (span > 0) (recentTimestamps.size - 1).toFloat() / span * 1000f else 0f
        } else 0f

        // Process CPU: read /proc/self/stat for utime + stime
        val cpuPercent = readProcessCpuPercent()

        // System memory
        val systemMemPercent = readSystemMemoryPercent()

        return PerfStats(frameCount, fps, lastInferenceMs, cpuPercent, systemMemPercent)
    }

    private fun readProcessCpuPercent(): Int {
        return try {
            val stat = File("/proc/self/stat").readText()
            val afterParen = stat.substringAfter(") ")
            val fields = afterParen.split(" ")
            // fields[0]=state, fields[11]=utime, fields[12]=stime
            if (fields.size >= 13) {
                val utime = fields[11].toLong()
                val stime = fields[12].toLong()
                val cpuTime = utime + stime // in jiffies (typically 10ms each)

                val wallNow = SystemClock.elapsedRealtime()
                if (lastCpuSampleTime > 0) {
                    val cpuDelta = cpuTime - lastProcessCpuTime
                    val wallDelta = wallNow - lastCpuSampleTime
                    lastProcessCpuTime = cpuTime
                    lastCpuSampleTime = wallNow
                    if (wallDelta > 0) {
                        // jiffies to ms: cpuTime * 10ms, then percentage of wall time
                        ((cpuDelta * 1000 / wallDelta).toInt()).coerceIn(0, 100)
                    } else 0
                } else {
                    lastProcessCpuTime = cpuTime
                    lastCpuSampleTime = wallNow
                    0
                }
            } else 0
        } catch (_: Exception) {
            0
        }
    }

    private fun readSystemMemoryPercent(): Int {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0) {
                val used = memInfo.totalMem - memInfo.availMem
                ((used * 100) / memInfo.totalMem).toInt()
            } else 0
        } catch (_: Exception) {
            0
        }
    }
}
