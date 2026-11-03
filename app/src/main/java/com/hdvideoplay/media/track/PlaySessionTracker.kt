package com.hdvideoplay.media.track

import android.os.SystemClock

/**
 * 播放会话状态机（需求 3.1-3.3）。
 *
 * 会话定义：一次进入播放页（PlayerActivity 实例）+ (mediaId, episode)。
 * - 线路/清晰度切换：同一会话（不重复 play_start，进度档位集合延续，回退不重报）。
 * - 集数切换：新会话（重置进度档位 / watched / maxPct / prepare 起点）。
 * - 旧会话因集数切换结束时不产生 play_end（end_reason 枚举只有 complete/back/error）。
 *
 * 去重保证：
 * - play_start：每会话仅首次成功出画（onRenderedFirstFrame）时上报一次。
 * - play_progress：25/50/75/100 每档每会话仅上报一次；拖动回退不重报；
 *   继续播放越过更高档位时正常补报新档。
 * - play_end：每会话仅上报一次；未出画（会话未真正开始）不产生结束事件。
 * - watched_ms：仅累计真实播放增量（isPlaying && !buffering 且单步增量在
 *   合理窗口 (0, 3000ms] 内）；拖动/回退/续播跳变不计入观看时长。
 */
class PlaySessionTracker(
    private val mediaId: Int,
    initialEpisode: Int,
    private val reporter: (eventName: String, props: Map<String, Any?>) -> Unit,
    /** 时钟注入：生产用 elapsedRealtime，单测可注入可控时钟。 */
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {

    companion object {
        private val PROGRESS_STEPS = intArrayOf(25, 50, 75, 100)
        /** 单次 tick 允许的最大位置增量：超过即视为 seek 跳变，不计入观看时长。 */
        private const val MAX_LEGIT_STEP_MS = 3_000L
    }

    var episode: Int = initialEpisode
        private set

    private var lineName: String = ""
    private var prepareStartedAtMs: Long = -1L

    private var playStartReported = false
    private val reportedPcts = mutableSetOf<Int>()
    private var watchedMs = 0L
    private var lastPosMs = -1L
    private var maxPct = 0
    private var ended = false

    val isStarted: Boolean get() = playStartReported
    val isEnded: Boolean get() = ended

    /** 会话内首次 prepare 时记录起点；线路切换不会重置（startup_ms 从会话发起播放算起）。 */
    fun onPrepareStarted(lineName: String) {
        this.lineName = lineName
        if (prepareStartedAtMs < 0) {
            prepareStartedAtMs = nowMs()
        }
    }

    /** 视频真正出画（onRenderedFirstFrame）：每会话只报一次 play_start。 */
    fun onFirstFrame() {
        if (playStartReported) return
        playStartReported = true
        val startup = if (prepareStartedAtMs > 0) {
            (nowMs() - prepareStartedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        reporter(
            TrackEvents.PLAY_START,
            mapOf(
                TrackEvents.P_MEDIA_ID to mediaId,
                TrackEvents.P_EPISODE to episode,
                TrackEvents.P_LINE_NAME to lineName,
                TrackEvents.P_STARTUP_MS to startup,
            ),
        )
    }

    /** 500ms 进度 tick：累计真实观看时长 + 检查 25/50/75/100 档位。 */
    fun onPositionTick(positionMs: Long, durationMs: Long, playing: Boolean, buffering: Boolean) {
        val pos = positionMs.coerceAtLeast(0L)
        if (lastPosMs >= 0L && playing && !buffering) {
            val delta = pos - lastPosMs
            if (delta in 1..MAX_LEGIT_STEP_MS) {
                watchedMs += delta
            }
        }
        lastPosMs = pos

        if (durationMs <= 0L) return
        val pct = ((pos * 100L) / durationMs).toInt().coerceIn(0, 100)
        if (pct > maxPct) maxPct = pct
        for (step in PROGRESS_STEPS) {
            if (pct >= step && reportedPcts.add(step)) {
                reporter(
                    TrackEvents.PLAY_PROGRESS,
                    mapOf(
                        TrackEvents.P_MEDIA_ID to mediaId,
                        TrackEvents.P_EPISODE to episode,
                        TrackEvents.P_PCT to step,
                        TrackEvents.P_WATCHED_MS to watchedMs,
                    ),
                )
            }
        }
    }

    /** 拖动进度条：只更新位置基准与最大进度，不把跳变算进观看时长。 */
    fun onSeeked(positionMs: Long, durationMs: Long) {
        lastPosMs = positionMs.coerceAtLeast(0L)
        if (durationMs > 0L) {
            val pct = ((lastPosMs * 100L) / durationMs).toInt().coerceIn(0, 100)
            if (pct > maxPct) maxPct = pct
        }
    }

    /** 视频正常播放完成 → play_end(complete)。 */
    fun onCompleted() = endOnce(TrackEvents.END_COMPLETE)

    /** 播放异常导致结束（全线路耗尽）→ play_end(error)。 */
    fun onErrorEnd() = endOnce(TrackEvents.END_ERROR)

    /** 用户返回/退出播放页 → play_end(back)。 */
    fun onUserExit() = endOnce(TrackEvents.END_BACK)

    /** Activity 销毁兜底（系统回收/任务移除）：已开始且未结束才报 back。 */
    fun onDestroyFallback() = endOnce(TrackEvents.END_BACK)

    /** 集数切换：旧集进度静默结束（无 play_end），开启新集会话。 */
    fun onEpisodeChanged(newEpisode: Int) {
        if (newEpisode == episode) return
        episode = newEpisode
        playStartReported = false
        reportedPcts.clear()
        watchedMs = 0L
        lastPosMs = -1L
        maxPct = 0
        ended = false
        prepareStartedAtMs = -1L
        lineName = ""
    }

    private fun endOnce(endReason: String) {
        if (ended || !playStartReported) return
        ended = true
        reporter(
            TrackEvents.PLAY_END,
            mapOf(
                TrackEvents.P_MEDIA_ID to mediaId,
                TrackEvents.P_EPISODE to episode,
                TrackEvents.P_END_REASON to endReason,
                TrackEvents.P_WATCHED_MS to watchedMs,
                TrackEvents.P_MAX_PCT to maxPct,
            ),
        )
    }
}
