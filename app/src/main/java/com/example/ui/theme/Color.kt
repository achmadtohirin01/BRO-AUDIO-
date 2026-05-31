package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class DspTheme(
    val themeName: String,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val glowColor: Color,
    val trackBackground: Color
)

object DspThemeSelector {
    val ElegantDark = DspTheme(
        themeName = "Elegant Dark",
        background = Color(0xFF0F1113),
        surface = Color(0xFF16181A),
        onSurface = Color(0xFFE2E2E6),
        primaryAccent = Color(0xFFC6FF00), // Sparkling Lime Neon
        secondaryAccent = Color(0xFFD4FF33),
        glowColor = Color(0x26C6FF00),
        trackBackground = Color(0xFF24272A)
    )

    val CyberObsidian = DspTheme(
        themeName = "Cyber Obsidian",
        background = Color(0xFF0C0D0F),
        surface = Color(0xFF14161B),
        onSurface = Color(0xFFE5E7EB),
        primaryAccent = Color(0xFFFF6600), // Vivid Obsidian Orange
        secondaryAccent = Color(0xFFFF944D),
        glowColor = Color(0x26FF6600),
        trackBackground = Color(0xFF21252E)
    )

    val GoldenAmber = DspTheme(
        themeName = "Golden Amber",
        background = Color(0xFF0E0D0A),
        surface = Color(0xFF191611),
        onSurface = Color(0xFFEDE8E0),
        primaryAccent = Color(0xFFE5A93B), // Warm Luxury Gold
        secondaryAccent = Color(0xFFF7D070),
        glowColor = Color(0x26E5A93B),
        trackBackground = Color(0xFF28241D)
    )

    val AtomicLime = DspTheme(
        themeName = "Atomic Lime",
        background = Color(0xFF090C09),
        surface = Color(0xFF131813),
        onSurface = Color(0xFFE5EFE5),
        primaryAccent = Color(0xFF4EE44E), // Sci-fi Matrix Green
        secondaryAccent = Color(0xFF88FF88),
        glowColor = Color(0x264EE44E),
        trackBackground = Color(0xFF202B20)
    )

    val RubyCrimson = DspTheme(
        themeName = "Ruby Crimson",
        background = Color(0xFF0D0A0B),
        surface = Color(0xFF1D1416),
        onSurface = Color(0xFFEFE9EB),
        primaryAccent = Color(0xFFE63946), // Cyberpunk Ruby Red
        secondaryAccent = Color(0xFFF07178),
        glowColor = Color(0x26E63946),
        trackBackground = Color(0xFF2D1F23)
    )

    val ElectricCyan = DspTheme(
        themeName = "Electric Cyan",
        background = Color(0xFF070A0F),
        surface = Color(0xFF0E1420),
        onSurface = Color(0xFFE5EFF9),
        primaryAccent = Color(0xFF00E5FF), // Pure Energy Cyan
        secondaryAccent = Color(0xFF70F3FF),
        glowColor = Color(0x2600E5FF),
        trackBackground = Color(0xFF182335)
    )

    val themes = listOf(ElegantDark, CyberObsidian, GoldenAmber, AtomicLime, RubyCrimson, ElectricCyan)

    fun getTheme(name: String): DspTheme {
        return themes.find { it.themeName.equals(name, ignoreCase = true) } ?: ElegantDark
    }
}
