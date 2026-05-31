package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
  dspTheme: DspTheme = DspThemeSelector.CyberObsidian,
  content: @Composable () -> Unit,
) {
  val colorScheme = darkColorScheme(
    primary = dspTheme.primaryAccent,
    secondary = dspTheme.secondaryAccent,
    background = dspTheme.background,
    surface = dspTheme.surface,
    onBackground = dspTheme.onSurface,
    onSurface = dspTheme.onSurface,
    surfaceVariant = dspTheme.trackBackground,
    onSurfaceVariant = dspTheme.onSurface.copy(alpha = 0.7f),
    outline = if (dspTheme.themeName == "Elegant Dark") Color(0xFF24272A) else dspTheme.primaryAccent.copy(alpha = 0.3f),
    primaryContainer = dspTheme.glowColor,
    onPrimaryContainer = dspTheme.onSurface
  )

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
