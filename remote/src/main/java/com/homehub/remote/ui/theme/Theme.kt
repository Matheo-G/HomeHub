package com.homehub.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Palette de la télécommande, alignée sur celle du launcher HomeHub. */
object RemoteColors {
    val Background = Color(0xFF07090E)
    val Surface = Color(0xFF12161F)
    val SurfaceHigh = Color(0xFF1B202B)
    val SurfacePressed = Color(0xFF252B38)
    val Outline = Color(0x1AFFFFFF)
    val Accent = Color(0xFF3D7BFF)
    val AccentSoft = Color(0xFF7FA6FF)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFFF6B7A)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB8FFFFFF)
    val TextTertiary = Color(0x70FFFFFF)
}

private val RemoteTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.12.em),
)

@Composable
fun HomeHubRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RemoteColors.Accent,
            onPrimary = Color.White,
            secondary = RemoteColors.AccentSoft,
            background = RemoteColors.Background,
            onBackground = RemoteColors.TextPrimary,
            surface = RemoteColors.Surface,
            onSurface = RemoteColors.TextPrimary,
            surfaceVariant = RemoteColors.SurfaceHigh,
            onSurfaceVariant = RemoteColors.TextSecondary,
            outline = RemoteColors.Outline,
            error = RemoteColors.Danger,
        ),
        typography = RemoteTypography,
        content = content,
    )
}
