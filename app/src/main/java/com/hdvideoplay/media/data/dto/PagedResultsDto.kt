package com.hdvideoplay.media.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagedResultsDto(
    val page: Int = 1,
    val size: Int = 20,
    val total: Int = 0,
    @SerialName("num_pages") val numPages: Int = 0,
    val results: List<MediaItemDto> = emptyList(),
)

@Serializable
data class FilterOptionsDto(
    val genres: List<String> = emptyList(),
    @SerialName("release_years") val releaseYears: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
)
