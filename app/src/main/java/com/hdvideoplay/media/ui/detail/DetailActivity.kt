package com.hdvideoplay.media.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ads.AdScenes
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ads.NativeReturnPresenter
import com.hdvideoplay.media.ads.PageDwellTracker
import com.hdvideoplay.media.data.notice.HintUtil
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.ui.player.PlayerActivity
import com.hdvideoplay.media.ui.splash.SplashActivity
import com.hdvideoplay.media.ui.theme.MediaTheme

/**
 * Detail page: meta + lines / seasons / recommends.
 * Content-list entries use [PlayerActivity] directly (play-auto brief 方案 A).
 * When [EXTRA_AUTO_PLAY] is true, Detail auto-starts Player once links are ready
 * so the user never waits on the poster+red-button shell.
 *
 * Intent extras contract:
 * - [EXTRA_MEDIA_ID] (Int, required) — local Media.id
 * - [EXTRA_SEASON] (Int, optional)
 * - [EXTRA_EPISODE] (Int, optional; movies default to 0)
 * - [EXTRA_AUTO_PLAY] (Boolean, optional; default false)
 */
class DetailActivity : ComponentActivity() {

    private fun mediaIdOf(i: Intent = intent): Int = i.getIntExtra(EXTRA_MEDIA_ID, -1)
    private fun episodeOf(i: Intent = intent): Int = i.getIntExtra(EXTRA_EPISODE, 0)
    private fun autoPlayOf(i: Intent = intent): Boolean = i.getBooleanExtra(EXTRA_AUTO_PLAY, false)
    private fun fromPositionOf(i: Intent = intent): String? = i.getStringExtra(EXTRA_FROM_POSITION)
    private fun moduleIdOf(i: Intent = intent): Int? =
        i.getIntExtra(EXTRA_MODULE_ID, 0).takeIf { it > 0 }

