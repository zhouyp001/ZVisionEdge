package com.zhouyp.visionedge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zhouyp.visionedge.ui.screen.CameraScreen
import com.zhouyp.visionedge.ui.theme.VisionEdgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VisionEdgeTheme {
                CameraScreen()
            }
        }
    }
}
