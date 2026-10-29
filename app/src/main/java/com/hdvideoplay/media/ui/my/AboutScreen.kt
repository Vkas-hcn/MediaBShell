package com.hdvideoplay.media.ui.my

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ui.theme.VideoPlayTokens

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = remember { AboutConfigLoader.load(context) }
    val usable = remember(config) { AboutConfigLoader.isUsable(config) }
    val webView = remember { mutableStateOf<WebView?>(null) }

    fun goBack() {
        val view = webView.value
        if (usable && view != null && view.canGoBack()) view.goBack() else onBack()
    }

    BackHandler(enabled = usable) { goBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(VideoPlayTokens.PageBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                color = VideoPlayTokens.PrimaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .clickable(onClick = ::goBack)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.my_about_title),
                color = VideoPlayTokens.PrimaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        if (usable) {
            // Requirement: H5 occupies the upper half of the content area.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.5f),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val uri: Uri = request?.url ?: return false
                                    val scheme = uri.scheme?.lowercase()
                                    if (scheme == "http" || scheme == "https") return false
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                    return true
                                }
                            }
                            loadUrl(config.url)
                            webView.value = this
                        }
                    },
                )
            }
            Spacer(Modifier.weight(0.5f))
        } else {
            // Switch off: blank About body, and no WebView is ever instantiated.
            Spacer(Modifier.weight(1f))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.value?.stopLoading()
            webView.value?.destroy()
            webView.value = null
        }
    }
}
