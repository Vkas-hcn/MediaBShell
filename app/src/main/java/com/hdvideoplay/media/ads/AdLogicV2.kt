package com.hdvideoplay.media.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Ad Logic V2 scheduling kernel (高靖 2026-10-27).
 *
 * Owns: cold 10s gate, hot must-show splash_hot, firstWatch latch,
 * playback N×10min progress boundaries, and **成对** interstitial-feel emit
 * (fullscreen native shell → midcard).
 *
 * V2B (高靖): dwell &lt; 60s pure back/return → **NO ads at all**
 * (short return → NONE). Dead midcard-only return API removed. dwell ≥60s → paired.
 *
 * V2C (高靖):
 * - Same leave: dwell vs firstWatch emit **ONE** paired (dwell preferred).
 * - longDwell empty inventory → log `dwell_fire_empty` + one short delayed retry.
 * - **My excluded**: no route dwell clock / no leave PAIRED from My.
 *
 * Employee2 owns: route dwell UI, tab/back presentation, fullscreen shell layout.
 * This object exposes latches + emit APIs e2 can call — does not rewrite AdActivity UI.
 * e2: NativeReturnPresenter short dwell must not show either (presentReturn → false).
 *
 * Load ≠ Show. Never auto-click CTAs. V2 paths never use InterstitialAd API.
 */
object AdLogicV2 {

    private const val TAG = "AdLogicV2"

    /** Cold-start quiet window: forbid business ad emits (splash excluded). */
    const val COLD_QUIET_MS = 10_000L

    /** Playback progress boundary step (10 minutes). Trigger when position **>** N×this. */
    const val PROGRESS_BOUNDARY_MS = 600_000L

    /** Same-page dwell threshold for e2 route logic (≥60s → fullscreen shell pair). */
    const val ROUTE_DWELL_MS = 60_000L

    /** V2C: single empty-inventory retry delay for dwell paired (800–1500ms). */
    const val DWELL_EMPTY_RETRY_MS = 1_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val coldStartAtMs = AtomicLong(0L)
    private val firstWatchLatched = AtomicBoolean(false)
    private val firstReturnLatched = AtomicBoolean(false)

    /**
     * V2C leave-transition mutex: when a dwell paired emit is attempted/claimed for
     * this leave, [onEnterPlayer] must not emit a second firstWatch pair.
     */
    private val suppressNextFirstWatch = AtomicBoolean(false)

    /** Fired progress boundary indices N (1,2,3…) for current playback session. */
    private val firedProgressBoundaries = ConcurrentHashMap.newKeySet<Int>()

    /** Optional e2 dwell clock: route key → enter elapsedRealtime. */
    @Volatile
    private var routeEnteredAtMs: Long = 0L
    @Volatile
    private var currentRouteKey: String = ""

    // --- Cold start (B) ---

    /** Record cold-start t0. Call once from [AdScheduler.init] / process start. */
    fun markColdStart(nowMs: Long = System.currentTimeMillis()) {
        if (coldStartAtMs.compareAndSet(0L, nowMs)) {
            Log.i(TAG, "coldStart t0=$nowMs quiet=${COLD_QUIET_MS}ms")
        }
    }

    fun coldStartAtMs(): Long = coldStartAtMs.get()

