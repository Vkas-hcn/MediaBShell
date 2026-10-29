package com.hdvideoplay.media.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterUtilsTest {

    @Test
    fun emptyOrBlank_returnsNull() {
        assertNull(PosterUtils.resolvePosterUrl(null))
        assertNull(PosterUtils.resolvePosterUrl(""))
        assertNull(PosterUtils.resolvePosterUrl("   "))
    }

    @Test
    fun bareTmdbW500_returnsNull() {
        assertNull(PosterUtils.resolvePosterUrl("https://image.tmdb.org/t/p/w500"))
        assertNull(PosterUtils.resolvePosterUrl("https://image.tmdb.org/t/p/w500/"))
        assertNull(PosterUtils.resolvePosterUrl("http://image.tmdb.org/t/p/original"))
    }

    @Test
    fun bareRelativeSize_returnsNull() {
        assertNull(PosterUtils.resolvePosterUrl("w500"))
        assertNull(PosterUtils.resolvePosterUrl("/w500"))
        assertNull(PosterUtils.resolvePosterUrl("/w500/"))
    }

    @Test
    fun fullPosterUrl_returnedAsIs() {
        val url = "https://image.tmdb.org/t/p/w500/abc123.jpg"
        assertEquals(url, PosterUtils.resolvePosterUrl(url))
    }

    @Test
    fun cdnPoster_returnedAsIs() {
        val url = "https://cdn.example.com/poster.jpg"
        assertEquals(url, PosterUtils.resolvePosterUrl(url))
    }
}
