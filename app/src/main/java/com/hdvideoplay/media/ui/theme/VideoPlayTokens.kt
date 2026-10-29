package com.hdvideoplay.media.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Shared Lanhu Video Play tokens.
 * Mirrors Theme.kt public tokens (BrandGold / PageBg / TabBarBg / MutedText).
 * Employee2 screens may use either this object or the Theme.kt top-level vals.
 */
object VideoPlayTokens {
    val AccentGold: Color get() = BrandGold
    val PageBg: Color get() = com.hdvideoplay.media.ui.theme.PageBg
    val TabBarBg: Color get() = com.hdvideoplay.media.ui.theme.TabBarBg
    val PrimaryText: Color get() = com.hdvideoplay.media.ui.theme.PrimaryText
    val PosterTitle: Color get() = com.hdvideoplay.media.ui.theme.PosterTitle
    val Muted: Color get() = MutedText
    val SurfaceElevated: Color get() = com.hdvideoplay.media.ui.theme.SurfaceElevated
    val ChipBg: Color get() = com.hdvideoplay.media.ui.theme.ChipBg
    val ChipBorder: Color get() = com.hdvideoplay.media.ui.theme.ChipBorder
    val ProgressTrack = Color(0x66FFFFFF)
    val SectionCard = Color(0xFF0E1219)
    /** Progress track on splash: #7C8CAE @ 32% */
    val SplashTrack: Color get() = SplashProgressTrack
}

/** Poster grid / rail sizing from Lanhu brief. */
object VideoPlayDimens {
    const val PosterWidth = 101
    const val PosterHeight = 160
    const val PosterRadius = 12
    const val GridInset = 16
    const val GridGap = 13
    /** Vertical gap between home modules (Lanhu: 16dp). */
    const val SectionGap = 16
    const val SectionBarWidth = 4
    const val SectionBarHeight = 16
    const val TabBarHeight = 66
    const val NavIconSize = 24
    const val SplashLogoWidth = 140
    const val SplashLogoHeight = 130
}
