package com.hdvideoplay.media.util

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
