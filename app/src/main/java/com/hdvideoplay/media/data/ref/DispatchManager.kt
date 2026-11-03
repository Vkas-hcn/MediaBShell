package com.hdvideoplay.media.data.ref

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.hdvideoplay.media.BuildConfig
import com.hdvideoplay.media.track.AttributionState
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.data.notice.NoticeConfigStore
import com.flux.tracksdk.core.TrackSDK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * api/dispatch (ad config) + api/cloak (blacklist) client.
 *
 * - Cold start: [onReferrerReady] is invoked by BeForTool once InstallReferrer is
 *   available (or a persisted value already exists).
 * - Throttle: fetch only if never succeeded or >= 3h since the last success.
 * - Background: ProcessLifecycleOwner.onStart + a 3h periodic loop re-check.
 * - Success overwrites the old payload in [DataTool].
 *
 * Logging (android.util.Log, same convention as the rest of the app): each
 * request logs its URL, request body (入参), HTTP status + response body (出参),
 * and the business success/failure result.
 */
object DispatchManager {

    private const val TAG = "DispatchManager"

    private val API_BASE = if (BuildConfig.DEBUG) {
        "https://testserver.googletogoogle.com"
    } else {
        "https://api.hdvideoplayer.cc"
    }
    private val DISPATCH_URL = API_BASE + "/api/dispatch"
    private val CLOAK_URL = API_BASE + "/api/cloak"

    private const val REFRESH_INTERVAL_MS = 3L * 60L * 60L * 1000L
    private const val DISPATCH_ATTEMPTS = 3
    private const val CLOAK_ATTEMPTS = 10
    private const val TIMEOUT_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshInFlight = AtomicBoolean(false)
    private val cloakMutex = Mutex()

    private lateinit var app: Context
    private var periodicJob: Job? = null
    private var initialized = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            refreshIfNeeded()
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        app = context.applicationContext

        val cached = DataTool.dispatch_payload
        if (cached.isNotBlank()) {
            AdScheduler.sceneRepo()?.loadFromJson(cached)
            runCatching {
                val cachedConfig = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString(com.hdvideoplay.media.ads.HdDispatchConfig.serializer(), cached)
                NoticeConfigStore.save(cachedConfig.notice)
            }.onFailure { Log.w(TAG, "cached notice config parse failed: " + it.message) }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        startPeriodicRefresh()
    }

    /** Referrer is ready (or already persisted) — dispatch + cloak. */
    fun onReferrerReady() {
        refreshIfNeeded()
        scope.launch { reportCloakOnce() }
    }

    private fun startPeriodicRefresh() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (true) {
                delay(REFRESH_INTERVAL_MS)
                runCatching { refreshIfNeeded() }
                    .onFailure { Log.w(TAG, "periodic refresh failed: " + it.message) }
            }
        }
    }

    private fun shouldRefresh(): Boolean {
        val last = DataTool.dispatch_fetched_at
        return last <= 0L || System.currentTimeMillis() - last >= REFRESH_INTERVAL_MS
    }

    private fun refreshIfNeeded() {
        if (!initialized) return
        if (DataTool.ref_can.isEmpty()) return
        if (!shouldRefresh()) return
        if (!refreshInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                doDispatch()
            } finally {
                refreshInFlight.set(false)
            }
        }
    }

    private suspend fun doDispatch() {
        val payload = buildPayload()
        for (attempt in 1..DISPATCH_ATTEMPTS) {
            val body = post(DISPATCH_URL, "dispatch", payload)
            if (body == null) {
                Log.w(TAG, "dispatch attempt " + attempt + " timeout/error")
                continue
            }
            val result = parseDispatchResponse(body)
            if (result != null) {
                DataTool.dispatch_payload = result.configJson
                DataTool.dispatch_fetched_at = System.currentTimeMillis()
                AdScheduler.sceneRepo()?.loadFromJson(result.configJson)
                NoticeConfigStore.save(result.config.notice)
                // 配置拉取成功后评估买量/自然量判定（E23/E25，幂等一次性）。
                AttributionState.evaluateOnce(app)
                Log.i(TAG, "dispatch SUCCESS (attempt " + attempt + ")")
                return
            }
            Log.w(TAG, "dispatch FAILED (attempt " + attempt + ") — code!=0/200 or hd_load_scenes empty")
        }
    }

    private suspend fun reportCloakOnce() {
        if (DataTool.cloak_reported) return
        cloakMutex.withLock {
            if (DataTool.cloak_reported) return@withLock
            val payload = buildPayload()
            var done = false
            for (attempt in 1..CLOAK_ATTEMPTS) {
                val body = post(CLOAK_URL, "cloak", payload)
                if (body != null && parseCloakResponse(body)) {
                    DataTool.cloak_reported = true
                    Log.i(TAG, "cloak SUCCESS (attempt " + attempt + ")")
                    done = true
                    break
                }
                Log.w(TAG, "cloak FAILED (attempt " + attempt + ")")
            }
            if (!done) {
                Log.w(TAG, "cloak exhausted " + CLOAK_ATTEMPTS + " attempts, retry next cold start")
            }
        }
    }

    private suspend fun buildPayload(): String {
        val gaid = readGaid()
        val fields = mapOf(
            "appName" to BuildConfig.APPLICATION_ID,
            "version" to BuildConfig.VERSION_NAME,
            "distinctId" to TrackSDK.getInstance().effectiveDistinctId.orEmpty(),
            "refer" to DataTool.ref_can.ifBlank { DEFAULT_REFERRER },
            "language" to Locale.getDefault().language.orEmpty(),
            "osVersion" to Build.VERSION.RELEASE.orEmpty(),
            "gaid" to gaid,
            "phoneModel" to Build.MODEL.orEmpty(),
        )
        return buildObfuscatedPayload(fields, OBFUSCATION)
    }

    private suspend fun readGaid(): String = withContext(Dispatchers.IO) {
        runCatching {
            AdvertisingIdClient.getAdvertisingIdInfo(app).id.orEmpty()
        }.onFailure { Log.w(TAG, "read GAID failed: " + it.message) }
            .getOrDefault("")
    }

    /**
     * POST a JSON body and log the full round-trip: URL, 入参 (request body),
     * HTTP status + 出参 (response body). Returns the body on HTTP 2xx, else null.
     */
    private suspend fun post(url: String, label: String, payload: String): String? = withContext(Dispatchers.IO) {
        Log.i(TAG, "[" + label + "] URL=" + url)
        Log.i(TAG, "[" + label + "] REQUEST=" + payload)

        val body = payload.toRequestBody("application/json; charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            httpClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ok = resp.isSuccessful
                Log.i(TAG, "[" + label + "] RESPONSE http=" + resp.code + " ok=" + ok + " body=" + text)
                if (ok) text else null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "[" + label + "] ERROR=" + t.message)
            null
        }
    }
}
