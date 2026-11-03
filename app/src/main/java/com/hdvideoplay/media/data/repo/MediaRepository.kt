package com.hdvideoplay.media.data.repo

import com.hdvideoplay.media.data.api.MediaApi
import com.hdvideoplay.media.data.api.NetworkModule
import com.hdvideoplay.media.data.dto.ApiResult
import com.hdvideoplay.media.data.dto.DetailResponseDto
import com.hdvideoplay.media.data.dto.OutsideLinksResponseDto
import com.hdvideoplay.media.data.dto.PlayReportBody
import com.hdvideoplay.media.data.dto.FilterOptionsDto
import com.hdvideoplay.media.data.dto.HomeModuleDto
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.dto.PagedResultsDto
import com.hdvideoplay.media.data.dto.StreamEntryDto
import com.hdvideoplay.media.data.dto.StreamingPlatformDto
import com.hdvideoplay.media.track.TrackEvents
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class MediaRepository(
    private val api: MediaApi = NetworkModule.api,
    private val json: Json = NetworkModule.json,
) {
    /**
     * Home data must survive a network failure so the Home screen (and the
     * recall popup's offline fallback path) can render instead of crashing.
     * The API call is evaluated before decodeList()'s try block, so catch it
     * here as well.
     */
    suspend fun homeData(language: String = "en"): Result<List<HomeModuleDto>> = try {
        decodeList(timedApi("home") { api.homeData(language) })
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (t: Throwable) {
        Result.failure(t)
    }

    suspend fun homeStreamData(): Result<List<StreamEntryDto>> =
        decodeList(timedApi("home_stream") { api.homeStreamData() })

    suspend fun moduleData(id: Int, language: String = "en", page: Int = 1): Result<List<MediaItemDto>> =
        decodeList(timedApi("module") { api.moduleData(id, language, page) })

    suspend fun search(keyword: String, page: Int = 1): Result<PagedResultsDto> =
        decodeObject(timedApi("search") { api.search(keyword = keyword, page = page) }, PagedResultsDto())

    suspend fun filterOptions(): Result<FilterOptionsDto> =
        decodeObject(timedApi("filter") { api.filterOptions() }, FilterOptionsDto())

    suspend fun discover(
        category: Int = 1,
        genre: String? = null,
        releaseYear: String? = null,
        country: String? = null,
        page: Int = 1,
    ): Result<PagedResultsDto> = decodeObject(
        timedApi("discover") {
            api.discover(
                category = category,
                genre = genre,
                releaseYear = releaseYear,
                country = country,
                page = page,
            )
        },
        PagedResultsDto(),
    )

    suspend fun streaming(): Result<List<StreamingPlatformDto>> =
        decodeList(timedApi("streaming") { api.streaming() })

    suspend fun daily(dailyStyle: String = "2"): Result<List<MediaItemDto>> =
        decodeList(timedApi("daily") { api.daily(dailyStyle) })


    suspend fun detail(mediaId: Int, language: String = "en"): Result<DetailResponseDto> =
        decodeObject(timedApi("detail") { api.detail(mediaId, language) }, DetailResponseDto())

    suspend fun outsideLinks(
        mediaId: Int,
        episode: Int,
        language: String = "en",
    ): Result<OutsideLinksResponseDto> =
        decodeObject(
            timedApi("outsideLinks") { api.outsideLinks(mediaId, episode, language) },
            OutsideLinksResponseDto(),
        )

    /**
     * Play report must never break the playback path.
     * Failures are swallowed into Result.failure for optional logging.
     */
    suspend fun playReport(body: PlayReportBody): Result<Unit> = try {
        val result = timedApi("playReport") { api.playReport(body) }
        if (result.isSuccess) Result.success(Unit)
        else Result.failure(ApiException(result.code, result.msg))
    } catch (t: Throwable) {
        Result.failure(t)
    }

    /**
     * api_error 业务层：HTTP 200 但 ApiResult.code!=1 时上报一次。
     * HTTP 非 2xx / 网络异常由 NetworkModule 拦截器上报，此处不重复。
     */
    private suspend fun timedApi(apiName: String, call: suspend () -> ApiResult): ApiResult {
        // currentTimeMillis：JVM 单测可用（SystemClock 在本地单测未 mock）。
        val start = System.currentTimeMillis()
        val result = call()
        if (!result.isSuccess) {
            TrackEvents.apiError(
                api = apiName,
                httpCode = 200,
                costMs = System.currentTimeMillis() - start,
            )
        }
        return result
    }

    private inline fun <reified T> decodeList(result: ApiResult): Result<List<T>> = try {
        if (!result.isSuccess) {
            Result.failure(ApiException(result.code, result.msg))
        } else {
            val element = result.data
            when {
                element == null || element is JsonNull -> Result.success(emptyList())
                element is JsonPrimitive && element.content.isBlank() -> Result.success(emptyList())
                element is JsonArray -> Result.success(json.decodeFromJsonElement<List<T>>(element))
                else -> Result.success(emptyList())
            }
        }
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private inline fun <reified T> decodeObject(result: ApiResult, empty: T): Result<T> = try {
        if (!result.isSuccess) {
            Result.failure(ApiException(result.code, result.msg))
        } else {
            val element = result.data
            when {
                element == null || element is JsonNull -> Result.success(empty)
                element is JsonPrimitive -> Result.success(empty)
                element is JsonObject -> Result.success(json.decodeFromJsonElement(element))
                else -> Result.success(empty)
            }
        }
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

class ApiException(val code: String, message: String) : Exception("API $code: $message")
