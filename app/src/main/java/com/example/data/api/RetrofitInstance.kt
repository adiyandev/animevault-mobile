package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitInstance {
    private const val MEDIA_API_BASE_URL = "https://api.tvmaze.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    val api: JikanApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/v4/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JikanApi::class.java)
    }

    val tvMazeApi: TvMazeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.tvmaze.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TvMazeApi::class.java)
    }

    val videasyApi: VideasyApi by lazy {
        Retrofit.Builder()
            .baseUrl(MEDIA_API_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VideasyApi::class.java)
    }
}
