package com.neo.neomovies.data.torrents

import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class JacredTorrentsRepository(
    private val okHttpClient: OkHttpClient,
) {

    suspend fun search(query: String): List<JacredTorrent> {
        if (query.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url("https://jacred.xyz/api/v1.0/torrents?search=${java.net.URLEncoder.encode(query, "UTF-8")}&apikey=null")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36",
                    )
                    .build()

            val response = okHttpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    error("Jacred error: HTTP ${it.code}")
                }

                val body = it.body?.string().orEmpty()
                val json = JSONArray(body)

                val torrents = ArrayList<JacredTorrent>(json.length())
                for (i in 0 until json.length()) {
                    val o = json.getJSONObject(i)
                    torrents.add(
                        JacredTorrent(
                            tracker = o.optString("tracker", ""),
                            url = o.optString("url", ""),
                            title = o.optString("title", ""),
                            size = o.optLong("size", 0L),
                            sizeName = o.optString("sizeName", ""),
                            createTime = o.optString("createTime", ""),
                            sid = o.optInt("sid", 0),
                            pir = o.optInt("pir", 0),
                            magnet = o.optString("magnet", ""),
                            name = o.optString("name", ""),
                            originalName = o.optString("originalname", ""),
                            released = o.optInt("relased", 0),
                            videoType = o.optString("videotype", ""),
                            quality = o.optInt("quality", 0),
                        )
                    )
                }

                val map = LinkedHashMap<String, JacredTorrent>()
                for (t in torrents) {
                    if (t.sid > 0 && t.magnet.isNotBlank()) {
                        map.putIfAbsent(t.magnet, t)
                    }
                }

                map.values
                    .sortedWith(compareByDescending<JacredTorrent> { it.sid }.thenByDescending { it.pir })
            }
        }
    }
}

data class JacredTorrent(
    val tracker: String,
    val url: String,
    val title: String,
    val size: Long,
    val sizeName: String,
    val createTime: String,
    val sid: Int,
    val pir: Int,
    val magnet: String,
    val name: String,
    val originalName: String,
    val released: Int,
    val videoType: String,
    val quality: Int,
)
