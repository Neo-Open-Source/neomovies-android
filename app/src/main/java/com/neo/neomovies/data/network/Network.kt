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
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

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

    val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        // Avoid infinite loops
        if (responseCount(response) >= 2) return@Authenticator null

        val prefs = authContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val currentAccessToken = prefs.getString("token", null)

        // If the request already used a different token than current, retry once with current.
        val requestToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (!currentAccessToken.isNullOrBlank() && requestToken != null && requestToken != currentAccessToken) {
            return@Authenticator response.request.newBuilder()
                .header("Authorization", "Bearer $currentAccessToken")
                .build()
        }

        val refreshToken = prefs.getString("refresh_token", null)
        if (refreshToken.isNullOrBlank()) return@Authenticator null

        val refreshed = runCatching {
            val json = JSONObject().apply { put("refresh_token", refreshToken) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val refreshRequest = Request.Builder()
                .url(BuildConfig.NEO_ID_BASE_URL.trimEnd('/') + "/api/auth/refresh")
                .post(body)
                .build()
            // Use a bare client without this authenticator to prevent recursion.
            OkHttpClient().newCall(refreshRequest).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val raw = r.body?.string().orEmpty()
                val obj = JSONObject(raw)
                val access = obj.optString("access_token", "").takeIf { it.isNotBlank() }
                val refresh = obj.optString("refresh_token", "").takeIf { it.isNotBlank() }
                if (access == null) return@use null
                access to refresh
            }
        }.getOrNull()

        if (refreshed == null) {
            prefs.edit().remove("token").remove("refresh_token").apply()
            return@Authenticator null
        }

        val newAccess = refreshed.first
        val newRefresh = refreshed.second
        prefs.edit().putString("token", newAccess).apply()
        if (!newRefresh.isNullOrBlank()) {
            prefs.edit().putString("refresh_token", newRefresh).apply()
        }

        response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    return OkHttpClient.Builder()
        .authenticator(tokenAuthenticator)
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

private fun responseCount(response: Response): Int {
    var r: Response? = response
    var result = 1
    while (true) {
        r = r?.priorResponse ?: break
        result++
    }
    return result
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
