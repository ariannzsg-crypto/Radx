package com.radx.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RadxColorScheme = darkColorScheme(
    primary = RadxCyan,
    onPrimary = Color(0xFF04222B),
    primaryContainer = Color(0xFF0C3B47),
    onPrimaryContainer = RadxCyanLight,
    secondary = RadxBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF14304F),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = RadxCyanLight,
    background = RadxBackground,
    onBackground = RadxTextPrimary,
    surface = RadxSurface,
    onSurface = RadxTextPrimary,
    surfaceVariant = RadxSurfaceElevated,
    onSurfaceVariant = RadxTextSecondary,
    outline = RadxOutline,
    outlineVariant = RadxOutline,
    error = RadxError,
    onError = Color(0xFF3A0A0A),
)

private val RadxTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val RadxShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun RadxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RadxColorScheme,
        typography = RadxTypography,
        shapes = RadxShapes,
        content = content,
    )
}
