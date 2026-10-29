package com.hdvideoplay.media.ads

import java.util.concurrent.atomic.AtomicReference

/**
 * Hands a loaded [CachedAd] (NativeAd payload) from [NativeAdLoader] to [AdActivity]
 * without parceling SDK objects through Intent extras.
 *
 * [presentMode] selects midcard vs fullscreen shell layout (e2 AdActivity).
 */
object AdDisplayBridge {
    private val pending = AtomicReference<Pending?>(null)

    data class Pending(
        val entry: CachedAd,
        val presentMode: NativePresentMode,
        val onDismiss: (shown: Boolean) -> Unit,
        val location: AdLocation = AdLocation.RETURN_AD,
    )

    fun offer(
        entry: CachedAd,
        presentMode: NativePresentMode,
        onDismiss: (shown: Boolean) -> Unit,
        location: AdLocation = AdLocation.RETURN_AD,
    ): Boolean {
        val prev = pending.getAndSet(Pending(entry, presentMode, onDismiss, location))
        if (prev != null) {
            runCatching { prev.onDismiss(false) }
        }
        return true
    }

    fun take(): Pending? = pending.getAndSet(null)
}
