package com.neo.neomovies.alloha

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class AllohaRepository(
    private val token: String = "ffbd312217e27c4245f2678afe1881",
) {
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
    })

    private val client: OkHttpClient = run {
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, trustAllCerts, SecureRandom())
        OkHttpClient.Builder()
            .sslSocketFactory(sc.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
    data class Translation(val id: String, val name: String, val iframeUrl: String)
    data class Episode(val num: Int, val translations: List<Translation>)
    data class Season(val num: Int, val episodes: List<Episode>)
    data class Content(
        val title: String,
        val isSerial: Boolean,
        val movieIframe: String?,
        val seasons: List<Season>,
    )

    suspend fun getContent(kpId: Int): Content = withContext(Dispatchers.IO) {
        val url = "https://api.alloha.tv/?token=$token&kp=$kpId"
        val request = Request.Builder().url(url).build()
        val json = client.newCall(request).execute().use { it.body?.string() ?: "" }
        parseContent(json)
    }

    private fun parseContent(json: String): Content {
        val dataObj = JSONObject(json).getJSONObject("data")
        val title = dataObj.optString("name", "Unknown")
        val seasonsObj = dataObj.optJSONObject("seasons")

        if (seasonsObj != null) {
            val seasons = mutableListOf<Season>()
            seasonsObj.keys().forEach { sKey ->
                val sObj = seasonsObj.getJSONObject(sKey)
                val episodesObj = sObj.optJSONObject("episodes") ?: return@forEach
                val episodes = mutableListOf<Episode>()
                episodesObj.keys().forEach { eKey ->
                    val eObj = episodesObj.getJSONObject(eKey)
                    val transObj = eObj.optJSONObject("translation") ?: return@forEach
                    val translations = mutableListOf<Translation>()
                    transObj.keys().forEach { tKey ->
                        val tData = transObj.getJSONObject(tKey)
                        val iframe = tData.optString("iframe", "")
                        if (iframe.isNotBlank()) {
                            translations.add(Translation(
                                id = tKey,
                                name = tData.optString("translation", "Unknown"),
                                iframeUrl = iframe,
                            ))
                        }
                    }
                    if (translations.isNotEmpty()) {
                        episodes.add(Episode(eKey.toIntOrNull() ?: 0, translations.sortedBy { it.name }))
                    }
                }
                if (episodes.isNotEmpty()) {
                    seasons.add(Season(sKey.toIntOrNull() ?: 0, episodes.sortedBy { it.num }))
                }
            }
            return Content(title, true, null, seasons.sortedBy { it.num })
        } else {
            val iframe = dataObj.optString("iframe", "")
            return Content(title, false, iframe.takeIf { it.isNotBlank() }, emptyList())
        }
    }
}
