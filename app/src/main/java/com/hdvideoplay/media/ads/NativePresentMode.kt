package com.hdvideoplay.media.ads

import java.util.concurrent.atomic.AtomicReference

/**
 * Native AdActivity presentation mode (V2).
 *
 * - [MIDCARD]: dark-scrim mid-card (`activity_ad_native`) — paired stage-2 / non-return midcard paths.
 *   V2B: dwell&lt;60s pure return shows **no** ad (not midcard).
 * - [FULLSCREEN]: interstitial-feel native shell (`activity_ad_native_fullscreen`) —
 *   page-switch / long-dwell / first Player / 10min boundary stage-1.
 *
 * Pairing (fullscreen → midcard) is **owned by AdScheduler / employee1 enqueue**.
 * Display layer only presents the mode it is given and callbacks [onDismiss] so
 * the scheduler can immediately show stage-2 midcard. Do NOT launch midcard from UI.
 */
enum class NativePresentMode {
    MIDCARD,
    FULLSCREEN,
}

/**
 * One-shot hint consumed by [NativeAdLoader.show] → [AdActivity.launch].
 * Employee1 may [setNext] FULLSCREEN before emit for interstitial-feel stage-1;
 * employee2 return/dwell hooks also set this before [AdScheduler.show].
 */
object NativePresentHints {
    private val next = AtomicReference(NativePresentMode.MIDCARD)

    fun setNext(mode: NativePresentMode) {
        next.set(mode)
    }

    /** Consume and reset to [MIDCARD] default. */
    fun take(default: NativePresentMode = NativePresentMode.MIDCARD): NativePresentMode =
        next.getAndSet(default)

    fun peek(): NativePresentMode = next.get()
}
