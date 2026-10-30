package com.radx.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radx.app.data.AccentColor

/**
 * The colour pair the interface is built around. Glass surfaces, the connect orb, the
 * navigation highlight and the ambient backdrop all read these, so switching the accent
 * in the settings repaints the whole app.
 */
data class RadxAccent(
    val primary: Color,
    val secondary: Color,
    /** Ambient glow behind the content; usually a deeper, less saturated tone. */
    val glow: Color,
    val onPrimary: Color,
) {
    companion object {
        val Default = RadxAccent(
            primary = RadxCyan,
            secondary = RadxBlue,
            glow = Color(0xFF1D4ED8),
            onPrimary = Color(0xFF04222B),
        )

        val Blue = RadxAccent(
            primary = Color(0xFF60A5FA),
            secondary = Color(0xFF6366F1),
            glow = Color(0xFF4338CA),
            onPrimary = Color(0xFF06162E),
        )

        val Violet = RadxAccent(
            primary = Color(0xFFC4B5FD),
            secondary = Color(0xFF8B5CF6),
            glow = Color(0xFF6D28D9),
            onPrimary = Color(0xFF190A33),
        )

        val Emerald = RadxAccent(
            primary = Color(0xFF6EE7B7),
            secondary = Color(0xFF10B981),
            glow = Color(0xFF047857),
            onPrimary = Color(0xFF04241A),
        )
    }
}

fun accentOf(kind: AccentColor): RadxAccent = when (kind) {
    AccentColor.CYAN -> RadxAccent.Default
    AccentColor.BLUE -> RadxAccent.Blue
    AccentColor.VIOLET -> RadxAccent.Violet
    AccentColor.EMERALD -> RadxAccent.Emerald
}

val LocalRadxAccent = staticCompositionLocalOf { RadxAccent.Default }

private fun schemeFor(accent: RadxAccent) = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.secondary.copy(alpha = 0.35f),
    onPrimaryContainer = accent.primary,
    secondary = accent.secondary,
    onSecondary = accent.onPrimary,
    secondaryContainer = accent.glow.copy(alpha = 0.35f),
    onSecondaryContainer = accent.primary,
    tertiary = accent.primary,
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

/**
 * @param dynamicColor take the palette from the wallpaper (Android 12+) instead of the
 * accent chosen in the settings.
 */
@Composable
fun RadxTheme(
    accent: AccentColor = AccentColor.CYAN,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = accentOf(accent)
    val context = LocalContext.current
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        schemeFor(palette)
    }
    CompositionLocalProvider(LocalRadxAccent provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RadxTypography,
            shapes = RadxShapes,
            content = content,
        )
    }
}
