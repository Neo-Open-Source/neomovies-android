package com.neo.neomovies.auth

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import com.neo.neomovies.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

private const val PREFS_NAME = "neo_id_prefs"
private const val KEY_STATE = "neo_id_state"
private const val AUTH_PREFS_NAME = "auth"
private const val KEY_TOKEN = "token"
private const val KEY_UNIFIED_ID = "unified_id"
private const val KEY_EMAIL = "email"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_AVATAR = "avatar"

class NeoIdAuthManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private fun decodeJwtPayload(token: String): JSONObject? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val payload = parts[1]
            val normalized = payload.replace('-', '+').replace('_', '/')
            val padded = normalized + "===".substring((normalized.length + 3) % 4)
            val json = String(Base64.decode(padded, Base64.DEFAULT))
            JSONObject(json)
        } catch (_: Exception) {
            null
        }
    }

    fun fetchAndPersistProfile(token: String): NeoIdAuthResult {
        val request = Request.Builder()
            .url(BuildConfig.NEO_ID_BASE_URL.trimEnd('/') + "/api/user/profile")
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return NeoIdAuthResult.Error(message = "Ошибка profile: HTTP ${response.code}")
                }

                val respBody = response.body?.string() ?: return NeoIdAuthResult.Error(message = "Пустой ответ profile")
                Log.d("NeoID", "profile response: $respBody")
                val user = JSONObject(respBody)

                val email = user.optString("email", "").takeIf { it.isNotBlank() }
                val displayName = user.optString("display_name", "").takeIf { it.isNotBlank() }
                val avatar = user.optString("avatar", "")

                val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
                authPrefs.edit {
                    if (email != null) putString(KEY_EMAIL, email)
                    if (displayName != null) putString(KEY_DISPLAY_NAME, displayName)
                    putString(KEY_AVATAR, avatar)
                }

                NeoIdAuthResult.Success(token = token)
            }
        } catch (e: Exception) {
            Log.e("NeoID", "Profile fetch exception", e)
            NeoIdAuthResult.Error(message = e.message ?: "Ошибка profile")
        }
    }

    fun startLogin() {
        if (BuildConfig.NEO_ID_API_KEY.isBlank()) {
            Toast.makeText(context, "NEO_ID_API_KEY не задан", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val state = UUID.randomUUID().toString()
        prefs.edit { putString(KEY_STATE, state) }

        val callbackUrl = "neomovies://auth/callback"
        val json = JSONObject().apply {
            put("redirect_url", callbackUrl)
            put("state", state)
        }

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(BuildConfig.NEO_ID_BASE_URL.trimEnd('/') + "/api/site/login")
            .post(body)
            .header("Content-Type", "application/json")
            .header("X-API-Key", BuildConfig.NEO_ID_API_KEY)
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("NeoID", "Site login failed: HTTP ${response.code}")
                        return@use
                    }

                    val respBody = response.body?.string() ?: return@use
                    val obj = JSONObject(respBody)
                    val rawLoginUrl = obj.optString("login_url", null) ?: return@use

                    val base = BuildConfig.NEO_ID_BASE_URL.trimEnd('/')
                    val loginUrl = if (rawLoginUrl.startsWith("/")) {
                        "$base$rawLoginUrl"
                    } else {
                        rawLoginUrl
                    }

                    val uri = Uri.parse(loginUrl)
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    Handler(Looper.getMainLooper()).post {
                        try {
                            customTabsIntent.launchUrl(context, uri)
                        } catch (e: Exception) {
                            Log.e("NeoID", "Failed to launch CustomTabs", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NeoID", "Site login exception", e)
            }
        }.start()
    }

    fun handleCallback(uri: Uri): NeoIdAuthResult {
        val token = uri.getQueryParameter("token")
        val error = uri.getQueryParameter("error")
        val stateParam = uri.getQueryParameter("state")

        if (error != null) {
            return NeoIdAuthResult.Error(message = error)
        }

        if (token.isNullOrBlank()) {
            return NeoIdAuthResult.Error(message = "Пустой токен Neo ID")
        }

        if (BuildConfig.NEO_ID_API_KEY.isBlank()) {
            return NeoIdAuthResult.Error(message = "NEO_ID_API_KEY не задан")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedState = prefs.getString(KEY_STATE, null)
        if (savedState != null && stateParam != null && savedState != stateParam) {
            return NeoIdAuthResult.Error(message = "Некорректный state")
        }

        val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        val payload = decodeJwtPayload(token)
        val emailFromJwt = payload?.optString("email")?.takeIf { it.isNotBlank() }
        val nameFromJwt = (
            payload?.optString("name")?.takeIf { it.isNotBlank() }
                ?: payload?.optString("display_name")?.takeIf { it.isNotBlank() }
                ?: payload?.optString("username")?.takeIf { it.isNotBlank() }
        )

        authPrefs.edit {
            putString(KEY_TOKEN, token)
            if (!emailFromJwt.isNullOrBlank()) putString(KEY_EMAIL, emailFromJwt)
            if (!nameFromJwt.isNullOrBlank()) putString(KEY_DISPLAY_NAME, nameFromJwt)
        }

        return NeoIdAuthResult.Success(token = token)
    }

    fun verifyAndPersistUser(token: String): NeoIdAuthResult {
        if (BuildConfig.NEO_ID_API_KEY.isBlank()) {
            return NeoIdAuthResult.Error(message = "NEO_ID_API_KEY не задан")
        }

        val json = JSONObject().apply {
            put("token", token)
        }

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(BuildConfig.NEO_ID_BASE_URL.trimEnd('/') + "/api/site/verify")
            .post(body)
            .header("Content-Type", "application/json")
            .header("X-API-Key", BuildConfig.NEO_ID_API_KEY)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return NeoIdAuthResult.Error(message = "Ошибка verify: HTTP ${response.code}")
                }

                val respBody = response.body?.string() ?: return NeoIdAuthResult.Error(message = "Пустой ответ verify")
                Log.d("NeoID", "verify response: $respBody")
                val obj = JSONObject(respBody)
                val valid = obj.optBoolean("valid", false)
                if (!valid) {
                    return NeoIdAuthResult.Error(message = "Токен невалиден")
                }

                val user = obj.optJSONObject("user") ?: return NeoIdAuthResult.Error(message = "Нет user в verify")
                val unifiedId = user.optString("unified_id", "")
                val email = (
                    user.optString("email", "").takeIf { it.isNotBlank() }
                        ?: user.optString("user_email", "").takeIf { it.isNotBlank() }
                )
                val displayName = (
                    user.optString("display_name", "").takeIf { it.isNotBlank() }
                        ?: user.optString("name", "").takeIf { it.isNotBlank() }
                        ?: user.optString("username", "").takeIf { it.isNotBlank() }
                )

                val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
                authPrefs.edit {
                    putString(KEY_TOKEN, token)
                    putString(KEY_UNIFIED_ID, unifiedId)
                    if (email != null) putString(KEY_EMAIL, email) else remove(KEY_EMAIL)
                    if (displayName != null) putString(KEY_DISPLAY_NAME, displayName) else remove(KEY_DISPLAY_NAME)
                    putString(KEY_AVATAR, user.optString("avatar", ""))
                }

                NeoIdAuthResult.Success(token = token)
            }
        } catch (e: Exception) {
            NeoIdAuthResult.Error(message = e.message ?: "Ошибка verify")
        }
    }

    fun logout() {
        val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        authPrefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_UNIFIED_ID)
            remove(KEY_EMAIL)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_AVATAR)
        }
    }
}

sealed class NeoIdAuthResult {
    data class Success(val token: String) : NeoIdAuthResult()
    data class Error(val message: String) : NeoIdAuthResult()
}

