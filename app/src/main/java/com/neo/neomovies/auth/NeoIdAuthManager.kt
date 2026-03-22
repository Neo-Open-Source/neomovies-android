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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "neo_id_prefs"
private const val KEY_STATE = "neo_id_state"
private const val AUTH_PREFS_NAME = "auth"
private const val KEY_TOKEN = "token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_UNIFIED_ID = "unified_id"
private const val KEY_EMAIL = "email"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_AVATAR = "avatar"

class NeoIdAuthManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {

    private fun refreshAccessToken(refreshToken: String): Pair<String, String?>? {
        val json = JSONObject().apply { put("refresh_token", refreshToken) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(BuildConfig.NEO_ID_BASE_URL.trimEnd('/') + "/api/auth/refresh")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val respBody = response.body?.string().orEmpty()
                val obj = JSONObject(respBody)
                val access = obj.optString("access_token", "").takeIf { it.isNotBlank() }
                val refresh = obj.optString("refresh_token", "").takeIf { it.isNotBlank() }
                if (access == null) return@use null
                access to refresh
            }
        } catch (_: Exception) {
            null
        }
    }

    fun ensureValidAccessToken(): String? {
        val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        val token = authPrefs.getString(KEY_TOKEN, null)
        if (token.isNullOrBlank()) return null
        if (!isTokenExpired(token)) return token

        val refreshToken = authPrefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrBlank()) {
            clearAuth()
            return null
        }

        val refreshed = refreshAccessToken(refreshToken)
        if (refreshed == null) {
            Log.w("NeoID", "Refresh failed: invalid or expired refresh token")
            clearAuth()
            return null
        }

        val newAccess = refreshed.first
        val newRefresh = refreshed.second
        authPrefs.edit {
            putString(KEY_TOKEN, newAccess)
            if (!newRefresh.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, newRefresh)
        }
        refreshAuthState(context, reason = "token_refreshed")
        return newAccess
    }

    fun fetchAndPersistProfile(): NeoIdAuthResult {
        val token = ensureValidAccessToken()
            ?: return NeoIdAuthResult.Error(message = "Токен не найден или истек")

        val request = Request.Builder()
            .url(BuildConfig.NEO_ID_BASE_URL.trimEnd('/') + "/api/user/profile")
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        Log.w("NeoID", "Profile fetch unauthorized; clearing auth")
                        clearAuth()
                    }
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
        val refreshToken = uri.getQueryParameter("refresh_token")
            ?: uri.getQueryParameter("refreshToken")
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
            if (!refreshToken.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, refreshToken)
            if (!emailFromJwt.isNullOrBlank()) putString(KEY_EMAIL, emailFromJwt)
            if (!nameFromJwt.isNullOrBlank()) putString(KEY_DISPLAY_NAME, nameFromJwt)
        }
        refreshAuthState(context, reason = "callback")

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
                refreshAuthState(context, reason = "verify")

                NeoIdAuthResult.Success(token = token)
            }
        } catch (e: Exception) {
            NeoIdAuthResult.Error(message = e.message ?: "Ошибка verify")
        }
    }

    fun logout() {
        clearAuth()
    }

    fun isAuthorized(): Boolean {
        val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        val token = authPrefs.getString(KEY_TOKEN, null) ?: return false
        return !isTokenExpired(token)
    }

    private fun clearAuth() {
        val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        authPrefs.edit {
            remove(KEY_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_UNIFIED_ID)
            remove(KEY_EMAIL)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_AVATAR)
        }
        refreshAuthState(context, reason = "cleared")
    }

    private fun isTokenExpired(token: String, leewaySeconds: Long = 60): Boolean {
        return isTokenExpiredStatic(token, leewaySeconds)
    }

    private fun decodeJwtPayload(token: String): JSONObject? {
        return decodeJwtPayloadStatic(token)
    }

    companion object {
        data class AuthState(
            val isAuthorized: Boolean,
            val reason: String? = null,
        )

        private val authStateFlow = MutableStateFlow(AuthState(isAuthorized = false))

        fun authState(): StateFlow<AuthState> = authStateFlow.asStateFlow()

        fun refreshAuthState(context: Context, reason: String? = null) {
            val prefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
            val token = prefs.getString(KEY_TOKEN, null)
            val isValid = !token.isNullOrBlank() && !isTokenExpiredStatic(token)
            authStateFlow.value = AuthState(isAuthorized = isValid, reason = reason)
        }

        private fun isTokenExpiredStatic(token: String, leewaySeconds: Long = 60): Boolean {
            val payload = decodeJwtPayloadStatic(token) ?: return true
            val exp = payload.optLong("exp", 0L)
            if (exp <= 0L) return true
            val now = System.currentTimeMillis() / 1000
            return exp <= (now + leewaySeconds)
        }

        private fun decodeJwtPayloadStatic(token: String): JSONObject? {
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
    }
}

sealed class NeoIdAuthResult {
    data class Success(val token: String) : NeoIdAuthResult()
    data class Error(val message: String) : NeoIdAuthResult()
}
