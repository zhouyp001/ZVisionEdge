package com.example.zt_yolo_demo_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.zt_yolo_demo_app.ui.screen.CameraScreen
import com.example.zt_yolo_demo_app.ui.theme.ZtyolodemoappTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZtyolodemoappTheme {
                CameraScreen()
            }
        }
    }
}
