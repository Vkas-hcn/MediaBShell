package com.hdvideoplay.media.ads

import java.util.concurrent.ConcurrentHashMap

/**
 * Load concurrency gate (HaloDrift jb3.u parity).
 * Prevents duplicate in-flight load Jobs per scene — NOT an attribution/install gate.
 * A/B cold pool selection lives in [AdGate] (jb3.x).
 */
object LoadGate {
    private val inflight = ConcurrentHashMap.newKeySet<String>()

    /** Try to acquire load slot for [scene]. false = already loading. */
    fun tryAcquire(scene: String): Boolean = inflight.add(scene)

    fun release(scene: String) {
        inflight.remove(scene)
    }

    fun isLoading(scene: String): Boolean = inflight.contains(scene)

    fun clear() {
        inflight.clear()
    }
}
