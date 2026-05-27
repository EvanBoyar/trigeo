package com.trigeo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandLight = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color.White,
    secondary = Color(0xFFC58B2A),
    background = Color(0xFFF5F2EA),
    surface = Color(0xFFFFFFFF),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF8AB4D8),
    onPrimary = Color(0xFF0F1B2D),
    secondary = Color(0xFFF2C94C),
    background = Color(0xFF0F1B2D),
    surface = Color(0xFF152538),
)

@Composable
fun TrigeoTheme(
    useDark: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        useDark -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
