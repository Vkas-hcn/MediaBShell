package com.hdvideoplay.media.data.api

import com.hdvideoplay.media.data.dto.ApiResult
import com.hdvideoplay.media.data.dto.PlayReportBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Media client API. All paths keep the trailing slash.
 * Base URL must end with `/` (e.g. https://stage.hdvideoplay.top/).
 */
interface MediaApi {

    @GET("api/media/home_data/")
    suspend fun homeData(
        @Query("language") language: String = "en",
    ): ApiResult

    @GET("api/media/home_stream_data/")
    suspend fun homeStreamData(): ApiResult

    @GET("api/media/module_data/")
    suspend fun moduleData(
        @Query("id") id: Int,
        @Query("language") language: String,
        @Query("page") page: Int,
    ): ApiResult

    @GET("api/media/search/")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("language") language: String = "en",
    ): ApiResult

    @GET("api/media/filter/options/")
    suspend fun filterOptions(): ApiResult

    @GET("api/media/discover/")
    suspend fun discover(
        @Query("category") category: Int = 1,
        @Query("genre") genre: String? = null,
        @Query("release_year") releaseYear: String? = null,
        @Query("country") country: String? = null,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US",
    ): ApiResult

    @GET("api/media/streaming/")
    suspend fun streaming(): ApiResult

    @GET("api/media/daily/")
    suspend fun daily(
        @Query("daily_style") dailyStyle: String = "2",
    ): ApiResult

    // ---- Reserved for employee-2 (detail / play / report) ----

    @GET("api/media/detail/")
    suspend fun detail(
        @Query("media_id") mediaId: Int,
        @Query("language") language: String = "en",
    ): ApiResult

    @GET("api/media/outside/links/")
    suspend fun outsideLinks(
        @Query("media_id") mediaId: Int,
        @Query("episode") episode: Int,
        @Query("language") language: String = "en",
    ): ApiResult

    @POST("api/media/play_report/")
    suspend fun playReport(
        @Body body: PlayReportBody,
    ): ApiResult
}
