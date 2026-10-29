package com.hdvideoplay.media.data.dto

import com.hdvideoplay.media.data.api.NetworkModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaySourceDtoHeadersTest {

    private val json: Json = NetworkModule.json

    @Test
    fun outsideLinksSample_headersMapPreservesAllKeys() {
        // Shape matches stage outside/links list item for media_id=10000.
        val sample = """
            {
              "id": 38927161,
              "url": "https://bcdn.hakunaymatata.com/resource/x.mp4",
              "headers": {
                "accept": "*/*",
                "user-agent": "com.community.mbox.in.geobypass (Linux; Android)",
                "referer": "https://hdvideoplay.top/",
                "origin": "https://hdvideoplay.top",
                "x-extra": "keep-me"
              },
              "quality": "720p",
              "subtitles": [],
              "proportion": ""
            }
        """.trimIndent()
        val dto = json.decodeFromString(PlaySourceDto.serializer(), sample)
        val headers = dto.headers
        assertNotNull(headers)
        assertEquals(
            setOf("accept", "user-agent", "referer", "origin", "x-extra"),
            headers!!.keys,
        )
        assertEquals("*/*", headers["accept"])
        assertEquals("com.community.mbox.in.geobypass (Linux; Android)", headers["user-agent"])
        assertEquals("keep-me", headers["x-extra"])
    }
}
