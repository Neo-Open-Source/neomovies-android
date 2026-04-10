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
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/refresh")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val respBody = response.body?.string().orEmpty()
                val obj = JSONObject(respBody)
                // API returns camelCase: accessToken, refreshToken
                val access = obj.optString("accessToken", "").takeIf { it.isNotBlank() }
                val refresh = obj.optString("refreshToken", "").takeIf { it.isNotBlank() }
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
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/profile")
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
                // API wraps in { success, data: ProfileDto }
                val root = JSONObject(respBody)
                val user = root.optJSONObject("data") ?: root

                val email = user.optString("email", "").takeIf { it.isNotBlank() }
                val displayName = user.optString("name", "").takeIf { it.isNotBlank() }
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
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/neo-id/login")
            .post(body)
            .header("Content-Type", "application/json")
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
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        val stateParam = uri.getQueryParameter("state")

        if (error != null) {
            return NeoIdAuthResult.Error(message = error)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedState = prefs.getString(KEY_STATE, null)
        if (savedState != null && stateParam != null && savedState != stateParam) {
            return NeoIdAuthResult.Error(message = "Некорректный state")
        }

        // OIDC flow: received authorization code — exchange it for tokens via neomovies-api
        if (!code.isNullOrBlank()) {
            return exchangeCodeViaApi(code, stateParam)
        }

        // Legacy service token flow
        if (token.isNullOrBlank()) {
            return NeoIdAuthResult.Error(message = "Пустой токен Neo ID")
        }

        return exchangeTokenViaApi(token, refreshToken)
    }

    private fun exchangeCodeViaApi(code: String, state: String?): NeoIdAuthResult {
        // Exchange OIDC code for neomovies-api tokens via /api/v1/auth/neo-id/callback
        val json = JSONObject().apply {
            put("code", code)
            if (state != null) put("state", state)
        }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/neo-id/callback")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return NeoIdAuthResult.Error(message = "Callback failed: HTTP ${response.code}")
                }
                val respBody = response.body?.string().orEmpty()
                Log.d("NeoID", "code exchange response: $respBody")
                val obj = JSONObject(respBody)
                val accessToken = obj.optString("accessToken", "").takeIf { it.isNotBlank() }
                    ?: return NeoIdAuthResult.Error(message = "Нет accessToken в ответе")
                val newRefreshToken = obj.optString("refreshToken", "").takeIf { it.isNotBlank() }
                val user = obj.optJSONObject("user")

                val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
                authPrefs.edit {
                    putString(KEY_TOKEN, accessToken)
                    if (!newRefreshToken.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, newRefreshToken)
                    user?.optString("email")?.takeIf { it.isNotBlank() }?.let { putString(KEY_EMAIL, it) }
                    user?.optString("name")?.takeIf { it.isNotBlank() }?.let { putString(KEY_DISPLAY_NAME, it) }
                    user?.optString("avatar")?.takeIf { it.isNotBlank() }?.let { putString(KEY_AVATAR, it) }
                }
                refreshAuthState(context, reason = "code_exchange")
                NeoIdAuthResult.Success(token = accessToken)
            }
        } catch (e: Exception) {
            Log.e("NeoID", "Code exchange exception", e)
            NeoIdAuthResult.Error(message = e.message ?: "Ошибка обмена кода")
        }
    }

    private fun exchangeTokenViaApi(token: String, refreshToken: String?): NeoIdAuthResult {
        val json = JSONObject().apply { put("access_token", token) }
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/v1/auth/neo-id/callback")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return NeoIdAuthResult.Error(message = "Callback failed: HTTP ${response.code}")
                }
                val respBody = response.body?.string().orEmpty()
                Log.d("NeoID", "callback response: $respBody")
                val obj = JSONObject(respBody)
                val accessToken = obj.optString("accessToken", "").takeIf { it.isNotBlank() }
                    ?: return NeoIdAuthResult.Error(message = "Нет accessToken в ответе")
                val newRefreshToken = obj.optString("refreshToken", "").takeIf { it.isNotBlank() }
                val user = obj.optJSONObject("user")

                val authPrefs = context.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
                authPrefs.edit {
                    putString(KEY_TOKEN, accessToken)
                    val rt = newRefreshToken ?: refreshToken
                    if (!rt.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, rt)
                    user?.optString("email")?.takeIf { it.isNotBlank() }?.let { putString(KEY_EMAIL, it) }
                    user?.optString("name")?.takeIf { it.isNotBlank() }?.let { putString(KEY_DISPLAY_NAME, it) }
                    user?.optString("avatar")?.takeIf { it.isNotBlank() }?.let { putString(KEY_AVATAR, it) }
                }
                refreshAuthState(context, reason = "callback")
                NeoIdAuthResult.Success(token = accessToken)
            }
        } catch (e: Exception) {
            Log.e("NeoID", "Callback exception", e)
            NeoIdAuthResult.Error(message = e.message ?: "Ошибка callback")
        }
    }

    fun verifyAndPersistUser(token: String): NeoIdAuthResult {
        // Legacy method — now handled by handleCallback via /api/v1/auth/neo-id/callback
        return NeoIdAuthResult.Error(message = "Use handleCallback instead")
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
