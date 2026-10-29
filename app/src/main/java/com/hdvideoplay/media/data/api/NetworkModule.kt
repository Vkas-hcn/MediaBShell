package com.hdvideoplay.media.data.api

import com.hdvideoplay.media.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the CDN request headers for the currently playing outside-link source.
 * Applied by [NetworkModule.mediaOkHttpClient] so every media call (incl. redirects)
 * carries the headers returned by outside/links.
 */
object MediaCdnHeaders {
    private val current = AtomicReference<Map<String, String>>(emptyMap())

    fun set(headers: Map<String, String>) {
        current.set(LinkedHashMap(headers))
    }

    fun clear() {
        current.set(emptyMap())
    }

    fun snapshot(): Map<String, String> = current.get()
}

object NetworkModule {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    /**
     * Dedicated client for Exo/Media3 — no API logging.
     * Interceptor re-applies [MediaCdnHeaders] on every hop (incl. redirects).
     */
    private val cdnHeadersInterceptor = Interceptor { chain ->
        val original = chain.request()
        val headers = MediaCdnHeaders.snapshot()
        val request = if (headers.isEmpty()) {
            original
        } else {
            val builder = original.newBuilder()
            for ((name, value) in headers) {
                // header() replaces any existing value (case-insensitive).
                builder.header(name, value)
            }
            builder.build()
        }
        if (BuildConfig.DEBUG) {
            val sent = request.headers.names().sorted().joinToString(",")
            android.util.Log.i(
                "MediaCdnHttp",
                "REQ ${request.method} ${request.url} hdr=[$sent]",
            )
            for (name in request.headers.names()) {
                android.util.Log.i(
                    "MediaCdnHttp",
                    "REQ_H $name=${request.header(name)}",
                )
            }
        }
        val response = chain.proceed(request)
        if (BuildConfig.DEBUG) {
            android.util.Log.i(
                "MediaCdnHttp",
                "RESP code=${response.code} len=${response.header("content-length")} url=${response.request.url}",
            )
        }
        response
    }

    val mediaOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(cdnHeadersInterceptor)
        .build()

    /**
     * Short-timeout client for outside/links liveness probes.
     * Same redirect/SSL defaults as [mediaOkHttpClient], but **no**
     * [MediaCdnHeaders] interceptor — headers are applied per Request so
     * concurrent probes cannot race the player.
     */
    val mediaProbeOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun baseUrl(): String {
        val raw = BuildConfig.BASE_URL.trimEnd('/')
        return "$raw/"
    }

    val api: MediaApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl())
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MediaApi::class.java)
    }
}
