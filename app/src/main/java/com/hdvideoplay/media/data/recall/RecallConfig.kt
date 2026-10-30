package com.hdvideoplay.media.data.recall

import com.hdvideoplay.media.data.dto.ApiResult
import com.hdvideoplay.media.data.repo.ApiException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Recall config is re-read on cold start / after this TTL. */
internal const val RECALL_CONFIG_TTL_MS = 6L * 60L * 60L * 1000L

/** Never hammer the config endpoint after a failed attempt. */
internal const val RECALL_CONFIG_RETRY_MS = 5L * 60L * 1000L

/**
 * Last successfully fetched config.
 *
 * The backend contract is "data.value is the switch": a non-blank value means
 * the popup must show and the CTA opens that value. A blank value or code
 * 20001 is an authoritative "off" and is cached as such.
 */
internal data class RecallConfig(
    val value: String,
    val fetchedAtMs: Long,
) {
    val downloadUrl: String get() = value.trim()

    val enabled: Boolean get() = downloadUrl.isNotEmpty()

    fun isExpired(nowMs: Long, ttlMs: Long = RECALL_CONFIG_TTL_MS): Boolean =
        fetchedAtMs <= 0L || nowMs < fetchedAtMs || nowMs - fetchedAtMs > ttlMs
}

internal sealed interface RecallConfigFetch {
    data class Loaded(val value: String) : RecallConfigFetch
    data object Disabled : RecallConfigFetch
    data class Failed(val error: Throwable? = null) : RecallConfigFetch
}

/**
 * code=1 + data.value => Loaded (blank value is an authoritative off).
 * code=20001 (config row missing) => Disabled.
 * Anything else is a failed fetch: keep the previous successful value.
 */
internal fun parseRecallConfig(result: ApiResult): RecallConfigFetch = when {
    result.isSuccess -> RecallConfigFetch.Loaded(readValue(result.data).trim())
    result.code == "20001" -> RecallConfigFetch.Disabled
    else -> RecallConfigFetch.Failed(ApiException(result.code, result.msg))
}

private fun readValue(data: JsonElement?): String = when (data) {
    is JsonObject -> (data["value"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    is JsonPrimitive -> data.contentOrNull.orEmpty()
    else -> ""
}
