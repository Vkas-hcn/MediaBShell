package com.hdvideoplay.media.data.probe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaySourceProbeTest {

    @Test
    fun isAvailableStatus_only200Or206() {
        assertTrue(PlaySourceProbe.isAvailableStatus(200))
        assertTrue(PlaySourceProbe.isAvailableStatus(206))
        assertFalse(PlaySourceProbe.isAvailableStatus(204))
        assertFalse(PlaySourceProbe.isAvailableStatus(301))
        assertFalse(PlaySourceProbe.isAvailableStatus(403))
        assertFalse(PlaySourceProbe.isAvailableStatus(404))
        assertFalse(PlaySourceProbe.isAvailableStatus(500))
        assertFalse(PlaySourceProbe.isAvailableStatus(0))
    }

    @Test
    fun shouldFallbackFromHead_onUnsupportedOrUncertain() {
        assertTrue(PlaySourceProbe.shouldFallbackFromHead(null))
        assertTrue(PlaySourceProbe.shouldFallbackFromHead(405))
        assertTrue(PlaySourceProbe.shouldFallbackFromHead(501))
        // CDN often blocks HEAD with 403 while Range/GET works
        assertTrue(PlaySourceProbe.shouldFallbackFromHead(403))
        assertTrue(PlaySourceProbe.shouldFallbackFromHead(400))
        assertFalse(PlaySourceProbe.shouldFallbackFromHead(200))
        assertFalse(PlaySourceProbe.shouldFallbackFromHead(206))
        assertFalse(PlaySourceProbe.shouldFallbackFromHead(404))
        assertFalse(PlaySourceProbe.shouldFallbackFromHead(500))
    }

    @Test
    fun shouldFallbackFromRange_onUnsupportedOrUncertain() {
        assertTrue(PlaySourceProbe.shouldFallbackFromRange(null))
        assertTrue(PlaySourceProbe.shouldFallbackFromRange(405))
        assertTrue(PlaySourceProbe.shouldFallbackFromRange(501))
        assertTrue(PlaySourceProbe.shouldFallbackFromRange(416))
        assertFalse(PlaySourceProbe.shouldFallbackFromRange(200))
        assertFalse(PlaySourceProbe.shouldFallbackFromRange(206))
        assertFalse(PlaySourceProbe.shouldFallbackFromRange(403))
        assertFalse(PlaySourceProbe.shouldFallbackFromRange(404))
    }
}
