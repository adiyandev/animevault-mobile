package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JikanResponse<T>(
    val data: T
)

@JsonClass(generateAdapter = true)
data class JikanPageResponse<T>(
    val data: List<T>
)

@JsonClass(generateAdapter = true)
data class Anime(
    @Json(name = "mal_id") val malId: Int,
    val title: String,
    @Json(name = "title_english") val titleEnglish: String?,
    val images: Images?,
    val score: Double?,
    val synopsis: String?,
    val episodes: Int?,
    val status: String?,
    val year: Int?,
    val genres: List<Genre>? = null,
    val mediaType: String = "anime" // "anime", "movie", or "tv"
) {
    val displayTitle: String
        get() = titleEnglish ?: title

    val posterUrl: String?
        get() = images?.webp?.bestUrl ?: images?.jpg?.bestUrl
}

@JsonClass(generateAdapter = true)
data class Genre(
    val name: String
)

@JsonClass(generateAdapter = true)
data class Images(
    val jpg: ImageUrls?,
    val webp: ImageUrls?
)

@JsonClass(generateAdapter = true)
data class ImageUrls(
    @Json(name = "image_url") val imageUrl: String?,
    @Json(name = "large_image_url") val largeImageUrl: String?
) {
    val bestUrl: String?
        get() = largeImageUrl ?: imageUrl
}