    /** E19 reactivation_landing：拉活落地详情页真正展示后只上报一次（旋转重建不重报）。 */
    private var landingReported = false
    private var createdFromSavedState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createdFromSavedState = savedInstanceState != null
        Log.i("DetailActivity", "NAV onCreate instance=" + hashCode() +
            " mediaId=" + mediaIdOf() + " savedState=" + (savedInstanceState != null))
        enableEdgeToEdge()
        // Load ≠ Show: enter Detail only preloads normal pools; show on return_ad.
        AdScheduler.preload(AdScenes.NORMAL_1)
        AdScheduler.preload(AdScenes.NORMAL_2)
        PageDwellTracker.enter(PageDwellTracker.DETAIL)
        Log.i("DetailActivity", "enter → preload normal_1 + normal_2 (no show)")
        bindContent()
    }

    override fun onStart() {
        super.onStart()
        // Re-warm on visible (idempotent: cache hit / in-flight guarded in scheduler).
        AdScheduler.preload(AdScenes.NORMAL_1)
        AdScheduler.preload(AdScenes.NORMAL_2)
    }

    override fun onResume() {
        super.onResume()
        // E19：通知深链落详情页、页面真正可见时上报（不提前、不重复）。
        if (!landingReported &&
            !createdFromSavedState &&
            intent.hasExtra(SplashActivity.EXTRA_SESSION_SOURCE)
        ) {
            landingReported = true
            val source = intent.getStringExtra(SplashActivity.EXTRA_SESSION_SOURCE) ?: "notification"
            val posId = when {
                intent.hasExtra(HintUtil.CO_SHOW_TYPE) -> "news"
                intent.getBooleanExtra(SplashActivity.EXTRA_FROM_FCM, false) -> "fcm"
                else -> null
            }
            TrackEvents.reactivationLanding(source, "detail", posId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newMediaId = mediaIdOf(intent)
        val newEpisode = episodeOf(intent)
        // 同一个视频的重复触发（重复点击通知等）：不重建，避免详情页重新加载 + autoPlay 二次跳转
        val sameTarget = newMediaId == mediaIdOf() && newEpisode == episodeOf()
        setIntent(intent)
        Log.i(
            "DetailActivity",
            "onNewIntent mediaId=" + newMediaId + " ep=" + newEpisode +
                " same=" + sameTarget + (if (sameTarget) " → ignore" else " → recreate"),
        )
        if (sameTarget) return
        recreate()
    }

    private fun bindContent() {
        val mediaId = mediaIdOf()
        val initialEpisode = episodeOf()
        Log.i(
            "DetailActivity",
            "bindContent mediaId=$mediaId episode=$initialEpisode intent=$intent",
        )
        setContent {
            MediaTheme {
                // System / gesture back must share the Compose onBack hook (return_ad),
                // otherwise default finish bypasses AdScheduler.show(RETURN_AD).
                BackHandler { attemptReturnAdThenFinish() }
                val fromPosition = fromPositionOf()
                val detailViewModel: DetailViewModel = viewModel(
                    key = "detail-$mediaId-$initialEpisode",
                    factory = DetailViewModel.factory(
                        mediaId = mediaId,
                        initialEpisode = initialEpisode,
                        trackDetailEvents = fromPosition != null,
                        fromPosition = fromPosition,
                        moduleId = moduleIdOf(),
                        autoPlay = autoPlayOf(),
                    ),
                )
                DetailScreen(
                    mediaId = mediaId,
                    initialEpisode = initialEpisode,
                    autoPlay = autoPlayOf(),
                    viewModel = detailViewModel,
                    onBack = { attemptReturnAdThenFinish() },
                    onPlay = { playMediaId, episode, title, fullscreen, lineIndex ->
                        if (playMediaId <= 0) {
                            Log.e(
                                "DetailActivity",
                                "refuse play: playMediaId=$playMediaId " +
                                    "(intentMediaId=$mediaId season fallback may be 0)",
                            )
                            Toast.makeText(
                                this@DetailActivity,
                                getString(R.string.invalid_media),
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@DetailScreen
                        }
                        Log.i(
                            "DetailActivity",
                            "start Player mediaId=$playMediaId ep=$episode line=$lineIndex",
                        )
                        // V2C: do not leave/discard here — Player.enter(PLAYER) returns
                        // this Detail Leave and handleLeave keeps longDwell (dwell > firstWatch).
                        startActivity(
                            PlayerActivity.createIntent(
                                context = this@DetailActivity,
                                mediaId = playMediaId,
                                episode = episode,
                                title = title,
                                startFullscreen = fullscreen,
                                lineIndex = lineIndex,
                            ),
                        )
                    },
                    onOpenRecommend = { item ->
                        val id = item.localId()
                        if (id > 0) {
                            // Same as list entries: go straight to Player (autoPlay).
                            startActivity(
                                PlayerActivity.createIntent(
                                    context = this@DetailActivity,
                                    mediaId = id,
                                    episode = 0,
                                    title = item.title,
                                    fromPosition = TrackEvents.FROM_RECOMMEND,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }


    private var returnAdConsumed = false

    private fun attemptReturnAdThenFinish() {
        if (returnAdConsumed) {
            finish()
            return
        }
        returnAdConsumed = true
        // dwell≥60s → 成对; V2B short dwell → no ad (presentReturn returns false → finish).
        val leave = PageDwellTracker.leaveIfCurrent(PageDwellTracker.DETAIL, "return")
        val longDwell = leave?.longDwell == true
        val started = NativeReturnPresenter.presentReturn(this, longDwell) {
            finish()
        }
        if (!started) finish()
    }

    companion object {
        const val EXTRA_MEDIA_ID = "EXTRA_MEDIA_ID"
        const val EXTRA_SEASON = "EXTRA_SEASON"
        const val EXTRA_EPISODE = "EXTRA_EPISODE"
        const val EXTRA_AUTO_PLAY = "EXTRA_AUTO_PLAY"
        const val EXTRA_FROM_POSITION = "EXTRA_FROM_POSITION"
        const val EXTRA_MODULE_ID = "EXTRA_MODULE_ID"

        fun createIntent(
            context: Context,
            mediaId: Int,
            season: Int? = null,
            episode: Int = 0,
            autoPlay: Boolean = false,
            fromPosition: String? = null,
            moduleId: Int? = null,
        ): Intent = Intent(context, DetailActivity::class.java).apply {
            putExtra(EXTRA_MEDIA_ID, mediaId)
            if (season != null) putExtra(EXTRA_SEASON, season)
            putExtra(EXTRA_EPISODE, episode)
            putExtra(EXTRA_AUTO_PLAY, autoPlay)
            if (fromPosition != null) putExtra(EXTRA_FROM_POSITION, fromPosition)
            if (moduleId != null) putExtra(EXTRA_MODULE_ID, moduleId)
        }
    }
}
