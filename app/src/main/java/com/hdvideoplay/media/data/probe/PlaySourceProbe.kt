package com.hdvideoplay.media.data.probe

import android.util.Log
import com.hdvideoplay.media.BuildConfig
import com.hdvideoplay.media.data.api.NetworkModule
import com.hdvideoplay.media.data.dto.OutsideLinkDto
import com.hdvideoplay.media.data.dto.PlaySourceDto
import com.hdvideoplay.media.util.CdnRequestHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optional **weak** play-source probe (HEAD → Range → GET).
 *
 * HaloDrift alignment: probe results are **marks only**. Callers must never
 * hide lines or block Exo start based on probe outcomes — play the first link
 * immediately and failover on Exo error (403 etc.) with play_report.
 *
 * Uses [NetworkModule.mediaProbeOkHttpClient] and applies headers **per request**
 * via [CdnRequestHeaders] — never touches [com.hdvideoplay.media.data.api.MediaCdnHeaders].
 */
object PlaySourceProbe {

    private const val TAG = "PlaySourceProbe"
    private const val MAX_CONCURRENCY = 8

    /** Whole-line-set probe deadline. On timeout callers fall back to config order. */
    const val PROBE_TIMEOUT_MS = 10_000L

    /** Timed probe result. [timedOut] means not every source finished in time. */
    data class ProbeOutcome(
        val probed: List<ProbedLink>,
        val timedOut: Boolean,
    )

    /** Per-line probe result. [sourceFlags] aligns with the original source list. */
    data class ProbedLink(
        val link: OutsideLinkDto,
        val sourceFlags: List<Boolean>,
    ) {
        val isValid: Boolean get() = sourceFlags.any { it }

        /** Valid sources first; original order is preserved inside both groups. */
        fun orderedLink(): OutsideLinkDto {
            val valid = link.list.filterIndexed { index, _ ->
                sourceFlags.getOrElse(index) { false }
            }
            val invalid = link.list.filterIndexed { index, _ ->
                !sourceFlags.getOrElse(index) { false }
            }
            return link.copy(list = valid + invalid)
        }
    }

    /** Status codes that mean the CDN accepted the probe. */
    fun isAvailableStatus(code: Int): Boolean = code == 200 || code == 206

    private val client get() = NetworkModule.mediaProbeOkHttpClient

    private val semaphore = Semaphore(MAX_CONCURRENCY)

    /**
     * Weak mark: for each line, parallel list of per-source probe OK flags.
     * Empty source lists yield empty inner lists. Never filters / drops lines.
     */
    suspend fun markAvailability(links: List<OutsideLinkDto>): List<List<Boolean>> {
        if (links.isEmpty()) return emptyList()
        return coroutineScope {
            links.map { link ->
                async {
                    link.list.map { src ->
                        async {
                            if (src.url.isBlank()) false else probe(src)
                        }
                    }.awaitAll()
                }
            }.awaitAll()
        }
    }

    /**
     * Probe every line/source concurrently, then expose results for ordering.
     * HEAD is attempted first per source; the existing Range/GET fallback remains
     * for CDNs that reject HEAD. Headers are applied per source by [probe].
     */
    suspend fun probeLinks(links: List<OutsideLinkDto>): List<ProbedLink> {
        val flags = markAvailability(links)
        return links.mapIndexed { index, link ->
            ProbedLink(
                link = link,
                sourceFlags = flags.getOrElse(index) { List(link.list.size) { false } },
            )
        }
    }

    /**
     * Probe all lines/sources with an overall deadline. When the deadline hits,
     * the partial results collected so far are returned with [ProbeOutcome.timedOut].
     * Callers decide whether to keep the partial valid-first order or fall back
     * to the config order when no playable source was found.
     */
    suspend fun probeLinksTimed(
        links: List<OutsideLinkDto>,
        timeoutMs: Long = PROBE_TIMEOUT_MS,
    ): ProbeOutcome {
        if (links.isEmpty()) return ProbeOutcome(emptyList(), timedOut = false)

        val states: List<MutableList<Boolean>> =
            links.map { link -> MutableList(link.list.size) { false } }
        val total = links.sumOf { it.list.size }
        val completed = AtomicInteger(0)

        val finished: Unit? = if (timeoutMs > 0L) {
            withTimeoutOrNull(timeoutMs) {
                runProbeLoop(links, states, completed, total)
            }
        } else {
            runProbeLoop(links, states, completed, total)
            Unit
        }

        val probed = links.mapIndexed { index, link ->
            ProbedLink(link = link, sourceFlags = states[index].toList())
        }
        return ProbeOutcome(probed = probed, timedOut = finished == null)
    }

