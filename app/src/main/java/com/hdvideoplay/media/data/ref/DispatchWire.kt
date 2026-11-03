package com.hdvideoplay.media.data.ref

import com.hdvideoplay.media.ads.HdDispatchConfig
import com.hdvideoplay.media.data.dto.FlexibleCodeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/** Referrer fallback used when InstallReferrer returns an empty (organic) value. */
const val DEFAULT_REFERRER = "utm_source=(not%20set)&utm_medium=(not%20set)"

/**
 * Obfuscated request-field keys. Backend may rotate these; update this map only.
 */
val OBFUSCATION: Map<String, String> = mapOf(
    "appName" to "3a0m81",
    "version" to "osyv5e",
    "distinctId" to "epk91u",
    "gaid" to "i0om2z",
    "phoneModel" to "tp2pg0",
    "refer" to "hgak7y",
    "language" to "b08xj1",
    "osVersion" to "lfrbhg"
)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
internal data class DispatchEnvelope(
    @Serializable(with = FlexibleCodeSerializer::class) val code: String = "",
    val msg: String = "",
    val data: JsonElement? = null,
)

/** Parsed dispatch config + its raw merged-config JSON (the envelope's `data` field). */
data class DispatchResult(
    val config: HdDispatchConfig,
    val configJson: String,
)

/** Build the obfuscated request body. Keys are replaced via [mapping]. */
fun buildObfuscatedPayload(
    fields: Map<String, String>,
    mapping: Map<String, String>,
): String = buildJsonObject {
    fields.forEach { (field, value) ->
        put(mapping[field] ?: field, value)
    }
}.toString()

/**
 * Parse a wrapped dispatch response. Success = code in {0,200} (or absent) and
 * data.hd_load_scenes non-empty. Returns the parsed config AND the raw
 * merged-config JSON (`data`) so callers can persist/apply it. Returns null
 * otherwise (incl. 500 / empty scenes).
 */
fun parseDispatchResponse(body: String): DispatchResult? {
    val env = runCatching {
        json.decodeFromString(DispatchEnvelope.serializer(), body)
    }.getOrNull() ?: return null
    if (env.code.isNotEmpty() && env.code != "0" && env.code != "200") return null
    val data = env.data ?: return null
    val cfg = runCatching {
        json.decodeFromJsonElement<HdDispatchConfig>(data)
    }.getOrNull() ?: return null
    if (cfg.scenes.isEmpty()) return null
    return DispatchResult(config = cfg, configJson = data.toString())
}

/** Parse a wrapped cloak response. Success = code == 200 (or 0). */
fun parseCloakResponse(body: String): Boolean {
    val env = runCatching {
        json.decodeFromString(DispatchEnvelope.serializer(), body)
    }.getOrNull() ?: return false
    return env.code == "0" || env.code == "200"
}
