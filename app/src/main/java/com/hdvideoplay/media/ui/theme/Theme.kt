package com.hdvideoplay.media.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Public Lanhu Video Play tokens (employee1) ─────────────────────────────
/** Accent gold — section bars, selected tab, progress, selected chips. */
val BrandGold = Color(0xFFFFB800)
/** Page / splash fill. */
val PageBg = Color(0xFF07090E)
/** Bottom tab bar surface. */
val TabBarBg = Color(0xFF0A0D14)
/** Headings / primary labels. */
val PrimaryText = Color(0xFFFFFFFF)
/** Poster card titles (#E2E8F0). */
val PosterTitle = Color(0xFFE2E8F0)
/** Muted chrome — More, years, inactive tabs. */
val MutedText = Color(0xFF94A3B8)
/** Elevated / chip surfaces. */
val SurfaceElevated = Color(0xFF12151C)
val ChipBg = Color(0xFF1A1F2A)
val ChipBorder = Color(0xFF3A4254)
val PressSurface = Color(0xFF1A1F2A)
/** Splash progress track: #7C8CAE @ 32% opacity. */
val SplashProgressTrack = Color(0x527C8CAE)


private val VideoPlayDarkColors = darkColorScheme(
    primary = BrandGold,
    onPrimary = Color.Black,
    secondary = BrandGold,
    onSecondary = Color.Black,
    tertiary = BrandGold,
    background = PageBg,
    onBackground = PrimaryText,
    surface = PageBg,
    onSurface = PrimaryText,
    surfaceVariant = ChipBg,
    onSurfaceVariant = MutedText,
    outline = ChipBorder,
    error = BrandGold,
    onError = Color.Black,
)

/**
 * Video Play type scale — Inter (bundled). Prefer these over ad-hoc sp.
 * Gold (#FFB800) is reserved for brand / selected chrome.
 */
val VideoPlayTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = PrimaryText,
    ),
    titleLarge = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        color = PrimaryText,
    ),
    titleMedium = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = PrimaryText,
    ),
    titleSmall = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = PrimaryText,
    ),
    bodyLarge = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = PrimaryText,
    ),
    bodyMedium = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = PrimaryText,
    ),
    bodySmall = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = MutedText,
    ),
    labelLarge = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = PrimaryText,
    ),
    labelMedium = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 8.sp,
        lineHeight = 12.sp,
        color = MutedText,
    ),
    labelSmall = TextStyle(
        fontFamily = VideoPlayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = PrimaryText,
    ),
)


@Composable
fun MediaTheme(
    // Forced dark Video Play look — ignore system light theme.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val barColor = PageBg.toArgb()
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = VideoPlayDarkColors,
        typography = VideoPlayTypography,
        content = content,
    )
}
