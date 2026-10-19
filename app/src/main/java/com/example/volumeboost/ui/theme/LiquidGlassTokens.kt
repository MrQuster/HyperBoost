package com.example.volumeboost.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class GlassTokens(
    val isDark: Boolean = true,
    val isAmoled: Boolean = false,
    val baseBackground: Color = Color(0xFF060914),
    val cardBackingColor: Color = Color(0xC40A1022),
    val navBarBackingColor: Color = Color(0xF0080D1C),
    val accentColor: Color = Color(0xFF00F5D4),
    val secondaryAccent: Color = Color(0xFF00B4D8),
    val warningColor: Color = Color(0xFFFFBE0B),
    val dangerColor: Color = Color(0xFFFF4D6D),
    val textPrimary: Color = Color.White,
    val textSecondary: Color = Color(0xE0E2E8F0),
    val cardCornerRadius: Dp = 26.dp,
    val pillCornerRadius: Dp = 30.dp,
    val borderStrokeWidth: Dp = 1.2.dp,
    val glassSurfaceAlpha: Float = 0.18f,
    val shadowElevation: Dp = 16.dp,
    val blurRadius: Dp = 64.dp,
    val parallaxOffset: Offset = Offset.Zero,
    val powerSaveMode: Boolean = false,
    // Сетка отступов 4 / 8 / 12 / 16 / 24dp
    val spacingTiny: Dp = 4.dp,
    val spacingSmall: Dp = 8.dp,
    val spacingMedium: Dp = 12.dp,
    val spacingLarge: Dp = 16.dp,
    val spacingExtraLarge: Dp = 24.dp,
    val minTouchTarget: Dp = 48.dp,
    val screenBottomPadding: Dp = 118.dp,
    // Единая типографическая шкала
    val titleLargeSize: TextUnit = 22.sp,
    val sectionHeaderSize: TextUnit = 15.sp,
    val bodyPrimarySize: TextUnit = 14.sp,
    val bodySecondarySize: TextUnit = 12.sp
)

val LocalGlassTokens = staticCompositionLocalOf { GlassTokens() }

object AccentPalettes {
    val PresetColors = listOf(
        0xFF00F5D4L to "Cyber Mint",
        0xFF00B4D8L to "Liquid Cyan",
        0xFF9B5DE5L to "Hyper Violet",
        0xFFF72585L to "Neon Rose",
        0xFFFF9E00L to "Solar Amber",
        0xFF38B000L to "Emerald Pulse"
    )
}
