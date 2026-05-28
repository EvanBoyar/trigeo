package com.trigeo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trigeo.app.sensors.CompassAccuracy
import com.trigeo.app.sensors.DebugCompassOverride
import com.trigeo.app.ui.TrigeoRoot
import com.trigeo.app.ui.theme.TrigeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDebugCompassOverrides(intent)
        enableEdgeToEdge()
        setContent {
            TrigeoTheme {
                TrigeoRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDebugCompassOverrides(intent)
    }

    private fun applyDebugCompassOverrides(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        if (intent.hasExtra("force_no_compass")) {
            DebugCompassOverride.forceNoCompass = intent.getBooleanExtra("force_no_compass", false)
        }
        if (intent.hasExtra("force_compass_accuracy")) {
            val raw = intent.getStringExtra("force_compass_accuracy")
            DebugCompassOverride.forcedAccuracy = raw?.let { name ->
                CompassAccuracy.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
        }
    }
}
