package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("find/{external_id}")
    suspend fun findByExternalId(
        @Path("external_id") externalId: String,
        @Query("api_key") apiKey: String,
        @Query("external_source") externalSource: String = "imdb_id"
    ): TmdbFindResponse

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
}

@JsonClass(generateAdapter = true)
data class TmdbFindResponse(
    @Json(name = "movie_results") val movieResults: List<TmdbSearchResult>?,
    @Json(name = "tv_results") val tvResults: List<TmdbSearchResult>?
)

@JsonClass(generateAdapter = true)
data class TmdbSearchResponse(
    val results: List<TmdbSearchResult>?
)

@JsonClass(generateAdapter = true)
data class TmdbSearchResult(
    val id: Int?,
    val title: String?,
    val name: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?
)
