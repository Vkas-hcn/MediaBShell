package com.hdvideoplay.media.ui.my

import android.content.Context
import org.json.JSONObject

/** Local About H5 switch. Edit assets/about_config.json and rebuild. */
data class AboutConfig(
    val enabled: Boolean = false,
    val url: String = "",
)

object AboutConfigLoader {
    private const val ASSET = "about_config.json"

    fun load(context: Context): AboutConfig = runCatching {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        AboutConfig(
            enabled = json.optBoolean("enabled", false),
            url = json.optString("url", "").trim(),
        )
    }.getOrElse { AboutConfig() }

    fun isUsable(config: AboutConfig): Boolean =
        config.enabled && config.url.startsWith("https://", ignoreCase = true)
}