    private suspend fun runProbeLoop(
        links: List<OutsideLinkDto>,
        states: List<MutableList<Boolean>>,
        completed: AtomicInteger,
        total: Int,
    ) {
        coroutineScope {
            links.forEachIndexed { lineIndex, link ->
                link.list.forEachIndexed { sourceIndex, src ->
                    launch {
                        states[lineIndex][sourceIndex] =
                            if (src.url.isBlank()) false else probe(src)
                        completed.incrementAndGet()
                    }
                }
            }
            while (completed.get() < total) {
                delay(20L)
            }
        }
        Unit
    }

    /** Stable partition: playable lines first, unplayable lines after. */
    fun validFirst(probed: List<ProbedLink>): List<ProbedLink> {
        val (valid, invalid) = probed.partition { it.isValid }
        return valid + invalid
    }

    /**
     * @deprecated Strong filter — do **not** use for play gating. Prefer
     * [markAvailability] for optional UI marks, and Exo failover for truth.
     * Kept for unit tests / diagnostics only.
     */
    @Deprecated(
        message = "Do not gate playback on probe; use markAvailability for UI marks only",
        replaceWith = ReplaceWith("markAvailability(links)"),
    )
    suspend fun filterAvailable(links: List<OutsideLinkDto>): List<OutsideLinkDto> {
        if (links.isEmpty()) return emptyList()
        return coroutineScope {
            links.map { link ->
                async {
                    val sources = link.list.filter { it.url.isNotBlank() }
                    if (sources.isEmpty()) return@async null
                    val ok = sources.map { src ->
                        async { if (probe(src)) src else null }
                    }.awaitAll().filterNotNull()
                    if (ok.isEmpty()) null else link.copy(list = ok)
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun probe(source: PlaySourceDto): Boolean = withContext(Dispatchers.IO) {
        val url = source.url.trim()
        if (url.isEmpty()) return@withContext false
        val headers = CdnRequestHeaders.build(source.headers)
        semaphore.withPermit {
            probeUrl(url, headers)
        }
    }

    /**
     * HEAD first; if CDN does not support HEAD / inconclusive, fall back to
     * Range GET (`bytes=0-0`) then a short plain GET. Only 200/206 count.
     */
    internal fun probeUrl(url: String, headers: Map<String, String>): Boolean {
        val headCode = execute(url, headers, method = "HEAD", range = null)
        if (headCode != null && isAvailableStatus(headCode)) {
            logResult(url, "HEAD", headCode, true, headers)
            return true
        }
        if (shouldFallbackFromHead(headCode)) {
            val rangeCode = execute(url, headers, method = "GET", range = "bytes=0-0")
            if (rangeCode != null && isAvailableStatus(rangeCode)) {
                logResult(url, "GET-Range", rangeCode, true, headers)
                return true
            }
            if (shouldFallbackFromRange(rangeCode)) {
                val getCode = execute(url, headers, method = "GET", range = null)
                val ok = getCode != null && isAvailableStatus(getCode)
                logResult(url, "GET", getCode, ok, headers)
                return ok
            }
            // Range definitive fail (e.g. 403): still try plain GET once for evidence / CDN quirks.
            if (rangeCode == 403 || rangeCode == 401 || rangeCode == 428 || rangeCode == 429) {
                val getCode = execute(url, headers, method = "GET", range = null)
                val ok = getCode != null && isAvailableStatus(getCode)
                logResult(url, "GET-after-Range-$rangeCode", getCode, ok, headers)
                return ok
            }
            logResult(url, "GET-Range", rangeCode, false, headers)
            return false
        }
        logResult(url, "HEAD", headCode, false, headers)
        return false
    }

    /** HEAD 405/501/403/400 or null (network) → try Range/GET. */
    internal fun shouldFallbackFromHead(code: Int?): Boolean =
        code == null || code == 405 || code == 501 || code == 403 || code == 400 ||
            (code in 200..299 && !isAvailableStatus(code))

    internal fun shouldFallbackFromRange(code: Int?): Boolean =
        code == null || code == 405 || code == 501 || code == 416 ||
            (code in 200..299 && !isAvailableStatus(code))

    private fun execute(
        url: String,
        headers: Map<String, String>,
        method: String,
        range: String?,
    ): Int? {
        val builder = Request.Builder().url(url)
        for ((name, value) in headers) {
            builder.header(name, value)
        }
        if (range != null) {
            builder.header("Range", range)
        }
        when (method) {
            "HEAD" -> builder.head()
            "GET" -> builder.get()
            else -> builder.method(method, null)
        }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                response.body?.close()
                response.code
            }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "probe $method fail url=${url.take(96)} err=${t.message}")
            }
            null
        }
    }

    private fun logResult(
        url: String,
        via: String,
        code: Int?,
        ok: Boolean,
        headers: Map<String, String> = emptyMap(),
    ) {
        if (!BuildConfig.DEBUG) return
        val keys = headers.keys.sorted().joinToString(",")
        Log.i(TAG, "probe via=$via code=$code ok=$ok hdr=[$keys] url=${url.take(96)}")
        for ((k, v) in headers) {
            Log.i(TAG, "probe_H $k=$v")
        }
    }
}
