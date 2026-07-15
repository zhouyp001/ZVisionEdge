package com.zhouyp.visionedge.detection

import android.content.Context

data class AppConfig(val frameSkip: Int)

object ConfigLoader {
    fun load(context: Context): AppConfig {
        var frameSkip = 2 // default
        try {
            context.assets.open("config.yml").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                    val parts = trimmed.split(":", limit = 2)
                    if (parts.size != 2) return@forEachLine
                    when (parts[0].trim()) {
                        "frame_skip" -> frameSkip = parts[1].trim().toIntOrNull() ?: frameSkip
                    }
                }
            }
        } catch (_: Exception) {
            // use defaults
        }
        return AppConfig(frameSkip)
    }
}
