package com.neo.neomovies.update

import android.content.Context
import android.content.SharedPreferences
import com.neo.neomovies.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

private const val GITHUB_RELEASES_URL =
    "https://api.github.com/repos/Neo-Open-Source/neomovies-android/releases?per_page=20"

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val isPrerelease: Boolean,
    val htmlUrl: String,
    val apkUrl: String?,
)

object UpdateChecker {

    private val client = OkHttpClient()

    /** Returns the latest release for the given channel, or null if up-to-date / error. */
    suspend fun checkForUpdate(context: Context): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val wantPrerelease = getUpdateChannel(context) == UpdateChannel.PRERELEASE
            val json = client.newCall(Request.Builder().url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github+json").build())
                .execute().use { it.body?.string() } ?: return@withContext null

            val releases = JSONArray(json)
            for (i in 0 until releases.length()) {
                val r = releases.getJSONObject(i)
                val isPre = r.optBoolean("prerelease", false)
                if (!wantPrerelease && isPre) continue  // skip prereleases on stable channel
                if (wantPrerelease && !isPre) continue  // skip stable on prerelease channel

                val tag = r.optString("tag_name", "").removePrefix("v")
                if (tag.isBlank()) continue

                val remoteCode = computeVersionCode(tag)
                if (remoteCode <= BuildConfig.VERSION_CODE) return@withContext null  // up to date

                // Find arm64 APK asset, fallback to universal
                val assets = r.optJSONArray("assets") ?: JSONArray()
                var apkUrl: String? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val name = a.optString("name", "")
                    if (name.endsWith(".apk") && "tv" !in name) {
                        val url = a.optString("browser_download_url", "")
                        if ("arm64" in name) { apkUrl = url; break }
                        if (apkUrl == null) apkUrl = url
                    }
                }

                return@withContext ReleaseInfo(
                    tagName = r.optString("tag_name"),
                    versionName = tag,
                    isPrerelease = isPre,
                    htmlUrl = r.optString("html_url"),
                    apkUrl = apkUrl,
                )
            }
            null
        } catch (_: Exception) { null }
    }

    private fun computeVersionCode(versionName: String): Int {
        val numeric = versionName.substringBefore('-').substringBefore('+')
        val parts = numeric.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val base = (major * 1_000_000) + (minor * 10_000) + (patch * 100)
        val pre = Regex("pre(\\d+)", RegexOption.IGNORE_CASE).find(versionName)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return base + pre
    }

    // ── Channel preference ────────────────────────────────────────────────────

    enum class UpdateChannel { STABLE, PRERELEASE }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    fun getUpdateChannel(context: Context): UpdateChannel {
        val saved = prefs(context).getString("channel", null)
        if (saved != null) return UpdateChannel.valueOf(saved)
        // Default: match current build type
        return if (BuildConfig.PRE_RELEASE) UpdateChannel.PRERELEASE else UpdateChannel.STABLE
    }

    fun setUpdateChannel(context: Context, channel: UpdateChannel) {
        prefs(context).edit().putString("channel", channel.name).apply()
    }
}
