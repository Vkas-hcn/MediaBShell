package com.hdvideoplay.media.ui.recall

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.hdvideoplay.media.R
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily
import kotlin.math.min

private val ScrimColor = Color(0x99000000)
private val CardColor = Color(0xFF0D1520)
private val ButtonLabelColor = Color(0xFF001C54)
private val CardShape = RoundedCornerShape(16.dp)
private val ButtonShape = RoundedCornerShape(12.dp)
private val CardWidth = 312.dp
private val CardHeight = 471.dp

/**
 * Lanhu recall popup. The card artwork is a designer-exported slice that
 * already contains the title, description and Upgrade word art (the source
 * text layers sit below the bitmap in the design z-order). Only the close
 * icon and CTA are native.
 */
@Composable
internal fun RecallPopup(
    downloadUrl: String,
    onDismiss: () -> Unit,
    onUpgrade: (String) -> Unit,
) {
    val closeLabel = stringResource(R.string.recall_close)
    // recall_show：弹窗真正展示成功后上报（每次 Visible 状态进入组合 = 一次展示）。
    LaunchedEffect(Unit) { TrackEvents.recallShow() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
    ) {
        DialogWindowConfig()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor),
        ) {
            val scale = min(
                1f,
                min(
                    (maxWidth.value - 32f) / 312f,
                    (maxHeight.value - 32f) / 471f,
                ),
            ).coerceAtLeast(0.1f)

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = CardWidth, height = CardHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    }
                    .recallShadow(CardShape)
                    .clip(CardShape)
                    .background(CardColor),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_recall_popup_bg),
                    contentDescription = stringResource(R.string.recall_message),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(48.dp)
                        .semantics { contentDescription = closeLabel }
                        .clickable(
                            onClickLabel = closeLabel,
                            role = Role.Button,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_recall_close),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(ButtonShape)
                        .background(BrandGold)
                        .clickable(
                            onClickLabel = stringResource(R.string.recall_upgrade_now),
                            role = Role.Button,
                        ) { onUpgrade(downloadUrl) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_recall_download),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.recall_upgrade_now),
                        color = ButtonLabelColor,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = VideoPlayFontFamily,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun DialogWindowConfig() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.apply {
            // Our own full-screen scrim replaces the platform dim, otherwise
            // the card would sit on a double-darkened background.
            setDimAmount(0f)
            addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            WindowCompat.getInsetsController(this, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
}

private fun Modifier.recallShadow(shape: Shape): Modifier =
    if (Build.VERSION.SDK_INT >= 28) {
        shadow(
            elevation = 8.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.White.copy(alpha = 0.16f),
            spotColor = Color.White.copy(alpha = 0.16f),
        )
    } else {
        this
    }
