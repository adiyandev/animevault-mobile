package com.example.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface TvMazeApi {
    @GET("shows")
    suspend fun getShows(
        @Query("page") page: Int = 0
    ): List<TvMazeShow>

    @GET("search/shows")
    suspend fun searchShows(
        @Query("q") query: String
    ): List<TvMazeSearchResult>
}

data class TvMazeSearchResult(
    val score: Double,
    val show: TvMazeShow
)

data class TvMazeShow(
    val id: Int,
    val name: String,
    val type: String?,
    val genres: List<String>?,
    val status: String?,
    val runtime: Int?,
    val premiered: String?,
    val rating: TvMazeRating?,
    val image: TvMazeImage?,
    val summary: String?,
    val externals: TvMazeExternals?
)

data class TvMazeRating(
    val average: Double?
)

data class TvMazeImage(
    val medium: String?,
    val original: String?
)

data class TvMazeExternals(
    val tvrage: Int?,
    val thetvdb: Int?,
    val imdb: String?
)
