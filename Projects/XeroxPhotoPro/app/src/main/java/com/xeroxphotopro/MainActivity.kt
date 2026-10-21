package com.xeroxphotopro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import com.xeroxphotopro.ui.PhotoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF006D66), secondary = Color(0xFF465E59), background = Color(0xFFF5F7F4), surface = Color.White)) {
                PhotoApp()
            }
        }
    }
}
