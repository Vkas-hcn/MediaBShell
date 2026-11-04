package com.hdvideoplay.media.data.api

import com.hdvideoplay.media.BuildConfig
import com.hdvideoplay.media.track.TrackEvents
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
    /**
     * [default] = 当前播放源（视频）的请求头；[byUrl] = 按 URL 精确匹配的请求头（如字幕
     * request_headers）。拦截器优先命中 byUrl，否则回落 default；HLS 分片/重定向回落 default。
     */
    data class Snapshot(
        val default: Map<String, String> = emptyMap(),
        val byUrl: Map<String, Map<String, String>> = emptyMap(),
    )

    private val current = AtomicReference(Snapshot())

    fun set(headers: Map<String, String>) {
        val prev = current.get()
        current.set(Snapshot(default = LinkedHashMap(headers), byUrl = prev.byUrl))
    }

    fun setByUrl(byUrl: Map<String, Map<String, String>>) {
        val prev = current.get()
        current.set(Snapshot(default = prev.default, byUrl = copyByUrl(byUrl)))
    }

    /** 原子设置视频默认 headers 与 URL 专属 headers，避免读到半更新状态。 */
    fun set(default: Map<String, String>, byUrl: Map<String, Map<String, String>>) {
        current.set(Snapshot(default = LinkedHashMap(default), byUrl = copyByUrl(byUrl)))
    }

    fun clear() {
        current.set(Snapshot())
    }

    fun snapshot(): Map<String, String> = current.get().default

    /** 命中 URL 专属 headers（如字幕），否则回落到视频默认 headers。 */
    fun headersFor(url: String): Map<String, String> {
        val s = current.get()
        return s.byUrl[url] ?: s.default
    }

    private fun copyByUrl(byUrl: Map<String, Map<String, String>>): Map<String, Map<String, String>> =
        byUrl.mapValues { (_, v) -> LinkedHashMap(v) }
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

    /**
     * api_error：MediaApi 接口 HTTP 错误 / 网络异常上报。
     * 仅挂在本 API 客户端（CDN/探测客户端不受影响）；业务失败（HTTP 200 + code!=1）
     * 由 MediaRepository 上报，两条路径互斥不重报。
     * 网络异常无 HTTP 状态码时 http_code=0（不伪造状态码）。
     */
    private val apiErrorInterceptor = Interceptor { chain ->
        val apiName = TrackEvents.apiNameForPath(chain.request().url.encodedPath)
        val start = android.os.SystemClock.elapsedRealtime()
        if (apiName == null) {
            chain.proceed(chain.request())
        } else {
            try {
                val response = chain.proceed(chain.request())
                if (response.code !in 200..299) {
                    TrackEvents.apiError(
                        api = apiName,
                        httpCode = response.code,
                        costMs = android.os.SystemClock.elapsedRealtime() - start,
                    )
                }
                response
            } catch (t: java.io.IOException) {
                TrackEvents.apiError(
                    api = apiName,
                    httpCode = 0,
                    costMs = android.os.SystemClock.elapsedRealtime() - start,
                )
                throw t
            }
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(apiErrorInterceptor)
        .addInterceptor(logging)
        .build()

    /**
     * Dedicated client for Exo/Media3 — no API logging.
     * Interceptor re-applies [MediaCdnHeaders] on every hop (incl. redirects).
     */
    private val cdnHeadersInterceptor = Interceptor { chain ->
        val original = chain.request()
        val headers = MediaCdnHeaders.headersFor(original.url.toString())
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
