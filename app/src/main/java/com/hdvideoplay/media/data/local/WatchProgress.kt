package com.hdvideoplay.media.data.local

import kotlinx.serialization.Serializable

@Serializable
data class WatchProgress(
    val mediaId: Int,
    val title: String,
    val poster: String = "",
    val progressSec: Long = 0,
    val durationSec: Long = 0,
    val updatedAt: Long = 0, // epoch ms
    val mediaType: String = "movie", // movie|tv
    val season: Int? = null,
    val episode: Int? = null,
)
