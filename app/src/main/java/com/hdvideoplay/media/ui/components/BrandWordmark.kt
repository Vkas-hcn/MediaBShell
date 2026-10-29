package com.hdvideoplay.media.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.PrimaryText
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily

/** Centered “Video” (white) + “Play” (gold) wordmark — Home / Channel header. */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    fontSizeSp: Int = 16,
) {
    val wordmark = stringResource(R.string.brand_wordmark)
    val playIdx = wordmark.indexOf("Play")
    val annotated = buildAnnotatedString {
        if (playIdx > 0) {
            withStyle(
                SpanStyle(
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = VideoPlayFontFamily,
                    fontSize = fontSizeSp.sp,
                ),
            ) {
                append(wordmark.substring(0, playIdx))
            }
            withStyle(
                SpanStyle(
                    color = BrandGold,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = VideoPlayFontFamily,
                    fontSize = fontSizeSp.sp,
                ),
            ) {
                append(wordmark.substring(playIdx))
            }
        } else {
            withStyle(
                SpanStyle(
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = VideoPlayFontFamily,
                    fontSize = fontSizeSp.sp,
                ),
            ) {
                append(wordmark)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = annotated)
    }
}
