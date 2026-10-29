package com.hdvideoplay.media.ui.my

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ui.theme.VideoPlayTokens

@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    SimpleShellScreen(
        title = stringResource(R.string.my_favorites_title),
        body = stringResource(R.string.my_favorites_empty),
        onBack = onBack,
    )
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    SimpleShellScreen(
        title = stringResource(R.string.my_downloads_title),
        body = stringResource(R.string.my_downloads_empty),
        onBack = onBack,
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    SimpleShellScreen(
        title = stringResource(R.string.my_settings_title),
        body = stringResource(R.string.my_settings_body),
        onBack = onBack,
    )
}

@Composable
private fun SimpleShellScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(VideoPlayTokens.PageBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                color = VideoPlayTokens.PrimaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                text = title,
                color = VideoPlayTokens.PrimaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = body,
                color = VideoPlayTokens.Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}
