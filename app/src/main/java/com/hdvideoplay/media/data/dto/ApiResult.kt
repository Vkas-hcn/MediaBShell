package com.hdvideoplay.media.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Unified envelope. [data] stays as JsonElement because the backend may return
 * object, array, or "" on errors; callers decode via [MediaRepository].
 */
@Serializable
data class ApiResult(
    @Serializable(with = FlexibleCodeSerializer::class)
    val code: String = "",
    val msg: String = "",
    val data: JsonElement? = null,
) {
    val isSuccess: Boolean get() = code == "1"
}
