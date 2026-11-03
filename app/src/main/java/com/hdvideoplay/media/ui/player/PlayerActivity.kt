package com.hdvideoplay.media.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hdvideoplay.media.data.local.WatchHistoryRepository
import com.hdvideoplay.media.data.local.WatchProgress
import com.hdvideoplay.media.util.PosterUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.ui.detail.LinesStatus
import com.hdvideoplay.media.ui.detail.SourceCapsule
import com.hdvideoplay.media.ui.detail.WatchMetaSection
import com.hdvideoplay.media.ui.detail.rememberPortraitVideoHeight
import com.hdvideoplay.media.ui.theme.VideoPlayTokens
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ads.AdLogicV2
import com.hdvideoplay.media.ads.NativeReturnPresenter
import com.hdvideoplay.media.ads.PageDwellTracker
import com.hdvideoplay.media.data.api.MediaCdnHeaders
import com.hdvideoplay.media.data.api.NetworkModule
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.dto.PlaySourceDto
import com.hdvideoplay.media.track.PlaySessionTracker
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.util.CdnRequestHeaders
import com.hdvideoplay.media.data.dto.SubtitleDto
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.ui.theme.MediaTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class PlayerActivity : ComponentActivity() {

    /**
     * Always read extras from the current [intent] — never cache with `by lazy`.
     * With singleTop / task reuse, a stale lazy value stays at the first Intent
     * (often default -1) and surfaces as invalid_media on the second open.
     */
    private fun mediaIdOf(i: Intent = intent): Int = i.getIntExtra(EXTRA_MEDIA_ID, -1)
    private fun episodeOf(i: Intent = intent): Int = i.getIntExtra(EXTRA_EPISODE, 0)
    private fun titleOf(i: Intent = intent): String = i.getStringExtra(EXTRA_TITLE).orEmpty()
    private fun startFullscreenOf(i: Intent = intent): Boolean =
        i.getBooleanExtra(EXTRA_START_FULLSCREEN, false)
    private fun lineIndexOf(i: Intent = intent): Int = i.getIntExtra(EXTRA_LINE_INDEX, 0)
    private fun startPositionOf(i: Intent = intent): Long =
        i.getLongExtra(EXTRA_START_POSITION_MS, 0L)

    private fun fromPositionOf(i: Intent = intent): String? = i.getStringExtra(EXTRA_FROM_POSITION)
    private fun moduleIdOf(i: Intent = intent): Int? =
        i.getIntExtra(EXTRA_MODULE_ID, 0).takeIf { it > 0 }

    /** 播放会话状态机（play_start/progress/end 去重与观看时长累计）。 */
    private lateinit var sessionTracker: PlaySessionTracker

    private var returnAdConsumed = false

    /** Content-list opens Player directly — return_ad must fire here or it never shows. */
    private fun attemptReturnAdThenFinish() {
        if (returnAdConsumed) {
            finish()
            return
        }
        returnAdConsumed = true
        // play_end(back)：用户主动退出播放页（会话未出画则不上报；展示广告不影响）。
        runCatching { sessionTracker.onUserExit() }
        // dwell≥60s → 成对; V2B short dwell → no ad (presentReturn returns false → finish).
        val leave = PageDwellTracker.leaveIfCurrent(PageDwellTracker.PLAYER, "return")
        val longDwell = leave?.longDwell == true
        val started = NativeReturnPresenter.presentReturn(this, longDwell) {
            finish()
        }
        if (!started) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // V2C: enter returns previous page Leave (e.g. Detail→Player) — MUST handle longDwell.
        // Same leave: dwell vs firstWatch emit once, dwell preferred.
        val prevLeave = PageDwellTracker.enter(PageDwellTracker.PLAYER)
        AdLogicV2.resetProgressBoundaries()
        val dwellStarted = NativeReturnPresenter.handleLeave(
            activity = this,
            leave = prevLeave,
            preferDwellOverFirstWatch = true,
        ) { shown ->
            Log.i("PlayerActivity", "V2C prev-page dwell pair complete shown=$shown")
        }
        if (dwellStarted) {
            // Dwell already emitted (or empty-retry scheduled); skip firstWatch for this leave.
            Log.i(
                "PlayerActivity",
                "enter Player after longDwell leave page=${prevLeave?.page} — skip firstWatch",
            )
        } else {
            // No longDwell from previous page → firstWatch path (cold quiet / latch guarded).
            AdLogicV2.onEnterPlayer(this) { shown ->
                Log.i("PlayerActivity", "V2 firstWatch pair complete shown=$shown")
            }
            Log.i("PlayerActivity", "enter Player → AdLogicV2.onEnterPlayer")
        }
        enableEdgeToEdge()
        val mediaId = mediaIdOf()
        val episode = episodeOf()
        val titleExtra = titleOf()
        val initialLineIndex = lineIndexOf()
        val initialStartPositionMs = startPositionOf()
        val fromPosition = fromPositionOf()
        val moduleId = moduleIdOf()
        sessionTracker = PlaySessionTracker(mediaId, episode, reporter = { event, props ->
            TrackEvents.bi(event, props)
        })
        Log.i(
            "PlayerActivity",
            "onCreate mediaId=$mediaId episode=$episode line=$initialLineIndex " +
                "startMs=$initialStartPositionMs title=${titleExtra.take(40)} intent=$intent",
        )
        if (startFullscreenOf() && savedInstanceState == null) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        setContent {
            MediaTheme {
                // System / gesture back → same return_ad path as chrome onBack.
                // Nested BackHandlers (source panel / episodes sheet) take precedence
                // and dismiss overlays first; this only runs when no panel is open.
                BackHandler { attemptReturnAdThenFinish() }
                // Key VM by mediaId+episode so a reused Activity never keeps a stale -1 VM.
                val viewModel: PlayerViewModel = viewModel(
                    key = "player-$mediaId-$episode-$initialLineIndex",
                    factory = PlayerViewModel.factory(
                        application,
                        mediaId,
                        episode,
                        initialLineIndex,
                        trackDetailEvents = fromPosition != null,
                        fromPosition = fromPosition,
                        moduleId = moduleId,
                    ),
                )
                val state by viewModel.linksState.collectAsStateWithLifecycle()
                val linksProbing by viewModel.linksProbing.collectAsStateWithLifecycle()
                val context = LocalContext.current
                // Re-read on every composition so UI error mapping matches current Intent.
                val currentMediaId = mediaIdOf()

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

                // detailMeta independent of links — empty_links still shows WatchMeta (§7).
                val detailMeta by viewModel.detailMeta.collectAsStateWithLifecycle()
                val detailMetaLoading by viewModel.detailMetaLoading.collectAsStateWithLifecycle()
                val currentEpisode by viewModel.currentEpisode.collectAsStateWithLifecycle()
                val openRecommend: (MediaItemDto) -> Unit = { item ->
                    val id = item.localId()
                    if (id > 0) {
                        startActivity(
                            createIntent(
                                context = this@PlayerActivity,
                                mediaId = id,
                                episode = 0,
                                title = item.title,
                                fromPosition = TrackEvents.FROM_RECOMMEND,
                            ),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    when (val s = state) {
                        UiState.Loading -> PlayerLoadingShell(
                            title = titleExtra.ifBlank {
                                detailMeta?.detail?.details?.title
                                    ?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.player_title)
                            },
                            mediaId = currentMediaId,
                            detailMeta = detailMeta,
                            detailMetaLoading = detailMetaLoading,
                            currentEpisode = currentEpisode,
                            onBack = { attemptReturnAdThenFinish() },
                            onSelectSeason = viewModel::selectSeason,
                            onSwitchEpisode = viewModel::switchEpisode,
                            onOpenRecommend = openRecommend,
                        )
                        is UiState.Error -> {
                            // §7: empty_links / no url → stay in Player (NO auto finish);
                            // player-area ErrorBox + WatchMeta below when meta available.
                            val isInvalidMedia = currentMediaId <= 0
                            val isNoSources = s.message == "empty_links" ||
                                s.message == "no_alive_links" ||
                                s.message == "no_available_sources" ||
                                s.message == "invalid_media" ||
                                s.message == "Invalid media id"
                            val msg = when {
                                isInvalidMedia -> stringResource(R.string.invalid_media)
                                isNoSources -> stringResource(R.string.no_available_lines)
                                else -> s.message
                            }
                            if (isInvalidMedia) {
                                ErrorBox(
                                    msg,
                                    onRetry = { },
                                )
                            } else {
                                NoSourcePlayerShell(
                                    message = msg,
                                    title = titleExtra.ifBlank {
                                        detailMeta?.detail?.details?.title
                                            ?.takeIf { it.isNotBlank() }
                                            ?: stringResource(R.string.player_title)
                                    },
                                    mediaId = currentMediaId,
                                    detailMeta = detailMeta,
                                    detailMetaLoading = detailMetaLoading,
                                    currentEpisode = currentEpisode,
                                    onBack = { attemptReturnAdThenFinish() },
                                    onRetry = { if (currentMediaId > 0) viewModel.loadLinks() },
                                    onSelectSeason = viewModel::selectSeason,
                                    onSwitchEpisode = viewModel::switchEpisode,
                                    onOpenRecommend = openRecommend,
                                )
                            }
                        }
                        is UiState.Success -> {
                            PlayerContent(
                                ready = s.data,
                                tracker = sessionTracker,
                                onContentShown = { viewModel.reportDetailShownOnce(s.data) },
                                title = titleExtra.ifBlank {
                                    detailMeta?.detail?.details?.title
                                        ?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.player_title)
                                },
                                mediaId = currentMediaId,
                                detailMeta = detailMeta,
                                detailMetaLoading = detailMetaLoading,
                                currentEpisode = currentEpisode,
                                startPositionMs = initialStartPositionMs,
                                linksProbing = linksProbing,
                                onBack = { attemptReturnAdThenFinish() },
                                onSelectLine = viewModel::selectLine,
                                onSelectSource = viewModel::selectSource,
                                onSelectSeason = viewModel::selectSeason,
                                onSwitchEpisode = viewModel::switchEpisode,
                                onOpenRecommend = openRecommend,
                                onSourceReady = viewModel::onSourceReady,
                                onSourceFailed = { errorMsg ->
                                    viewModel.onSourceFailed(
                                        getString(R.string.play_failed_toast),
                                        errorMsg,
                                    )
                                },
                                onEnterFullscreen = {
                                    requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                },
                                onExitFullscreen = {
                                    requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val mediaId = mediaIdOf(intent)
        Log.i("PlayerActivity", "onNewIntent mediaId=$mediaId intent=$intent — recreate")
        // Force a fresh Activity/VM so extras + ViewModel never stay on the first launch.
        recreate()
    }

    override fun onDestroy() {
        // 兜底：系统回收/任务移除时，若会话已开始且未结束则补 play_end(back)。
        runCatching { sessionTracker.onDestroyFallback() }
        super.onDestroy()
    }

    /** Keep EXTRA_MEDIA_ID when system Up synthesizes the parent DetailActivity. */
    override fun getParentActivityIntent(): Intent? {
        val mid = mediaIdOf()
        return if (mid > 0) {
            com.hdvideoplay.media.ui.detail.DetailActivity.createIntent(
                context = this,
                mediaId = mid,
                episode = episodeOf(),
            )
        } else {
            super.getParentActivityIntent()
        }
    }

    companion object {
        const val EXTRA_MEDIA_ID = "EXTRA_MEDIA_ID"
        const val EXTRA_EPISODE = "EXTRA_EPISODE"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_START_FULLSCREEN = "EXTRA_START_FULLSCREEN"
        const val EXTRA_LINE_INDEX = "EXTRA_LINE_INDEX"
        const val EXTRA_START_POSITION_MS = "EXTRA_START_POSITION_MS"
        const val EXTRA_FROM_POSITION = "EXTRA_FROM_POSITION"
        const val EXTRA_MODULE_ID = "EXTRA_MODULE_ID"

        fun createIntent(
            context: Context,
            mediaId: Int,
            episode: Int = 0,
            title: String = "",
            startFullscreen: Boolean = false,
            lineIndex: Int = 0,
            startPositionMs: Long = 0L,
            fromPosition: String? = null,
            moduleId: Int? = null,
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_MEDIA_ID, mediaId)
            putExtra(EXTRA_EPISODE, episode)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_START_FULLSCREEN, startFullscreen)
            putExtra(EXTRA_LINE_INDEX, lineIndex)
            putExtra(EXTRA_START_POSITION_MS, startPositionMs)
            if (fromPosition != null) putExtra(EXTRA_FROM_POSITION, fromPosition)
            if (moduleId != null) putExtra(EXTRA_MODULE_ID, moduleId)
        }
    }
}


/**
 * Immediate player shell while links/meta load. This replaces the old
 * full-screen LoadingBox: video area and meta skeleton are visible at once,
 * with no page-sized spinner.
 */
@Composable
private fun PlayerLoadingShell(
    title: String,
    mediaId: Int,
    detailMeta: PlayerDetailMeta?,
    detailMetaLoading: Boolean,
    currentEpisode: Int,
    onBack: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val containerH = rememberPortraitVideoHeight()
    val meta = detailMeta?.detail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (landscape) Color.Black else VideoPlayTokens.PageBg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (landscape) Modifier.weight(1f)
                    else Modifier.height(containerH),
                )
                .background(Color.Black),
        ) {
            Box(
                modifier = if (landscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                },
            ) {
                // Video-surface skeleton, not a full-page loading indicator.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0E14)),
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.24f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141824)),
                )
            }
            Text(
                text = "\u2190",
                color = Color.White,
                fontSize = 28.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (!landscape) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll)
                    .background(VideoPlayTokens.PageBg)
                    .padding(bottom = 24.dp),
            ) {
                WatchMetaSection(
                    details = meta?.details,
                    titleFallback = title,
                    currentLineLabel = null,
                    lines = emptyList(),
                    selectedLineIndex = 0,
                    linesStatus = LinesStatus.Checking,
                    seasons = meta?.seasons.orEmpty(),
                    selectedSeasonIndex = detailMeta?.selectedSeasonIndex ?: 0,
                    selectedEpisode = currentEpisode,
                    recommends = meta?.recommends.orEmpty(),
                    onSelectLine = {},
                    onSelectSeason = onSelectSeason,
                    onSelectEpisode = { _, ep, _ -> onSwitchEpisode(ep) },
                    onOpenRecommend = onOpenRecommend,
                    fallbackMediaId = mediaId,
                    loading = detailMetaLoading,
                )
            }
        }
    }
}

