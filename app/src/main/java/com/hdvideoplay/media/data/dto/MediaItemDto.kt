package com.hdvideoplay.media.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Common list item. Most list APIs use [media_id]; daily uses [id].
 */
@Serializable
data class MediaItemDto(
    @SerialName("media_id") val mediaId: Int? = null,
    val id: Int? = null,
    val title: String = "",
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val season: Int? = null,
    @SerialName("origin_country") val originCountry: String? = null,
) {
    /**
     * Local Media.id — prefer a *positive* media_id, else a positive id (daily).
     * Kotlin `0 ?: id` stays 0 (0 is not null), which dropped real ids and
     * surfaced as Invalid media / skipped navigation.
     */
    fun localId(): Int {
        val mid = mediaId
        if (mid != null && mid > 0) return mid
        val iid = id
        if (iid != null && iid > 0) return iid
        return mid ?: iid ?: 0
    }
}
