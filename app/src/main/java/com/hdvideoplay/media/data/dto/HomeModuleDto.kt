package com.hdvideoplay.media.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HomeModuleDto(
    val id: Int = 0,
    @SerialName("module_name") val moduleName: String = "",
    val data: List<MediaItemDto> = emptyList(),
)

@Serializable
data class StreamEntryDto(
    val id: Int = 0,
    @SerialName("stream_name") val streamName: String = "",
    @SerialName("stream_url") val streamUrl: String? = null,
)

@Serializable
data class StreamingPlatformDto(
    val name: String = "",
    val icon: String? = null,
)
