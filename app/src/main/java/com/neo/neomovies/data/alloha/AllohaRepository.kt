package com.neo.neomovies.data.alloha

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val TAG = "AllohaRepository"

data class AllohaTranslation(
    val id: String,
    val name: String,
    val iframeUrl: String,
)

data class AllohaEpisode(
    val season: Int,
    val episode: Int,
    val translations: List<AllohaTranslation>,
)

data class AllohaSeason(
    val season: Int,
    val episodes: List<AllohaEpisode>,
)

data class AllohaMovie(
    val title: String,
    val iframeUrl: String,
    val translations: List<AllohaTranslation>,
)

data class AllohaApiResult(
    val title: String,
    val isSerial: Boolean,
    val movie: AllohaMovie?,
    val seasons: List<AllohaSeason>,
)

class AllohaRepository(
    private val okHttpClient: OkHttpClient,
    private val token: String = "ffbd312217e27c4245f2678afe1881",
) {

    private fun buildTrustingClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
        })
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, trustAllCerts, SecureRandom())
        return okHttpClient.newBuilder()
            .sslSocketFactory(sc.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    suspend fun fetchByKpId(kpId: Int): AllohaApiResult = withContext(Dispatchers.IO) {
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        val encodedKp = URLEncoder.encode(kpId.toString(), "UTF-8")
        val apiUrl = "https://api.alloha.tv/?token=$encodedToken&kp=$encodedKp"

        Log.d(TAG, "fetchByKpId: kpId=$kpId url=$apiUrl")

        val client = buildTrustingClient()
        val request = Request.Builder().url(apiUrl).build()
        val jsonStr = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Alloha API returned ${response.code}")
            }
            response.body?.string() ?: throw RuntimeException("Empty response body")
        }

        val dataObj = JSONObject(jsonStr).getJSONObject("data")
        val title = dataObj.optString("name", "Unknown")
        val seasonsObj = dataObj.optJSONObject("seasons")

        if (seasonsObj != null) {
            val parsedSeasons = mutableListOf<AllohaSeason>()
            seasonsObj.keys().forEach { sKey ->
                val sObj = seasonsObj.getJSONObject(sKey)
                val episodesObj = sObj.optJSONObject("episodes") ?: return@forEach
                val parsedEpisodes = mutableListOf<AllohaEpisode>()

                episodesObj.keys().forEach { eKey ->
                    val eObj = episodesObj.getJSONObject(eKey)
                    val transObj = eObj.optJSONObject("translation") ?: return@forEach
                    val parsedTrans = mutableListOf<AllohaTranslation>()

                    transObj.keys().forEach { tKey ->
                        val tData = transObj.getJSONObject(tKey)
                        val transName = tData.optString("translation", "Unknown")
                        val iframe = tData.optString("iframe", "")
                        if (iframe.isNotBlank()) {
                            parsedTrans.add(
                                AllohaTranslation(
                                    id = tKey,
                                    name = transName,
                                    iframeUrl = iframe,
                                )
                            )
                        }
                    }
                    val seasonNum = sKey.toIntOrNull() ?: return@forEach
                    val episodeNum = eKey.toIntOrNull() ?: return@forEach
                    parsedEpisodes.add(
                        AllohaEpisode(
                            season = seasonNum,
                            episode = episodeNum,
                            translations = parsedTrans.sortedBy { it.name },
                        )
                    )
                }
                val seasonNum = sKey.toIntOrNull() ?: return@forEach
                parsedSeasons.add(
                    AllohaSeason(
                        season = seasonNum,
                        episodes = parsedEpisodes.sortedBy { it.episode },
                    )
                )
            }

            val sortedSeasons = parsedSeasons.sortedBy { it.season }
            Log.d(TAG, "fetchByKpId: returning ${sortedSeasons.size} seasons")
            AllohaApiResult(
                title = title,
                isSerial = true,
                movie = null,
                seasons = sortedSeasons,
            )
        } else {
            val iframe = dataObj.optString("iframe", "")
            Log.d(TAG, "fetchByKpId: movie iframe=$iframe")
            AllohaApiResult(
                title = title,
                isSerial = false,
                movie = if (iframe.isNotBlank()) {
                    AllohaMovie(
                        title = title,
                        iframeUrl = iframe,
                        translations = listOf(
                            AllohaTranslation(
                                id = "default",
                                name = title,
                                iframeUrl = iframe,
                            )
                        ),
                    )
                } else null,
                seasons = emptyList(),
            )
        }
    }
}
