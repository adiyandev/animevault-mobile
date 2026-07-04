package com.example.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface VideasyApi {
    @GET("shows")
    suspend fun getShows(
        @Query("page") page: Int = 0
    ): List<VideasyShow>

    @GET("search/shows")
    suspend fun searchShows(
        @Query("q") query: String
    ): List<VideasySearchResult>
}

data class VideasySearchResult(
    val score: Double,
    val show: VideasyShow
)

data class VideasyShow(
    val id: Int,
    val name: String,
    val type: String?,
    val genres: List<String>?,
    val status: String?,
    val runtime: Int?,
    val premiered: String?,
    val rating: VideasyRating?,
    val image: VideasyImage?,
    val summary: String?,
    val externals: VideasyExternals?
)

data class VideasyRating(
    val average: Double?
)

data class VideasyImage(
    val medium: String?,
    val original: String?
)

data class VideasyExternals(
    val tvrage: Int?,
    val thetvdb: Int?,
    val imdb: String?
)
