package com.hdvideoplay.media.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hdvideoplay.media.R

/**
 * Inter bundled under res/font. Fallback chain ends at SansSerif via Compose
 * if a weight file is missing on device.
 */
val VideoPlayFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
