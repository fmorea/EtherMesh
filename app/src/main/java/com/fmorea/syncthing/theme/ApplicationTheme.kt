package com.fmorea.syncthing.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.fmorea.syncthing.util.LocalActivity
import com.fmorea.syncthing.util.LocalResources

// Holo Colors (Light)
private val HoloLightGray = Color(0xFFEEEEEE)
private val HoloCyan = Color(0xFF0099CC)
private val HoloCharcoal = Color(0xFF222222)
private val HoloWhite = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = HoloCyan,
    onPrimary = HoloWhite,
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF006064),
    
    secondary = HoloCharcoal,
    onSecondary = HoloWhite,
    secondaryContainer = HoloLightGray,
    onSecondaryContainer = Color.Black,
    
    background = HoloLightGray,
    surface = HoloWhite,
    onBackground = Color.Black,
    onSurface = Color.Black,
    
    surfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF757575)
)

private val DarkColorScheme = LightColorScheme // Force light theme as requested

@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme

    val activity = context.findActivity()
    val resources = context.resources

    CompositionLocalProvider(
        LocalActivity provides activity,
        LocalResources provides resources
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

private fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("Context does not contain an Activity")
}