/**
 * §7 no-source shell: 16:9 player area shows ErrorBox ("暂无可用线路" + retry);
 * WatchMeta below remains visible when detail meta loaded. Never auto-finish.
 */
@Composable
private fun NoSourcePlayerShell(
    message: String,
    title: String,
    mediaId: Int,
    detailMeta: PlayerDetailMeta?,
    detailMetaLoading: Boolean,
    currentEpisode: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
) {
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val displayTitle = detailMeta?.detail?.details?.title
        ?.takeIf { it.isNotBlank() }
        ?: title

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (landscape) Color.Black else VideoPlayTokens.PageBg),
    ) {
        val containerH = rememberPortraitVideoHeight()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (landscape) Modifier.weight(1f)
                    else Modifier.height(containerH),
                )
                .background(Color.Black),
        ) {
            // V8.1: portrait fixed height container; 16:9 surface centered letterbox; landscape fills.
            Box(
                modifier = if (landscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                },
                contentAlignment = Alignment.Center,
            ) {
                ErrorBox(
                    message = message,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Back affordance — ErrorBox only has retry; product §7 also requires return.
            androidx.compose.material3.TextButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Text(text = "\u2190", color = Color.White)
            }
        }

        if (!landscape) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll)
                    .background(VideoPlayTokens.PageBg)
                    .padding(bottom = 24.dp),
            ) {
                val meta = detailMeta?.detail
                WatchMetaSection(
                    details = meta?.details,
                    titleFallback = displayTitle,
                    currentLineLabel = null,
                    lines = emptyList(),
                    selectedLineIndex = 0,
                    linesStatus = LinesStatus.None,
                    seasons = meta?.seasons.orEmpty(),
                    selectedSeasonIndex = detailMeta?.selectedSeasonIndex ?: 0,
                    selectedEpisode = currentEpisode,
                    recommends = meta?.recommends.orEmpty(),
                    onSelectLine = {},
                    onSelectSeason = onSelectSeason,
                    onSelectEpisode = { _, ep, _ -> onSwitchEpisode(ep) },
                    onOpenRecommend = onOpenRecommend,
                    fallbackMediaId = mediaId,
                    loading = detailMetaLoading,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerContent(
    ready: PlayerReady,
    title: String,
    mediaId: Int,
    detailMeta: PlayerDetailMeta?,
    detailMetaLoading: Boolean,
    currentEpisode: Int,
    startPositionMs: Long = 0L,
    linksProbing: Boolean = false,
    tracker: PlaySessionTracker,
    onContentShown: () -> Unit,
    onBack: () -> Unit,
    onSelectLine: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onOpenRecommend: (MediaItemDto) -> Unit,
    onSourceReady: () -> Unit,
    onSourceFailed: (errorMsg: String?) -> Boolean,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
) {
    val context = LocalContext.current
    val source = ready.currentSource
    val onReadyLatest = rememberUpdatedState(onSourceReady)
    val onFailedLatest = rememberUpdatedState(onSourceFailed)

    // detail_view：详情内容（播放页）首次成功展示时上报一次（VM 内按数据身份去重）。
    LaunchedEffect(Unit) { onContentShown() }

    // 集数切换 → 新播放会话（进度档位/watched/maxPct 重置；旧集静默结束）。
    LaunchedEffect(currentEpisode) { tracker.onEpisodeChanged(currentEpisode) }
    val landscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var subtitleOn by remember { mutableStateOf(false) }
    var hasTextTracks by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    // V9: landscape Line/Quality panel hidden by default; Settings gear opens it.
    var showSourcePanel by remember { mutableStateOf(false) }
    LaunchedEffect(landscape) {
        if (!landscape) showSourcePanel = false
    }

    // V7 watch history — reuse employee-1 WatchHistoryRepository (no second store).
    val historyRepo = remember { WatchHistoryRepository.get(context) }
    val historyScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastHistoryWriteAt by remember { mutableLongStateOf(0L) }
    var initialHistoryWritten by remember { mutableStateOf(false) }
    var playerEverReady by remember { mutableStateOf(false) }
    var initialSeekApplied by remember { mutableStateOf(false) }
    // PlayerContent mounts only when it is about to prepare a source; keep the
    // controls hidden until the first READY frame so the spinner is alone.
    var isSwitchingSource by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    val titleLatest = rememberUpdatedState(title)
    val detailMetaLatest = rememberUpdatedState(detailMeta)
    val episodeLatest = rememberUpdatedState(currentEpisode)
    val mediaIdLatest = rememberUpdatedState(mediaId)

    fun buildWatchProgress(posMs: Long, durMs: Long): WatchProgress {
        val meta = detailMetaLatest.value
        val details = meta?.detail?.details
        val seasons = meta?.detail?.seasons.orEmpty()
        val seasonNum = meta?.let { m ->
            m.detail.seasons.getOrNull(m.selectedSeasonIndex)?.seasonNumber
        }
        val resolvedTitle = details?.title?.takeIf { it.isNotBlank() }
            ?: titleLatest.value
        val poster = PosterUtils.resolvePosterUrl(details?.posterPath)
            ?: details?.posterPath.orEmpty()
        val ep = episodeLatest.value
        return WatchProgress(
            mediaId = mediaIdLatest.value,
            title = resolvedTitle,
            poster = poster,
            progressSec = (posMs.coerceAtLeast(0L) / 1000L),
            durationSec = if (durMs > 0L) durMs / 1000L else 0L,
            mediaType = if (seasons.isNotEmpty()) "tv" else "movie",
            season = seasonNum,
            episode = ep.takeIf { it > 0 },
        )
    }

    fun writeWatchHistory(force: Boolean = false, posMs: Long = -1L, durMs: Long = -1L) {
        val id = mediaIdLatest.value
        if (id <= 0) return
        val now = System.currentTimeMillis()
        // Throttle bursty seek/pause; force bypasses for onStop / initial.
        if (!force && now - lastHistoryWriteAt < 1_500L) return
        lastHistoryWriteAt = now
        val p = if (posMs >= 0L) posMs else player.currentPosition.coerceAtLeast(0L)
        val dRaw = if (durMs >= 0L) durMs else player.duration
        val d = if (dRaw > 0L) dRaw else 0L
        val snapshot = buildWatchProgress(p, d)
        historyScope.launch {
            runCatching { historyRepo.upsert(snapshot) }
        }
    }

    fun applySubtitleTracks(enabled: Boolean) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    fun sourceHasSubtitles(src: PlaySourceDto?): Boolean =
        src?.subtitles?.any { it.actualUrl.isNotBlank() } == true

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                val wasPlaying = isPlaying
                isPlaying = playing
                // Pause → throttled progress upsert.
                if (wasPlaying && !playing) {
                    writeWatchHistory(force = false)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerEverReady = true
                    isBuffering = false
                }
                if (playbackState == Player.STATE_ENDED) {
                    // play_end(complete)：视频正常播放完成（每会话只报一次，防销毁回调重报）。
                    tracker.onCompleted()
                }
            }

            override fun onRenderedFirstFrame() {
                // play_start：视频画面真正开始显示（出画），每会话只报一次。
                tracker.onFirstFrame()
            }

            override fun onTracksChanged(tracks: Tracks) {
                hasTextTracks = tracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_TEXT && group.length > 0
                }
            }
        }
        player.addListener(listener)
        // Start with text tracks off; CC toggle enables them.
        applySubtitleTracks(false)
        onDispose {
            writeWatchHistory(force = true)
            player.removeListener(listener)
            player.release()
            MediaCdnHeaders.clear()
        }
    }

    // Activity onStop → force upsert current progress.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                writeWatchHistory(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial upsert: STATE_READY once, or playing with position ≥ 5s.
    LaunchedEffect(isPlaying, positionMs, playerEverReady, mediaId) {
        if (mediaId <= 0 || initialHistoryWritten) return@LaunchedEffect
        val readyGate = playerEverReady
        val progressGate = isPlaying && positionMs >= 5_000L
        if (readyGate || progressGate) {
            // Read the player directly: the resume seek happens in STATE_READY before
            // the 500ms position ticker updates positionMs, so using the state here
            // would overwrite the resumed history with 00:00.
            val pos = if (readyGate) player.currentPosition.coerceAtLeast(0L) else positionMs
            val dur = player.duration.takeIf { it > 0L } ?: durationMs
            writeWatchHistory(force = true, posMs = pos, durMs = dur)
            initialHistoryWritten = true
        }
    }

    // While playing, upsert every 15s.
    LaunchedEffect(isPlaying, mediaId) {
        if (!isPlaying || mediaId <= 0) return@LaunchedEffect
        while (isActive) {
            delay(15_000L)
            if (!isPlaying) break
            writeWatchHistory(force = true)
        }
    }

    LaunchedEffect(player) {
        val act = context as? android.app.Activity
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            durationMs = if (d > 0) d else 0L
            // 播放会话：真实观看时长累计 + 25/50/75/100 进度档位上报（会话内去重）。
            tracker.onPositionTick(positionMs, durationMs, isPlaying, isBuffering)
            // V2 D: N×10min progress boundaries (not wall-clock hd_play_interval).
            AdLogicV2.onPlaybackProgress(positionMs, act)
            delay(500)
        }
    }

    // headersKey: switching sources with different CDN headers always re-prepares.
    val headersKey = source?.headers?.entries
        ?.sortedBy { it.key.lowercase() }
        ?.joinToString("|") { "${it.key.lowercase()}=${it.value}" }
        .orEmpty()
    LaunchedEffect(ready.lineIndex, ready.sourceIndex, source?.url, headersKey) {
        val lineName = ready.currentLine?.name?.takeIf { it.isNotBlank() }
            ?: "Line ${ready.lineIndex + 1}"
        tracker.onPrepareStarted(lineName)
        val src = source
        if (src == null || src.url.isBlank()) {
            isSwitchingSource = false
            isBuffering = false
            if (!onFailedLatest.value(null)) tracker.onErrorEnd()
            return@LaunchedEffect
        }
        isSwitchingSource = true
        val successOnce = AtomicBoolean(false)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY && successOnce.compareAndSet(false, true)) {
                    isSwitchingSource = false
                    isBuffering = false
                    if (!initialSeekApplied) {
                        initialSeekApplied = true
                        if (startPositionMs > 0L) {
                            val duration = player.duration
                            val target = if (duration > 0L) {
                                startPositionMs.coerceAtMost(duration)
                            } else {
                                startPositionMs
                            }
                            player.seekTo(target)
                            tracker.onSeeked(target, if (duration > 0L) duration else 0L)
                            Log.i("PlayerActivity", "initial resume seek to ${target}ms")
                        }
                    }
                    applySubtitleTracks(subtitleOn)
                    onReadyLatest.value()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isSwitchingSource = false
                isBuffering = false
                Log.e(
                    "PlayerActivity",
                    "playbackError code=${error.errorCode} name=${error.errorCodeName} msg=${error.message} cause=${error.cause}",
                    error,
                )
                // 线路失败：VM 自动推进换线；全部耗尽时结束会话（play_end(error)）。
                if (!onFailedLatest.value("${error.errorCodeName}: ${error.message}")) {
                    tracker.onErrorEnd()
                }
            }
        }
        player.addListener(listener)
        try {
            // Stop previous source before reprepare so line/quality switches are clean.
            player.stop()
            playSource(player, src)
            applySubtitleTracks(subtitleOn)
            player.playWhenReady = true
            awaitCancellation()
        } catch (ce: CancellationException) {
            // Line/quality change or failover cancels this effect — must NOT treat as play failure.
            throw ce
        } catch (t: Throwable) {
            isSwitchingSource = false
            isBuffering = false
            if (!onFailedLatest.value(t.message ?: "prepare_failed")) {
                tracker.onErrorEnd()
            }
        } finally {
            player.removeListener(listener)
        }
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        val dur = player.duration
        val clamped = if (dur > 0) target.coerceAtMost(dur) else target
        player.seekTo(clamped)
        writeWatchHistory(force = false, posMs = clamped, durMs = if (dur > 0) dur else 0L)
        tracker.onSeeked(clamped, if (dur > 0) dur else 0L)
        AdLogicV2.onPlaybackProgress(clamped, context as? android.app.Activity)
    }

    val autoLabel = source?.quality?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.auto_quality)

    val displayTitle = detailMeta?.detail?.details?.title
        ?.takeIf { it.isNotBlank() }
        ?: title

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (landscape) Color.Black else VideoPlayTokens.PageBg),
    ) {
        val containerH = rememberPortraitVideoHeight()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (landscape) Modifier.weight(1f)
                    else Modifier.height(containerH),
                )
                .background(Color.Black),
        ) {
            // V8.1: portrait fixed height=max(W*9/16, H/3); video 16:9 centered letterbox.
            // Landscape still weight(1f) fill. Controls host fills the fixed-height container.
            Box(
                modifier = if (landscape) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                },
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false // custom Compose overlays (§9–10)
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
                if (isSwitchingSource || isBuffering || linksProbing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            val episodeNumbers = detailMeta
                ?.let { meta ->
                    meta.detail.seasons
                        .getOrNull(meta.selectedSeasonIndex)
                        ?.episodes
                        ?.map { it.episodeNumber }
                        .orEmpty()
                }
                .orEmpty()
            val showPlayerControls =
                playerEverReady && !isSwitchingSource && !isBuffering && !linksProbing
            if (showPlayerControls) {
                PlayerControlsHost(
                landscape = landscape,
                state = PlayerControlsState(
                    title = displayTitle,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    subtitleOn = subtitleOn,
                    locked = locked,
                    autoLabel = autoLabel,
                    episodeNumbers = episodeNumbers,
                    selectedEpisode = currentEpisode,
                    subtitleText = null,
                ),
                onBack = onBack,
                onTogglePlay = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeekBack15 = { seekBy(-15_000L) },
                onSeekForward15 = { seekBy(15_000L) },
                onSeek = {
                    player.seekTo(it)
                    writeWatchHistory(force = false, posMs = it)
                    tracker.onSeeked(it, durationMs)
                    AdLogicV2.onPlaybackProgress(it, context as? android.app.Activity)
                },
                onToggleCc = {
                    if (!sourceHasSubtitles(source) && !hasTextTracks) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.no_subtitles),
                            Toast.LENGTH_SHORT,
                        ).show()
                        subtitleOn = false
                    } else {
                        val next = !subtitleOn
                        subtitleOn = next
                        applySubtitleTracks(next)
                    }
                },
                onAuto = {
                    // Prefer cycling quality on current line; if single quality, cycle lines.
                    val line = ready.currentLine
                    if (line != null && line.list.size > 1) {
                        val next = (ready.sourceIndex + 1) % line.list.size
                        onSelectSource(next)
                    } else if (ready.links.size > 1) {
                        val next = (ready.lineIndex + 1) % ready.links.size
                        onSelectLine(next)
                    }
                },
                onPlaybackSpeed = { speed -> player.setPlaybackSpeed(speed) },
                onSelectEpisode = { ep -> onSwitchEpisode(ep) },
                onFullscreen = onEnterFullscreen,
                onExitFullscreen = onExitFullscreen,
                onToggleLock = {
                    locked = !locked
                    if (locked) showSourcePanel = false
                },
                    onOpenSourcePanel = {
                        showSourcePanel = !showSourcePanel
                    },
                )
            }
            // V9 design: Settings opens bottom Source/Quality panel (scrim + surface).
            if (landscape && !locked && showSourcePanel) {
                BackHandler { showSourcePanel = false }
                LineQualityPanel(
                    ready = ready,
                    onSelectLine = { idx ->
                        showSourcePanel = false
                        onSelectLine(idx)
                    },
                    onSelectSource = { idx ->
                        showSourcePanel = false
                        onSelectSource(idx)
                    },
                    onDismiss = { showSourcePanel = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Portrait: full watch-path meta under player (brief §4.2).
        if (!landscape) {
            val scroll = rememberScrollState()
            val lineLabel = ready.currentLine?.name?.ifBlank { null }
                ?: ready.links.getOrNull(ready.lineIndex)?.let { "Line ${ready.lineIndex + 1}" }
            val qualityLabels = ready.currentLine?.list?.mapIndexed { i, src ->
                src.quality?.takeIf { it.isNotBlank() } ?: "Source ${i + 1}"
            }.orEmpty()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll)
                    .background(VideoPlayTokens.PageBg)
                    .padding(bottom = 24.dp),
            ) {
                // Always render the same WatchMetaSection instance. While the
                // detail meta request is in flight it shows fixed-size skeletons
                // above the already-real Play Source/Quality rows; when the meta
                // arrives the slots crossfade in place and the source row never
                // moves. Seasons/recommends are appended below after data arrives.
                val meta = detailMeta?.detail
                WatchMetaSection(
                    details = meta?.details,
                    titleFallback = displayTitle,
                    currentLineLabel = lineLabel,
                    lines = ready.links,
                    selectedLineIndex = ready.lineIndex,
                    linesStatus = LinesStatus.Ready,
                    seasons = meta?.seasons.orEmpty(),
                    selectedSeasonIndex = detailMeta?.selectedSeasonIndex ?: 0,
                    selectedEpisode = currentEpisode,
                    recommends = meta?.recommends.orEmpty(),
                    onSelectLine = onSelectLine,
                    onSelectSeason = onSelectSeason,
                    onSelectEpisode = { _, ep, _ -> onSwitchEpisode(ep) },
                    onOpenRecommend = onOpenRecommend,
                    fallbackMediaId = mediaId,
                    qualityLabels = qualityLabels,
                    selectedQualityIndex = ready.sourceIndex,
                    onSelectQuality = onSelectSource,
                    loading = detailMetaLoading,
                )
            }
        }
    }
}

