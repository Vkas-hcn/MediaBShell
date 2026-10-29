package com.hdvideoplay.media.ads

import android.util.Log

/**
 * Same-page dwell for V2 ad logic.
 *
 * Pages counted (product): Home / Channel / Detail / Search / Player / History / Module.
 * **My is excluded** (高靖 V2C) — do not accumulate dwell; leave to/from My must not
 * fire longDwell PAIRED. Continuous stay ≥ [THRESHOLD_MS] then leave
 * (tab switch / back / open Player) → interstitial-feel fullscreen shell
 * (scheduler may pair midcard after dismiss).
 */
object PageDwellTracker {

    const val THRESHOLD_MS = 60_000L

    const val HOME = "home"
    const val CHANNEL = "channel"
    const val DETAIL = "detail"
    const val SEARCH = "search"
    const val PLAYER = "player"
    /** Not a dwell page (高靖 V2C): never [enter]; kept for callers that need the key. */
    const val MY = "my"
    const val HISTORY = "history"
    /** Stable key for Module composable (moduleId optional suffix via [moduleKey]). */
    const val MODULE = "module"

    fun moduleKey(moduleId: Int? = null): String =
        if (moduleId != null && moduleId > 0) "module:$moduleId" else MODULE

    /**
     * My (MY / My / my) and route forms: no dwell clock / no leave pair.
     * Keep local (no AdLogicV2 call) to avoid object-init cycles.
     */
    fun isDwellExempt(page: String?): Boolean {
        if (page.isNullOrBlank()) return false
        val p = page.trim().lowercase()
        return p == MY ||
            p == "route:my" ||
            p.startsWith("my/") ||
            p.startsWith("my:")
    }

    data class Leave(
        val page: String,
        val dwellMs: Long,
        val longDwell: Boolean,
    )

    @Volatile private var currentPage: String? = null
    @Volatile private var enteredAtMs: Long = 0L

    /**
     * Enter [page]. If another page was active, returns its [Leave] snapshot
     * (caller decides whether to emit fullscreen). Does not emit ads itself.
     *
     * My / exempt: clears any previous clock **without** returning a Leave for PAIRED
     * (switching to My must not fire), and does not start a new dwell clock.
     */
    @Synchronized
    fun enter(page: String): Leave? {
        if (isDwellExempt(page)) {
            // Discard previous leave for ad purposes when navigating TO My.
            leaveInternal(reason = "enter_exempt:$page")
            currentPage = null
            enteredAtMs = 0L
            runCatching { AdLogicV2.onRouteEnter(page, 0L) }
            Log.d(TAG, "enter exempt page=$page — no dwell clock, previous leave suppressed")
            return null
        }
        val leave = leaveInternal(reason = "enter:$page")
        // Leaving an exempt page (should not be tracked) → never longDwell.
        val safeLeave = leave?.let {
            if (isDwellExempt(it.page)) it.copy(longDwell = false) else it
        }
        currentPage = page
        enteredAtMs = System.currentTimeMillis()
        // Keep e1 AdLogicV2 route clock in sync for resolveBackAdAction.
        runCatching { AdLogicV2.onRouteEnter(page, enteredAtMs) }
        Log.d(TAG, "enter page=$page")
        return safeLeave
    }

    /**
     * Leave current page without entering another (e.g. open PlayerActivity from Home).
     */
    @Synchronized
    fun leaveCurrent(reason: String = "leave"): Leave? = leaveInternal(reason)

    /** Peek dwell of current page without clearing. */
    @Synchronized
    fun peekDwellMs(): Long {
        if (currentPage == null || enteredAtMs <= 0L) return 0L
        if (isDwellExempt(currentPage)) return 0L
        return (System.currentTimeMillis() - enteredAtMs).coerceAtLeast(0L)
    }

    @Synchronized
    fun peekLongDwell(): Boolean = peekDwellMs() >= THRESHOLD_MS

    @Synchronized
    fun currentPage(): String? = currentPage

    /**
     * Leave [page] only if it is current (Detail/Player finish). Safe if mismatched.
     */
    @Synchronized
    fun leaveIfCurrent(page: String, reason: String = "leaveIf"): Leave? {
        if (currentPage != page) {
            Log.d(TAG, "leaveIf skip want=$page current=$currentPage")
            return null
        }
        return leaveInternal(reason)
    }

    private fun leaveInternal(reason: String): Leave? {
        val page = currentPage ?: return null
        val started = enteredAtMs
        currentPage = null
        enteredAtMs = 0L
        if (started <= 0L) return null
        val dwell = (System.currentTimeMillis() - started).coerceAtLeast(0L)
        // My / exempt never qualifies as longDwell.
        val longDwell = !isDwellExempt(page) && dwell >= THRESHOLD_MS
        Log.i(TAG, "leave page=$page dwellMs=$dwell long=$longDwell reason=$reason")
        return Leave(page = page, dwellMs = dwell, longDwell = longDwell)
    }

    private const val TAG = "PageDwell"
}
