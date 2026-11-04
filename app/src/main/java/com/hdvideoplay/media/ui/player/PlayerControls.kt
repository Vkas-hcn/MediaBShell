package com.hdvideoplay.media.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ui.theme.VideoPlayTokens
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

data class PlayerControlsState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val subtitleOn: Boolean = false,
    val locked: Boolean = false,
    /** Current clarity/quality label (Clarity chip). */
    val autoLabel: String = "AUTO",
    /** Current playback-speed label, e.g. "1.0X". */
    val speedLabel: String = "1.0X",
    /** Episode numbers for in-chrome Episodes sheet; empty hides entry. */
    val episodeNumbers: List<Int> = emptyList(),
    val selectedEpisode: Int = 0,
    val subtitleText: String? = null,
    /** 拖动进度时隐藏中央三键（快退/播放/快进）。 */
    val isScrubbing: Boolean = false,
)

/**
 * Shared player chrome: tap surface toggles visibility; idle auto-hides.
 * Accent gold on the thin progress track (+ Tab underline elsewhere).
 * V8: primary Play/Pause is white line icon (no red fill); portrait hides
 * Clarity/Speed/Episodes text chips; landscape uses corner mini icons.
 */
@Composable
fun PlayerControlsHost(
    landscape: Boolean,
    state: PlayerControlsState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack15: () -> Unit,
    onSeekForward15: () -> Unit,
    onScrubTo: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    enableScreenSeek: Boolean = false,
    onToggleCc: () -> Unit = {},
    /** Clarity: cycle quality / line (existing AUTO behavior). */
    onAuto: () -> Unit = {},
    onClarity: () -> Unit = onAuto,
    /**
     * Landscape Settings (Clarity gear): open Play Source / Quality panel.
     * Defaults to [onClarity] so callers that only pass onClarity still work.
     */
    onOpenSourcePanel: () -> Unit = onClarity,
    /** Apply ExoPlayer playback speed. */
    onPlaybackSpeed: (Float) -> Unit = {},
    /** Episode pick from in-chrome sheet. */
    onSelectEpisode: (Int) -> Unit = {},
    onFullscreen: () -> Unit = {},
    onExitFullscreen: () -> Unit = {},
    onToggleLock: () -> Unit = {},
    idleHideMs: Long = 3200L,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var hideToken by remember { mutableLongStateOf(0L) }
    var menu by remember { mutableStateOf<ChromeMenu?>(null) }
    var speedIndex by remember { mutableStateOf(SPEED_STEPS.indexOfFirst { it == 1.0f }.coerceAtLeast(0)) }

    fun bumpVisible() {
        controlsVisible = true
        hideToken++
        menu = null
    }

    fun cycleSpeed() {
        val next = (speedIndex + 1) % SPEED_STEPS.size
        speedIndex = next
        onPlaybackSpeed(SPEED_STEPS[next])
        controlsVisible = true
        hideToken++
        menu = null
    }

    LaunchedEffect(hideToken, state.isPlaying, state.locked) {
        if (!controlsVisible) return@LaunchedEffect
        if (state.locked) return@LaunchedEffect
        delay(idleHideMs)
        if (state.isPlaying) {
            controlsVisible = false
        }
    }

    // System back closes Episodes sheet before leaving player (return_ad).
    if (menu == ChromeMenu.Episodes) {
        BackHandler {
            menu = null
            bumpVisible()
        }
    }

    val latestLocked = rememberUpdatedState(state.locked)
    val latestPosition = rememberUpdatedState(state.positionMs)
    val latestDuration = rememberUpdatedState(state.durationMs)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (latestLocked.value) {
                        controlsVisible = !controlsVisible
                        hideToken++
                    } else {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) hideToken++
                    }
                })
            }
            .then(
                if (enableScreenSeek) {
                    Modifier.pointerInput(Unit) {
                        var totalDragPx = 0f
                        var startPositionMs = 0L
                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDragPx = 0f
                                startPositionMs = latestPosition.value
                            },
                            onDragEnd = { onScrubEnd() },
                            onDragCancel = { onScrubEnd() },
                        ) { change, dragAmount ->
                            change.consume()
                            totalDragPx += dragAmount
                            val widthPx = size.width.toFloat()
                            val duration = latestDuration.value
                            if (widthPx > 0f && duration > 0L) {
                                val target = startPositionMs + (totalDragPx / widthPx * duration).toLong()
                                onScrubTo(target)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        val sub = state.subtitleText
        if (!sub.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (landscape) 72.dp else 56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(text = sub, color = Color.White, fontSize = 14.sp)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (landscape) {
                LandscapeControls(
                    state = state,
                    speedLabel = SPEED_LABELS[speedIndex],
                    onBack = onBack,
                    onTogglePlay = { bumpVisible(); onTogglePlay() },
                    onSeekBack15 = { bumpVisible(); onSeekBack15() },
                    onSeekForward15 = { bumpVisible(); onSeekForward15() },
                    onScrubTo = { bumpVisible(); onScrubTo(it) },
                    onScrubEnd = { onScrubEnd() },
                    onToggleCc = { bumpVisible(); onToggleCc() },
                    onClarity = { bumpVisible(); onOpenSourcePanel() },
                    onSpeed = { cycleSpeed() },
                    onEpisodes = {
                        controlsVisible = true
                        hideToken++
                        menu = if (state.episodeNumbers.isNotEmpty()) ChromeMenu.Episodes else null
                    },
                    onExitFullscreen = onExitFullscreen,
                    onToggleLock = { bumpVisible(); onToggleLock() },
                )
            } else {
                PortraitControls(
                    state = state,
                    onBack = onBack,
                    onTogglePlay = { bumpVisible(); onTogglePlay() },
                    onSeekBack15 = { bumpVisible(); onSeekBack15() },
                    onSeekForward15 = { bumpVisible(); onSeekForward15() },
                    onScrubTo = { bumpVisible(); onScrubTo(it) },
                    onScrubEnd = { onScrubEnd() },
                    onToggleCc = { bumpVisible(); onToggleCc() },
                    onFullscreen = onFullscreen,
                )
            }

            // In-chrome Episodes sheet (does not touch WatchMeta).
            if (menu == ChromeMenu.Episodes && state.episodeNumbers.isNotEmpty()) {
                EpisodesSheet(
                    numbers = state.episodeNumbers,
                    selected = state.selectedEpisode,
                    onPick = { ep ->
                        menu = null
                        onSelectEpisode(ep)
                        bumpVisible()
                    },
                    onDismiss = { menu = null; bumpVisible() },
                )
            }
        }

        if (state.locked && controlsVisible) {
            IconButton(
                onClick = { bumpVisible(); onToggleLock() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(36.dp)
                    .background(Color(0x66000000), CircleShape),
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Unlock",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
    ) {
        content()
    }
}

@Composable
private fun CcButton(subtitleOn: Boolean, onClick: () -> Unit) {
    CompactIconButton(onClick = onClick, contentDescription = "CC") {
        Icon(
            painter = painterResource(R.drawable.ic_cc_outline),
            contentDescription = "CC",
            tint = if (subtitleOn) Color.White else Color(0xFFB3B3B3),
            modifier = Modifier.size(width = 22.dp, height = 16.dp),
        )
    }
}

@Composable
private fun PortraitControls(
    state: PlayerControlsState,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack15: () -> Unit,
    onSeekForward15: () -> Unit,
    onScrubTo: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onToggleCc: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x88000000), Color.Transparent, Color(0xAA000000)),
                    ),
                ),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactIconButton(onClick = onBack, contentDescription = "Back") {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            CcButton(subtitleOn = state.subtitleOn, onClick = onToggleCc)
        }

        if (!state.isScrubbing) {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp), // V8-2: 36–48dp (was 18)
            ) {
                Seek15Button(forward = false, onClick = onSeekBack15)
                PrimaryPlayPause(isPlaying = state.isPlaying, onClick = onTogglePlay)
                Seek15Button(forward = true, onClick = onSeekForward15)
            }
        }

        // V8-3: portrait does NOT render Clarity/Speed/Episodes text capsules
        // (episode pick stays on WatchMeta; clarity/speed via landscape corner).
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            PortraitProgressRow(
                state = state,
                onScrubTo = onScrubTo,
                onScrubEnd = onScrubEnd,
                onTogglePlay = onTogglePlay,
                onFullscreen = onFullscreen,
            )
        }
    }
}

