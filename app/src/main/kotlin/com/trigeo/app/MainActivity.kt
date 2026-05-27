package com.trigeo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trigeo.app.ui.TrigeoRoot
import com.trigeo.app.ui.theme.TrigeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrigeoTheme {
                TrigeoRoot()
            }
        }
    }
}
