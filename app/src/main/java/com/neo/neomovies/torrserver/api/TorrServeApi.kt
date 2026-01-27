package com.neo.neomovies.torrserver.api

import com.google.gson.Gson
import com.neo.neomovies.torrserver.api.model.TorrentRequest
import com.neo.neomovies.torrserver.api.model.TorrentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TorrServeApi(
    private val baseUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun echo(): String = makeGetRequest("/echo")

    suspend fun listTorrents(): List<TorrentStatus> {
        val resp = makePostRequest("/torrents", TorrentRequest("list"))
        return gson.fromJson(resp, Array<TorrentStatus>::class.java).toList()
    }

    suspend fun addTorrent(link: String, title: String?, poster: String?, saveToDb: Boolean): TorrentStatus {
        val req = TorrentRequest("add", link = link, title = title, poster = poster, saveToDb = saveToDb)
        val resp = makePostRequest("/torrents", req)
        return gson.fromJson(resp, TorrentStatus::class.java)
    }

    suspend fun getTorrent(hash: String): TorrentStatus {
        val resp = makePostRequest("/torrents", TorrentRequest("get", hash = hash))
        return gson.fromJson(resp, TorrentStatus::class.java)
    }

    suspend fun removeTorrent(hash: String) {
        makePostRequest("/torrents", TorrentRequest("rem", hash = hash))
    }

    private suspend fun makeGetRequest(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.message, response.code)
            response.body?.string().orEmpty()
        }
    }

    private suspend fun makePostRequest(path: String, bodyObj: Any): String = withContext(Dispatchers.IO) {
        val json = gson.toJson(bodyObj)
        val body = json.toRequestBody(jsonMediaType)
        val request = Request.Builder().url(baseUrl + path).post(body).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.message, response.code)
            response.body?.string().orEmpty()
        }
    }

    class ApiException(message: String, val code: Int) : Exception(message)
}
