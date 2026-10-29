package com.hdvideoplay.media.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.api.NetworkModule
import com.hdvideoplay.media.data.dto.PlaySourceDto
import com.hdvideoplay.media.data.dto.SubtitleDto
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.LoadingBox
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.ui.theme.MediaTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation

/**
 * Full-screen Media3 player: multi-line / quality switching,
 * custom HTTP headers, external srt/vtt subtitles, play_report.
 */
class PlayerActivity : ComponentActivity() {

    private val mediaId by lazy { intent.getIntExtra(EXTRA_MEDIA_ID, -1) }
    private val episode by lazy { intent.getIntExtra(EXTRA_EPISODE, 0) }
    private val titleExtra by lazy { intent.getStringExtra(EXTRA_TITLE).orEmpty() }

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModel.factory(application, mediaId, episode)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaTheme {
                val state by viewModel.linksState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is PlayerEvent.Toast ->
                                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                            PlayerEvent.AllFailed ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.all_sources_failed),
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(titleExtra.ifBlank { stringResource(R.string.player_title) })
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        when (val s = state) {
                            UiState.Loading -> LoadingBox()
                            is UiState.Error -> {
                                val msg = when {
                                    s.message == "empty_links" ->
                                        stringResource(R.string.no_play_sources)
                                    mediaId <= 0 ->
                                        stringResource(R.string.invalid_media)
                                    else -> s.message
                                }
                                ErrorBox(msg, onRetry = viewModel::loadLinks)
                            }
                            is UiState.Success -> PlayerContent(
                                ready = s.data,
                                onSelectLine = viewModel::selectLine,
                                onSelectSource = viewModel::selectSource,
                                onSourceReady = viewModel::onSourceReady,
                                onSourceFailed = {
                                    viewModel.onSourceFailed(
                                        getString(R.string.play_failed_toast),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MEDIA_ID = "EXTRA_MEDIA_ID"
        const val EXTRA_EPISODE = "EXTRA_EPISODE"
        const val EXTRA_TITLE = "EXTRA_TITLE"

        fun createIntent(
            context: Context,
            mediaId: Int,
            episode: Int = 0,
            title: String = "",
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_MEDIA_ID, mediaId)
            putExtra(EXTRA_EPISODE, episode)
            putExtra(EXTRA_TITLE, title)
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(
    ready: PlayerReady,
    onSelectLine: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onSourceReady: () -> Unit,
    onSourceFailed: () -> Boolean,
) {
    val context = LocalContext.current
    val source = ready.currentSource
    val onReadyLatest = rememberUpdatedState(onSourceReady)
    val onFailedLatest = rememberUpdatedState(onSourceFailed)

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(ready.lineIndex, ready.sourceIndex, source?.url) {
        val src = source
        if (src == null || src.url.isBlank()) {
            onFailedLatest.value()
            return@LaunchedEffect
        }
        val successOnce = AtomicBoolean(false)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && successOnce.compareAndSet(false, true)) {
                    onReadyLatest.value()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onFailedLatest.value()
            }
        }
        player.addListener(listener)
        try {
            playSource(player, src)
            player.playWhenReady = true
            awaitCancellation()
        } catch (_: Throwable) {
            onFailedLatest.value()
        } finally {
            player.removeListener(listener)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = stringResource(R.string.line),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ready.links.forEachIndexed { index, link ->
                FilterChip(
                    selected = index == ready.lineIndex,
                    onClick = { onSelectLine(index) },
                    label = { Text(link.name.ifBlank { "Line ${index + 1}" }) },
                )
            }
        }

        val line = ready.currentLine
        if (line != null && line.list.size > 1) {
            Text(
                text = stringResource(R.string.quality),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                line.list.forEachIndexed { index, src ->
                    FilterChip(
                        selected = index == ready.sourceIndex,
                        onClick = { onSelectSource(index) },
                        label = {
                            Text(src.quality?.takeIf { it.isNotBlank() } ?: "Source ${index + 1}")
                        },
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun playSource(player: ExoPlayer, source: PlaySourceDto) {
    val headers = source.headers.orEmpty()
    val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.okHttpClient)
        .setDefaultRequestProperties(headers)

    val subtitleConfigs = source.subtitles.mapNotNull { buildSubtitleConfig(it) }

    val mediaItem = MediaItem.Builder()
        .setUri(source.url)
        .setSubtitleConfigurations(subtitleConfigs)
        .build()

    val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
        .createMediaSource(mediaItem)

    player.setMediaSource(mediaSource)
    player.prepare()
}

@OptIn(UnstableApi::class)
private fun buildSubtitleConfig(sub: SubtitleDto): MediaItem.SubtitleConfiguration? {
    val url = sub.actualUrl.trim()
    if (url.isEmpty()) return null
    val mime = when {
        sub.format.equals("vtt", ignoreCase = true) ||
            url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
        sub.format.equals("srt", ignoreCase = true) ||
            url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
        else -> MimeTypes.APPLICATION_SUBRIP
    }
    val lang = sub.lang ?: sub.language ?: "und"
    return MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
        .setMimeType(mime)
        .setLanguage(lang)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
}