    /** True while within cold 10s quiet window (business emits forbidden). */
    fun isInColdQuietWindow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val t0 = coldStartAtMs.get()
        if (t0 <= 0L) return false
        return nowMs - t0 < COLD_QUIET_MS
    }

    fun coldQuietRemainingMs(nowMs: Long = System.currentTimeMillis()): Long {
        val t0 = coldStartAtMs.get()
        if (t0 <= 0L) return 0L
        return (COLD_QUIET_MS - (nowMs - t0)).coerceAtLeast(0L)
    }

    /**
     * Whether [location] is a cold/hot splash (exempt from quiet window).
     * App-open / splash themselves are allowed during quiet.
     */
    fun isSplashLocation(location: AdLocation): Boolean =
        location == AdLocation.FIRST_SPLASH_COLD ||
            location == AdLocation.SPLASH_COLD ||
            location == AdLocation.SPLASH_HOT

    /** Business emit blocked in cold quiet (non-splash). */
    fun isBusinessEmitBlocked(location: AdLocation): Boolean =
        !isSplashLocation(location) && isInColdQuietWindow()

    // --- Latches (C) ---

    fun isFirstWatchPending(): Boolean = !firstWatchLatched.get()

    fun isFirstReturnPending(): Boolean = !firstReturnLatched.get()

    /** Peek without consuming. */
    fun peekFirstWatch(): Boolean = !firstWatchLatched.get()

    fun peekFirstReturn(): Boolean = !firstReturnLatched.get()

    /**
     * Latch firstWatch once. Returns true if this call consumed the latch
     * (caller should emit paired fullscreen shell).
     */
    fun consumeFirstWatch(): Boolean {
        val ok = firstWatchLatched.compareAndSet(false, true)
        if (ok) Log.i(TAG, "firstWatch latched")
        return ok
    }

    /**
     * Latch firstReturn once. Returns true if consumed.
     * V2B: short-dwell pure return must NOT emit; dwell≥60s → [emitPairedInterstitialFeel].
     * Kept for latch bookkeeping / long-dwell consume from e2 presenters.
     */
    fun consumeFirstReturn(): Boolean {
        val ok = firstReturnLatched.compareAndSet(false, true)
        if (ok) Log.i(TAG, "firstReturn latched")
        return ok
    }

    /** Test / e2 reset helpers. */
    fun setFirstWatchLatched(value: Boolean) {
        firstWatchLatched.set(value)
    }

    fun setFirstReturnLatched(value: Boolean) {
        firstReturnLatched.set(value)
    }

    // --- V2C leave-slot mutex (dwell vs firstWatch) ---

    fun isDwellPairReason(reason: PairReason): Boolean =
        reason == PairReason.ROUTE_DWELL || reason == PairReason.DWELL_BACK

    /**
     * Claim the single leave-transition pair slot so [onEnterPlayer] / firstWatch
     * cannot emit a second pair. Dwell has priority.
     */
    fun claimDwellLeaveSlot(reason: PairReason = PairReason.ROUTE_DWELL) {
        suppressNextFirstWatch.set(true)
        if (firstWatchLatched.compareAndSet(false, true)) {
            Log.i(TAG, "dwell leave slot claimed — firstWatch latched reason=${reason.logKey}")
        } else {
            Log.i(TAG, "dwell leave slot claimed — suppress firstWatch reason=${reason.logKey}")
        }
    }

    /** True if a dwell leave has claimed the pair slot for this transition. */
    fun isLeavePairedClaimed(): Boolean = suppressNextFirstWatch.get()

    // --- My exclusion (V2C 高靖) ---

    /**
     * My / MY / my route keys are never dwell-counted and must not drive PAIRED.
     * Accepts PageDwellTracker page, AdLogicV2 route key, or "route:my".
     */
    fun isMyRouteKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val k = key.trim().lowercase()
        return k == "my" ||
            k == PageDwellTracker.MY ||
            k == "route:my" ||
            k.startsWith("my/") ||
            k.startsWith("my:")
    }

    // --- Route dwell hooks for e2 (B / product §2 + §8.3) ---

    fun onRouteEnter(routeKey: String, nowMs: Long = System.currentTimeMillis()) {
        if (isMyRouteKey(routeKey) || PageDwellTracker.isDwellExempt(routeKey)) {
            // My: clear any prior clock; do not accumulate dwell.
            currentRouteKey = ""
            routeEnteredAtMs = 0L
            Log.d(TAG, "routeEnter skip My key=$routeKey")
            return
        }
        currentRouteKey = routeKey
        routeEnteredAtMs = nowMs
        Log.d(TAG, "routeEnter key=$routeKey")
    }

    fun currentRouteKey(): String = currentRouteKey

    fun routeDwellMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (isMyRouteKey(currentRouteKey)) return 0L
        val entered = routeEnteredAtMs
        if (entered <= 0L) return 0L
        return (nowMs - entered).coerceAtLeast(0L)
    }

    fun hasRouteDwellMet(nowMs: Long = System.currentTimeMillis()): Boolean =
        routeDwellMs(nowMs) >= ROUTE_DWELL_MS

    /**
     * e2 back / leave-route decision helper (V2B / V2C).
     * - My → [BackAdAction.NONE]
     * - dwell≥60s → paired fullscreen+midcard (成对)
     * - dwell&lt;60s pure back/return → [BackAdAction.NONE] (NO ads)
     *
     * Returns suggested [BackAdAction] without emitting.
     */
    fun resolveBackAdAction(nowMs: Long = System.currentTimeMillis()): BackAdAction {
        if (isInColdQuietWindow(nowMs)) return BackAdAction.NONE
        if (isMyRouteKey(currentRouteKey)) return BackAdAction.NONE
        if (hasRouteDwellMet(nowMs)) return BackAdAction.PAIRED_FULLSCREEN_THEN_MIDCARD
        // V2B clean: short dwell pure return → NONE (midcard-only enums removed).
        return BackAdAction.NONE
    }

    enum class BackAdAction {
        NONE,
        /** ≥60s dwell (or other interstitial-feel) → 成对 fullscreen → midcard. */
        PAIRED_FULLSCREEN_THEN_MIDCARD,
    }

    // --- Enter Player = first watch (C) ---

    /**
     * Call when entering [com.hdvideoplay.media.ui.player.PlayerActivity].
     * After cold quiet, first entry → 成对 fullscreen shell → midcard.
     * V2C: skipped when a dwell leave paired already claimed this transition.
     * @return true if a paired emit was started
     */
    fun onEnterPlayer(
        activity: Activity?,
        onComplete: (shownPair: Boolean) -> Unit = {},
    ): Boolean {
        AdScheduler.preload(AdScenes.NORMAL_1)
        AdScheduler.preload(AdScenes.NORMAL_2)

        if (isInColdQuietWindow()) {
            Log.i(TAG, "onEnterPlayer during cold quiet — no business emit")
            onComplete(false)
            return false
        }
        // V2C: dwell leave owns this transition — do not emit a second pair.
        if (suppressNextFirstWatch.compareAndSet(true, false)) {
            firstWatchLatched.set(true)
            Log.i(TAG, "onEnterPlayer skip — dwell leave pair owns this transition")
            onComplete(false)
            return false
        }
        if (!consumeFirstWatch()) {
            Log.d(TAG, "onEnterPlayer firstWatch already latched")
            onComplete(false)
            return false
        }
        Log.i(TAG, "onEnterPlayer → emitPairedInterstitialFeel(firstWatch)")
        return emitPairedInterstitialFeel(
            activity = activity,
            reason = PairReason.FIRST_WATCH,
            onComplete = onComplete,
        )
    }

    // --- Playback progress N×10min (D) ---

    /** Reset progress boundaries when a new media / source session starts. */
    fun resetProgressBoundaries() {
        firedProgressBoundaries.clear()
        Log.d(TAG, "progress boundaries reset")
    }

    /**
     * Hook Player position / seek. When position **>** N×10min for an unfired N,
     * emit 成对 fullscreen → midcard once per boundary.
     *
     * Does **not** use wall-clock [AdPolicy.playIntervalMs] / hd_play_interval.
     */
    fun onPlaybackProgress(
        positionMs: Long,
        activity: Activity?,
        onComplete: (shownPair: Boolean) -> Unit = {},
    ): Boolean {
        if (positionMs <= 0L) return false
        if (isInColdQuietWindow()) return false
        if (activity == null || activity.isFinishing || activity.isDestroyed) return false

        // Find lowest unfired N such that positionMs > N * PROGRESS_BOUNDARY_MS
        var n = 1
        var triggered = false
        while (n * PROGRESS_BOUNDARY_MS < positionMs) {
            if (firedProgressBoundaries.add(n)) {
                Log.i(
                    TAG,
                    "progress crossed N=$n boundary>${n * PROGRESS_BOUNDARY_MS}ms pos=$positionMs",
                )
                triggered = emitPairedInterstitialFeel(
                    activity = activity,
                    reason = PairReason.PROGRESS_BOUNDARY,
                    onComplete = onComplete,
                )
                // One pair at a time; subsequent N fire on later progress ticks after dismiss.
                break
            }
            n++
        }
        return triggered
    }

    // --- 成对 interstitial-feel (fullscreen → midcard) ---

    enum class PairReason(val logKey: String) {
        FIRST_WATCH("first_watch"),
        PROGRESS_BOUNDARY("progress_boundary"),
        ROUTE_DWELL("route_dwell"),
        DWELL_BACK("dwell_back"),
        /** Generic interstitial-feel; e2 may pass this. */
        INTERSTITIAL_FEEL("interstitial_feel"),
    }

    /**
     * **成对展示（强制）**: fullscreen native shell → onDismiss **immediately** midcard
     * (`return_ad`). Must not be broken by `showing` lock or `hd_ad_interval` between
     * the two segments. Forbidden: fullscreen-only OR midcard-only on these paths.
     *
     * Hot-start App Open remains standalone (not paired).
     *
     * V2C: for dwell reasons, claims leave slot (suppresses firstWatch) before show;
     * when shell+midcard both miss Ready inventory, logs `dwell_fire_empty` and
     * schedules **one** short delayed retry with preload kick.
     *
     * @return true if at least the fullscreen segment was started (or midcard if shell miss),
     *         or a dwell empty-retry was scheduled
     */
    fun emitPairedInterstitialFeel(
        activity: Activity?,
        reason: PairReason,
        onComplete: (shownPair: Boolean) -> Unit = {},
        allowEmptyRetry: Boolean = true,
    ): Boolean {
        val act = activity
        if (act == null || act.isFinishing || act.isDestroyed) {
            Log.w(TAG, "emitPaired($reason) no live activity")
            onComplete(false)
            return false
        }
        if (isInColdQuietWindow()) {
            Log.i(TAG, "emitPaired($reason) blocked by cold quiet")
            onComplete(false)
            return false
        }

        // V2C: dwell leave claims the only pair slot before competing for showing.
        if (isDwellPairReason(reason)) {
            claimDwellLeaveSlot(reason)
        }

        Log.i(TAG, "成对 emit start reason=${reason.logKey} → fullscreen_shell then return_ad")
        // Ensure inventory for both segments.
        AdScheduler.preload(AdScenes.NORMAL_1)
        AdScheduler.preload(AdScenes.NORMAL_2)

        // Stage-1: fullscreen native shell (e2 layout via NativePresentHints).
        NativePresentHints.setNext(NativePresentMode.FULLSCREEN)
        val shellStarted = AdScheduler.showInternal(
            location = AdLocation.FULLSCREEN_SHELL,
            activity = act,
            bypassInterval = true,
            pairSegment = true,
            preferredFormat = AdFormat.NATIVE,
            onDismiss = { shellShown ->
                Log.i(TAG, "成对 shell dismissed shown=$shellShown → immediate midcard")
                // Immediately chain midcard; must NOT be blocked by showing/interval.
                mainHandler.post {
                    val live = AdScheduler.currentActivityOr(act)
                    NativePresentHints.setNext(NativePresentMode.MIDCARD)
                    val midStarted = AdScheduler.showInternal(
                        location = AdLocation.RETURN_AD,
                        activity = live,
                        bypassInterval = true,
                        pairSegment = true,
                        preferredFormat = AdFormat.NATIVE,
                        onDismiss = { midShown ->
                            Log.i(
                                TAG,
                                "成对 complete reason=${reason.logKey} shell=$shellShown mid=$midShown",
                            )
                            onComplete(shellShown || midShown)
                        },
                    )
                    if (!midStarted) {
                        Log.w(TAG, "成对 midcard miss (no Ready) — shell already shown=$shellShown")
                        onComplete(shellShown)
                    }
                }
            },
        )

        if (!shellStarted) {
            // Soft fallback if shell inventory empty: still try midcard (log incomplete pair).
            Log.w(TAG, "成对 shell miss — attempt midcard fallback (inventory empty)")
            NativePresentHints.setNext(NativePresentMode.MIDCARD)
            val midStarted = AdScheduler.showInternal(
                location = AdLocation.RETURN_AD,
                activity = act,
                bypassInterval = true,
                pairSegment = true,
                preferredFormat = AdFormat.NATIVE,
                onDismiss = { midShown -> onComplete(midShown) },
            )
            if (!midStarted) {
                // V2C: shell+mid both miss on dwell → dwell_fire_empty + one retry.
                if (allowEmptyRetry && isDwellPairReason(reason)) {
                    return scheduleDwellEmptyRetry(act, reason, onComplete)
                }
                onComplete(false)
                return false
            }
            return true
        }
        return true
    }

    /**
     * When longDwell / ROUTE_DWELL / DWELL_BACK paired finds no Ready inventory
     * (shell+midcard both miss): log tag `dwell_fire_empty`, kick preload, retry once.
     */
    private fun scheduleDwellEmptyRetry(
        activity: Activity,
        reason: PairReason,
        onComplete: (shownPair: Boolean) -> Unit,
    ): Boolean {
        Log.w(TAG, "dwell_fire_empty reason=${reason.logKey} — schedule one retry in ${DWELL_EMPTY_RETRY_MS}ms")
        AdScheduler.preload(AdScenes.NORMAL_1)
        AdScheduler.preload(AdScenes.NORMAL_2)
        mainHandler.postDelayed({
            val live = AdScheduler.currentActivityOr(activity)
            if (live == null || live.isFinishing || live.isDestroyed) {
                Log.w(TAG, "dwell_fire_empty retry aborted (no live activity)")
                onComplete(false)
                return@postDelayed
            }
            Log.i(TAG, "dwell_fire_empty retry once reason=${reason.logKey}")
            AdScheduler.preload(AdScenes.NORMAL_1)
            AdScheduler.preload(AdScenes.NORMAL_2)
            val retried = emitPairedInterstitialFeel(
                activity = live,
                reason = reason,
                onComplete = onComplete,
                allowEmptyRetry = false, // only one retry — do not loop
            )
            if (!retried) {
                // onComplete already invoked by emitPaired on empty miss
                Log.w(TAG, "dwell_fire_empty retry miss reason=${reason.logKey}")
            }
        }, DWELL_EMPTY_RETRY_MS)
        // Treat scheduled retry as started so callers (finish-after-dismiss / skip firstWatch) wait.
        return true
    }

    /**
     * e2 helper: dwell≥60s leave/back → 成对. Call instead of midcard-only.
     * Claims leave slot + empty-retry for ROUTE_DWELL.
     */
    fun emitDwellPaired(
        activity: Activity?,
        onComplete: (shownPair: Boolean) -> Unit = {},
    ): Boolean = emitPairedInterstitialFeel(activity, PairReason.ROUTE_DWELL, onComplete)
}