@Composable
private fun LandscapeControls(
    state: PlayerControlsState,
    speedLabel: String,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack15: () -> Unit,
    onSeekForward15: () -> Unit,
    onScrubTo: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onToggleCc: () -> Unit,
    onClarity: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: () -> Unit,
    onExitFullscreen: () -> Unit,
    onToggleLock: () -> Unit,
) {
    if (state.locked) {
        return
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x88000000), Color.Transparent, Color(0xAA000000)),
                    ),
                ),
        )

        // Top: back + title (ellipsis) | CC — CC must stay on-screen (§7.2 B)
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactIconButton(onClick = onBack, contentDescription = "Back") {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = state.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }

        CompactIconButton(
            onClick = onToggleLock,
            contentDescription = "Lock",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
        ) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = "Lock",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }

        if (!state.isScrubbing) {
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp), // V8-2: 36–48dp (was 18)
            ) {
                Seek15Button(forward = false, onClick = onSeekBack15)
                PrimaryPlayPause(isPlaying = state.isPlaying, onClick = onTogglePlay)
                Seek15Button(forward = true, onClick = onSeekForward15)
            }
        }

        // 08-style: progress row (times at ends) + next row MiniPause | exit
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            LandscapeProgressBlock(
                state = state,
                speedLabel = speedLabel,
                onScrubTo = onScrubTo,
                onScrubEnd = onScrubEnd,
                onToggleCc = onToggleCc,
                onSpeed = onSpeed,
                onExitFullscreen = onExitFullscreen,
                onOpenSource = onClarity,
                onEpisodes = onEpisodes,
            )
        }
    }
}

