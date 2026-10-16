package com.z.grow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green10 = Color(0xFF002106)
val Green20 = Color(0xFF00390F)
val Green30 = Color(0xFF1B5E20)
val Green40 = Color(0xFF2E7D32)
val Green80 = Color(0xFFA5D6A7)
val Green90 = Color(0xFFC8E6C9)
val Green95 = Color(0xFFE8F5E9)
val Lime40 = Color(0xFF558B2F)
val Lime80 = Color(0xFFC5E1A5)

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Lime40,
    onSecondary = Color.White,
    secondaryContainer = Lime80,
    background = Green95,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Lime80,
    background = Color(0xFF0F1A10),
    surface = Color(0xFF162117),
)

@Composable
fun GrowTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
