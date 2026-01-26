package com.neo.neomovies.data.network

import com.neo.neomovies.BuildConfig
import com.neo.neomovies.NeoMoviesApplication
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import android.util.Log
import java.util.concurrent.TimeUnit
import android.content.Context

fun createOkHttpClient(): OkHttpClient {
    val logger = HttpLoggingInterceptor { message ->
        Log.d("OkHttp", message)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
    }

    val authContext: Context = NeoMoviesApplication.instance.applicationContext

    return OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val prefs = authContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
            val token = prefs.getString("token", null)

            val newRequest = if (!token.isNullOrBlank()) {
                request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                request
            }

            chain.proceed(newRequest)
        }
        .addInterceptor(logger)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

fun createMoshi(): Moshi {
    return Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
}

fun createRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
    return Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
}
