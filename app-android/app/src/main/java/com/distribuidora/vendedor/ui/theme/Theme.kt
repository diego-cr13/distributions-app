package com.distribuidora.vendedor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    secondary = Color(0xFF4A6572),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
)

/** Single light theme for the whole app; no dark mode in the MVP. */
@Composable
fun VendorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
