package com.hdvideoplay.media.util

/**
 * Build per-source CDN request headers for Exo OkHttpDataSource.
 *
 * API may return lowercase keys (`user-agent`, `referer`, …). Known names are
 * canonicalized; **every** non-empty unknown key is kept — never dropped.
 * User-Agent stays in the map for [setDefaultRequestProperties] (callers may
 * also pass it to [OkHttpDataSource.Factory.setUserAgent] as dual insurance).
 */
object CdnRequestHeaders {

    fun build(raw: Map<String, String>?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (raw.isNullOrEmpty()) return out
        for ((k, v) in raw) {
            val keyRaw = k.trim()
            val value = v.trim()
            if (keyRaw.isEmpty() || value.isEmpty()) continue
            // OkHttp transparent gzip: if the app sets Accept-Encoding itself,
            // OkHttp will NOT decompress. API often returns accept-encoding:gzip
            // for CDN; forwarding it makes Exo parse gzip bytes as m3u8/mp4 → fail.
            // Same for Content-Encoding (response-only; never send on request).
            when (keyRaw.lowercase()) {
                "accept-encoding", "content-encoding" -> continue
            }
            val key = when (keyRaw.lowercase()) {
                "user-agent" -> "User-Agent"
                "referer" -> "Referer"
                "origin" -> "Origin"
                "accept" -> "Accept"
                "accept-language" -> "Accept-Language"
                "cookie" -> "Cookie"
                else -> keyRaw
            }
            out[key] = value
        }
        return out
    }

    fun userAgentOf(headers: Map<String, String>): String? =
        headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
}