@Composable
private fun PrimaryPlayPause(isPlaying: Boolean, onClick: () -> Unit) {
    // V8-2: no VideoPlayTokens.AccentGold fill; white Play/Pause ~38dp; touch ≥48dp (MiniPlayPause vocabulary)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun Seek15Button(forward: Boolean, onClick: () -> Unit) {
    // Design §B: Column(环 28dp + 「15s」10sp SemiBold 白); touch ≤40dp
    Column(
        modifier = Modifier
            .width(40.dp)
            .height(40.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(
                if (forward) R.drawable.ic_seek_forward_15 else R.drawable.ic_seek_back_15,
            ),
            contentDescription = if (forward) "+15s" else "-15s",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "15s",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun AutoChip(label: String, onClick: () -> Unit) {
    // Lanhu: clarity badge = #1F1F1F + 1dp white border + white text (no teal/lilac)
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .height(22.dp)
            .clip(shape)
            .background(Color(0xFF1F1F1F))
            .border(1.dp, Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MiniPlayPause(isPlaying: Boolean, onClick: () -> Unit) {
    // Bottom aux Pause/Play — white, 16–18dp, NOT red (§1.4 / §7.1)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun PortraitProgressRow(
    state: PlayerControlsState,
    onScrubTo: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onTogglePlay: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.positionMs.coerceIn(0L, duration.coerceAtLeast(1L))
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MiniPlayPause(isPlaying = state.isPlaying, onClick = onTogglePlay)
        // design §B: 时码竖 10sp；widthIn 不裁（禁死宽 48）
        Text(
            formatTime(position),
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        ThinSeekSlider(
            progress = progress,
            onSeekFraction = { frac ->
                if (duration > 0) onScrubTo((frac * duration).toLong())
            },
            onSeekFinished = { onScrubEnd() },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        Text(
            formatTime(duration),
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        CompactIconButton(onClick = onFullscreen, contentDescription = "Fullscreen") {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Landscape: slim gold scrubber; bottom-left eye + 1X; bottom-right corners; source via Settings. */
@Composable
private fun LandscapeProgressBlock(
    state: PlayerControlsState,
    speedLabel: String,
    onScrubTo: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onToggleCc: () -> Unit,
    onSpeed: () -> Unit,
    onExitFullscreen: () -> Unit,
    onOpenSource: () -> Unit = {},
    onEpisodes: () -> Unit = {},
) {
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.positionMs.coerceIn(0L, duration.coerceAtLeast(1L))
    val progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            formatTime(position),
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        ThinSeekSlider(
            progress = progress,
            onSeekFraction = { frac ->
                if (duration > 0) onScrubTo((frac * duration).toLong())
            },
            onSeekFinished = { onScrubEnd() },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
        )
        Text(
            formatTime(duration),
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 36.dp),
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Eye / visibility (CC)
        CcButton(subtitleOn = state.subtitleOn, onClick = onToggleCc)
        // 1X speed
        Box(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = 8.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onSpeed,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = compactSpeedLabel(speedLabel).uppercase(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        // Play Source sheet (landscape)
        CompactIconButton(onClick = onOpenSource, contentDescription = "Play Source") {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Play Source",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        if (state.episodeNumbers.isNotEmpty()) {
            CompactIconButton(onClick = onEpisodes, contentDescription = "Episodes") {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Episodes",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        CompactIconButton(onClick = onExitFullscreen, contentDescription = "Exit fullscreen") {
            Icon(
                Icons.Default.FullscreenExit,
                contentDescription = "Exit fullscreen",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Slim white progress track (2dp) + centered white thumb (稿 播放详情). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ThinSeekSlider(
    progress: Float,
    onSeekFraction: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = progress.coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = { onSeekFraction(it.coerceIn(0f, 1f)) },
        onValueChangeFinished = onSeekFinished,
        modifier = modifier.height(20.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = VideoPlayTokens.AccentGold,
            inactiveTrackColor = Color(0x55FFFFFF),
        ),
        thumb = {
            // Match the track's 20dp slot so Material3 centers the thumb on the
            // 2dp line; a 9dp slot is top-aligned and floats above the track.
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(Color.White, CircleShape),
                )
            }
        },
        track = { sliderState ->
            val range = sliderState.valueRange
            val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
            val frac = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0x99FFFFFF))
                        .align(Alignment.Center),
                )
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White)
                        .align(Alignment.CenterStart),
                )
            }
        },
    )
}


private enum class ChromeMenu { Episodes }

private val SPEED_STEPS = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val SPEED_LABELS = listOf("0.75X", "1.0X", "1.25X", "1.5X", "2.0X")


/** V8-3 landscape: 18–20dp white icons / 10–11sp speed; touch 36dp; optional dim corner bg. */
@Composable
private fun LandscapeCornerExtras(
    speedLabel: String,
    showEpisodes: Boolean,
    onClarity: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClarity,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Clarity",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onSpeed,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = compactSpeedLabel(speedLabel),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (showEpisodes) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onEpisodes,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Episodes",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** "1.0X" / "1.25X" → "1x" / "1.25x" for corner speed chip. */
private fun compactSpeedLabel(label: String): String {
    val raw = label.trim().removeSuffix("X").removeSuffix("x").trim()
    val n = raw.toFloatOrNull()
    return when {
        n == null -> label.lowercase()
        n == n.toInt().toFloat() -> "${n.toInt()}x"
        else -> "${raw}x"
    }
}

/** Lanhu-aligned Clarity / Speed / Episodes text entries (white, not teal). */
@Composable
private fun ClaritySpeedEpisodesRow(
    clarityLabel: String,
    speedLabel: String,
    showEpisodes: Boolean,
    onClarity: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
    ) {
        if (!compact) Spacer(Modifier.weight(1f))
        MenuTextChip(
            title = "Clarity",
            value = clarityLabel,
            onClick = onClarity,
        )
        MenuTextChip(
            title = "Speed",
            value = speedLabel,
            onClick = onSpeed,
        )
        if (showEpisodes) {
            MenuTextChip(
                title = "Episodes",
                value = null,
                onClick = onEpisodes,
            )
        }
    }
}

@Composable
private fun MenuTextChip(
    title: String,
    value: String?,
    onClick: () -> Unit,
) {
    // Clarity/Speed/Episodes: gray #1F1F1F + 1dp white border + white text
    val label = if (value.isNullOrBlank()) title else "$title · $value"
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .height(22.dp)
            .clip(shape)
            .background(Color(0xFF1F1F1F))
            .border(1.dp, Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun EpisodesSheet(
    numbers: List<Int>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Color(0xEE1A1A1A))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Episodes",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            val rows = numbers.chunked(6)
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { n ->
                        val sel = n == selected
                        val cellShape = RoundedCornerShape(6.dp)
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 32.dp)
                                .clip(cellShape)
                                // Selected: #2A2A2A + white 1.5dp border (not VideoPlayTokens.AccentGold fill)
                                .background(if (sel) Color(0xFF2A2A2A) else Color(0x33FFFFFF))
                                .then(
                                    if (sel) Modifier.border(1.5.dp, Color.White, cellShape)
                                    else Modifier,
                                )
                                .clickable { onPick(n) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = n.toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatPlayerTime(ms: Long): String = formatTime(ms)

private fun formatTime(ms: Long): String {
    val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
