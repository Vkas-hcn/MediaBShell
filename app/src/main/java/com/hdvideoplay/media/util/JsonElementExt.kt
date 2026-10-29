package com.hdvideoplay.media.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Only report list_id when the source id can be converted to Int. */
fun JsonElement?.asIntOrNull(): Int? {
    val el = this ?: return null
    if (el !is JsonPrimitive) return null
    el.intOrNull?.let { return it }
    return el.contentOrNull?.trim()?.toIntOrNull()
}

/**
 * origin_country may be a JSON string, array of strings, or null.
 * Returns the first usable country/region code (e.g. "US").
 */
fun JsonElement?.asFirstStringOrNull(): String? {
    val el = this ?: return null
    when (el) {
        is JsonPrimitive -> return el.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        is JsonArray -> {
            for (item in el) {
                val s = (item as? JsonPrimitive)?.contentOrNull?.trim()
                if (!s.isNullOrBlank()) return s
            }
            return null
        }
        else -> return null
    }
}
