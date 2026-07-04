package com.example.data.api

import com.example.data.model.Anime
import com.example.data.model.JikanPageResponse
import com.example.data.model.JikanResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface JikanApi {
    @GET("top/anime")
    suspend fun getTrendingAnime(
        @Query("filter") filter: String = "airing",
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanPageResponse<Anime>

    @GET("top/anime")
    suspend fun getTopAnimeByType(
        @Query("type") type: String,
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanPageResponse<Anime>

    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String?,
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("order_by") orderBy: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanPageResponse<Anime>

    @GET("anime/{id}/full")
    suspend fun getAnimeDetails(
        @Path("id") id: Int
    ): JikanResponse<Anime>

    @GET("schedules")
    suspend fun getSchedules(
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanPageResponse<Anime>

    @GET("seasons/now")
    suspend fun getCurrentSeason(
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanPageResponse<Anime>

    @GET("seasons/{year}/{season}")
    suspend fun getSpecificSeason(
        @Path("year") year: Int,
        @Path("season") season: String,
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanPageResponse<Anime>
}

