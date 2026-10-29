package com.hdvideoplay.media.util

/**
 * Search / list poster fallback.
 * Empty path or a bare TMDB size base like `…/w500` (no following path segment)
 * should not be loaded — use placeholder instead.
 */
object PosterUtils {

    /**
     * @return a loadable image URL, or null when the client should show a placeholder.
     */
    fun resolvePosterUrl(posterPath: String?): String? {
        val raw = posterPath?.trim().orEmpty()
        if (raw.isEmpty()) return null

        // Absolute URL with only a size suffix (e.g. https://image.tmdb.org/t/p/w500)
        val bareSize = Regex("""https?://.*/(?:w\d+|original)/?$""", RegexOption.IGNORE_CASE)
        if (bareSize.matches(raw)) return null

        // Relative path that is only a size token
        if (raw.matches(Regex("""/?w\d+/?""", RegexOption.IGNORE_CASE))) return null

        return raw
    }
}
