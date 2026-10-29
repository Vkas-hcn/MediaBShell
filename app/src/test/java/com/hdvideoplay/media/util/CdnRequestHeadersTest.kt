package com.hdvideoplay.media.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CdnRequestHeadersTest {

    @Test
    fun lowercaseKnownKeys_canonicalizedAndKeptIncludingUserAgent() {
        val raw = mapOf(
            "accept" to "*/*",
            "user-agent" to "com.community.mbox.in.geobypass (Linux; Android)",
            "referer" to "https://example.com/",
            "origin" to "https://example.com",
        )
        val headers = CdnRequestHeaders.build(raw)
        assertEquals(
            setOf("Accept", "User-Agent", "Referer", "Origin"),
            headers.keys,
        )
        assertEquals("*/*", headers["Accept"])
        assertEquals(
            "com.community.mbox.in.geobypass (Linux; Android)",
            headers["User-Agent"],
        )
        assertEquals(
            "com.community.mbox.in.geobypass (Linux; Android)",
            CdnRequestHeaders.userAgentOf(headers),
        )
        // Keys must not be dropped from the map (regression: old playSource continue'd UA out).
        assertTrue(headers.containsKey("User-Agent"))
        assertFalse(headers.keys.any { it.equals("user-agent", ignoreCase = true) && it != "User-Agent" })
    }

    @Test
    fun unknownKeys_neverDropped() {
        val raw = mapOf(
            "x-custom-token" to "abc",
            "Cookie" to "a=1",
            "X-Forwarded-For" to "1.2.3.4",
        )
        val headers = CdnRequestHeaders.build(raw)
        assertEquals("abc", headers["x-custom-token"])
        assertEquals("a=1", headers["Cookie"])
        assertEquals("1.2.3.4", headers["X-Forwarded-For"])
        assertEquals(3, headers.size)
    }

    @Test
    fun blankKeysOrValues_skipped() {
        val headers = CdnRequestHeaders.build(
            mapOf(
                "" to "x",
                "  " to "y",
                "Accept" to "",
                "User-Agent" to "  ",
                "ok" to "1",
            ),
        )
        assertEquals(mapOf("ok" to "1"), headers)
    }

    @Test
    fun nullOrEmpty_returnsEmpty() {
        assertTrue(CdnRequestHeaders.build(null).isEmpty())
        assertTrue(CdnRequestHeaders.build(emptyMap()).isEmpty())
    }

    @Test
    fun acceptEncoding_droppedForOkHttpTransparentGzip() {
        val headers = CdnRequestHeaders.build(
            mapOf(
                "user-agent" to "ExoPlayerLib/1.1.8 (Linux;Android 13) ExoPlayerLib/2.17.0",
                "accept-encoding" to "gzip",
                "Accept-Encoding" to "gzip, deflate",
                "content-encoding" to "gzip",
                "accept" to "*/*",
            ),
        )
        assertEquals(
            setOf("User-Agent", "Accept"),
            headers.keys,
        )
        assertFalse(headers.keys.any { it.equals("Accept-Encoding", ignoreCase = true) })
        assertFalse(headers.keys.any { it.equals("Content-Encoding", ignoreCase = true) })
    }

}