/**
 * V9 landscape Source/Quality panel (design notes):
 * Scrim tap closes; bottom Surface #1F1F1F, top radius 14dp;
 * max ~40% screen height, scrollable; V8 SourceCapsule rows.
 */
@Composable
private fun LineQualityPanel(
    ready: PlayerReady,
    onSelectLine: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val panelMax = screenH * 0.4f
    val surface = VideoPlayTokens.PageBg
    val shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

    Box(modifier = modifier) {
        // Scrim — tap outside panel to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = panelMax)
                .clip(shape)
                .background(surface)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.play_source),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
            )
            Spacer8()
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ready.links.forEachIndexed { index, link ->
                    SourceCapsule(
                        label = link.name.ifBlank { "Line ${index + 1}" },
                        selected = index == ready.lineIndex,
                        onClick = { onSelectLine(index) },
                    )
                }
            }

            val line = ready.currentLine
            if (line != null && line.list.size > 1) {
                Text(
                    text = stringResource(R.string.quality),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                )
                Spacer8()
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    line.list.forEachIndexed { index, src ->
                        SourceCapsule(
                            label = src.quality?.takeIf { it.isNotBlank() } ?: "Source ${index + 1}",
                            selected = index == ready.sourceIndex,
                            onClick = { onSelectSource(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer8() {
    Spacer(Modifier.height(8.dp))
}

@OptIn(UnstableApi::class)
private fun playSource(player: ExoPlayer, source: PlaySourceDto) {
    // Every non-empty API header key (incl. User-Agent) must land on OkHttpDataSource.
    // Prior bug: UA was continue'd out of the map and only set via setUserAgent — CDN 403/429.
    val headers = CdnRequestHeaders.build(source.headers)
    val userAgent = CdnRequestHeaders.userAgentOf(headers)
    Log.i(
        "PlayerActivity",
        "CDN headers keys=${headers.keys} hasUA=${!userAgent.isNullOrBlank()} url=${source.url.take(80)}",
    )
    // OkHttp interceptor path: every hop (incl. redirects) keeps this source's headers.
    MediaCdnHeaders.set(headers)
    // Fresh Factory per playSource so line/quality switches never reuse another source's headers.
    val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.mediaOkHttpClient)
        .setDefaultRequestProperties(headers)
    if (!userAgent.isNullOrBlank()) {
        // Dual insurance: map already has User-Agent; setUserAgent mirrors Exo default path.
        dataSourceFactory.setUserAgent(userAgent)
    }

    val subtitleConfigs = source.subtitles.mapNotNull { buildSubtitleConfig(it) }

    val uri = source.url.trim()
    val mime = guessPlayMime(uri)
    val mediaItem = MediaItem.Builder()
        .setUri(uri)
        .apply { if (mime != null) setMimeType(mime) }
        .setSubtitleConfigurations(subtitleConfigs)
        .build()

    val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
        .createMediaSource(mediaItem)

    player.setMediaSource(mediaSource, /* resetPosition= */ true)
    player.prepare()
}

private fun guessPlayMime(url: String): String? {
    // Force HLS/DASH MediaSource via mime so DefaultMediaSourceFactory never
    // falls through to Progressive for .m3u8 / application/x-mpegURL URLs.
    val lower = url.lowercase()
    return when {
        ".m3u8" in lower || "application/x-mpegurl" in lower || "application/vnd.apple.mpegurl" in lower ->
            MimeTypes.APPLICATION_M3U8
        ".mpd" in lower || "application/dash+xml" in lower -> MimeTypes.APPLICATION_MPD
        ".mp4" in lower -> MimeTypes.VIDEO_MP4
        else -> null
    }
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
        .setSelectionFlags(0)
        .build()
}
