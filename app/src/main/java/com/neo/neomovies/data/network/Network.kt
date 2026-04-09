package com.neo.neomovies.data.network

import com.neo.neomovies.BuildConfig
import com.neo.neomovies.NeoMoviesApplication
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.CacheControl
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
import java.io.File

private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB
private const val CACHE_MAX_AGE_SECONDS = 60 * 60 // 1 hour online
private const val CACHE_MAX_STALE_SECONDS = 60 * 60 * 24 * 7 // 7 days offline

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
    val cacheDir = File(authContext.cacheDir, "http_cache")
    val cache = Cache(cacheDir, CACHE_SIZE)

    val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        if (responseCount(response) >= 2) return@Authenticator null

        val prefs = authContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
        val currentAccessToken = prefs.getString("token", null)

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
                .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/refresh")
                .post(body)
                .build()
            OkHttpClient().newCall(refreshRequest).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val raw = r.body?.string().orEmpty()
                val obj = JSONObject(raw)
                // neomovies-api returns camelCase
                val access = obj.optString("accessToken", "").takeIf { it.isNotBlank() }
                val refresh = obj.optString("refreshToken", "").takeIf { it.isNotBlank() }
                if (access == null) return@use null
                access to refresh
            }
        }.getOrNull()

        if (refreshed == null) {
            Log.w("OkHttp", "Auth refresh failed; clearing stored auth")
            com.neo.neomovies.auth.NeoIdAuthManager(authContext).logout()
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
        .cache(cache)
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
        // Online: add Cache-Control max-age so responses are cached
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val cacheControl = CacheControl.Builder()
                .maxAge(CACHE_MAX_AGE_SECONDS, TimeUnit.SECONDS)
                .build()
            response.newBuilder()
                .header("Cache-Control", cacheControl.toString())
                .build()
        }
        // Offline: serve stale cache when no network
        .addInterceptor { chain ->
            var request = chain.request()
            val isOnline = com.neo.neomovies.data.network.OfflineManager.isOnline(authContext)
            if (!isOnline) {
                request = request.newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                            .onlyIfCached()
                            .maxStale(CACHE_MAX_STALE_SECONDS, TimeUnit.SECONDS)
                            .build()
                    )
                    .build()
            }
            chain.proceed(request)
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
