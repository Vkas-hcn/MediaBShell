package com.hdvideoplay.media.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Reserved for employee-2 (detail + Exo). Declared so the API surface is complete.
 */
@Serializable
data class DetailResponseDto(
    val details: DetailInfoDto? = null,
    val seasons: List<SeasonDto> = emptyList(),
    val recommends: List<RecommendGroupDto> = emptyList(),
)

@Serializable
data class DetailInfoDto(
    val id: Int = 0,
    val title: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    val genres: List<GenreDto> = emptyList(),
    @SerialName("origin_country") val originCountry: JsonElement? = null,
)

@Serializable
data class GenreDto(
    val id: Int = 0,
    val name: String = "",
)

@Serializable
data class SeasonDto(
    @SerialName("media_id") val mediaId: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
data class EpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class RecommendGroupDto(
    val k: String = "",
    val v: List<MediaItemDto> = emptyList(),
)

@Serializable
data class OutsideLinksResponseDto(
    @SerialName("outside_links") val outsideLinks: List<OutsideLinkDto> = emptyList(),
)

@Serializable
data class OutsideLinkDto(
    val name: String = "",
    @SerialName("audio_type") val audioType: String? = null,
    @SerialName("is_official") val isOfficial: Boolean? = null,
    val list: List<PlaySourceDto> = emptyList(),
)

@Serializable
data class PlaySourceDto(
    val id: JsonElement? = null,
    val url: String = "",
    val quality: String? = null,
    val proportion: String? = null,
    val headers: Map<String, String>? = null,
    val subtitles: List<SubtitleDto> = emptyList(),
)

@Serializable
data class SubtitleDto(
    val id: JsonElement? = null,
    @SerialName("actual_url") val actualUrl: String = "",
    val lang: String? = null,
    val language: String? = null,
    val format: String? = null,
    @SerialName("request_headers") val requestHeaders: Map<String, String>? = null,
)

@Serializable
data class PlayReportBody(
    @SerialName("media_id") val mediaId: Int,
    @SerialName("list_id") val listId: Int,
    val episode: Int,
    @SerialName("line_name") val lineName: String,
    @SerialName("android_id") val androidId: String? = null,
    @SerialName("is_success") val isSuccess: Boolean,
)
