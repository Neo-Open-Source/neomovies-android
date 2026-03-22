package com.neo.neomovies.downloads

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class DownloadSource {
    COLLAPS,
    TORRENT,
}

enum class DownloadType {
    MOVIE,
    EPISODE,
}

data class DownloadEntry(
    val id: String,
    val type: DownloadType,
    val source: DownloadSource,
    val title: String,
    val posterUrl: String?,
    val originalUrl: String?,
    val filePath: String,
    val fileSize: Long,
    val createdAt: Long,
    val showId: String? = null,
    val showTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

class DownloadsStore(private val context: Context) {
    private val lock = Any()

    private fun storeFile(): File {
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "index.json")
    }

    fun loadAll(): List<DownloadEntry> {
        synchronized(lock) {
            val file = storeFile()
            if (!file.exists()) return emptyList()
            val raw = file.readText()
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val list = ArrayList<DownloadEntry>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(fromJson(obj))
                }
                list
            }.getOrDefault(emptyList())
        }
    }

    fun upsert(entry: DownloadEntry) {
        synchronized(lock) {
            val all = loadAll().toMutableList()
            val idx = all.indexOfFirst { it.id == entry.id }
            if (idx >= 0) all[idx] = entry else all.add(entry)
            saveAll(all)
        }
    }

    fun removeById(id: String) {
        synchronized(lock) {
            val all = loadAll().filterNot { it.id == id }
            saveAll(all)
        }
    }

    private fun saveAll(list: List<DownloadEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        storeFile().writeText(arr.toString())
    }

    private fun toJson(entry: DownloadEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("type", entry.type.name)
            put("source", entry.source.name)
            put("title", entry.title)
            put("poster_url", entry.posterUrl ?: JSONObject.NULL)
            put("original_url", entry.originalUrl ?: JSONObject.NULL)
            put("file_path", entry.filePath)
            put("file_size", entry.fileSize)
            put("created_at", entry.createdAt)
            put("show_id", entry.showId ?: JSONObject.NULL)
            put("show_title", entry.showTitle ?: JSONObject.NULL)
            put("season_number", entry.seasonNumber ?: JSONObject.NULL)
            put("episode_number", entry.episodeNumber ?: JSONObject.NULL)
        }
    }

    private fun fromJson(obj: JSONObject): DownloadEntry {
        return DownloadEntry(
            id = obj.optString("id"),
            type = DownloadType.valueOf(obj.optString("type", DownloadType.MOVIE.name)),
            source = DownloadSource.valueOf(obj.optString("source", DownloadSource.COLLAPS.name)),
            title = obj.optString("title"),
            posterUrl = obj.optString("poster_url").takeIf { it.isNotBlank() },
            originalUrl = obj.optString("original_url").takeIf { it.isNotBlank() },
            filePath = obj.optString("file_path"),
            fileSize = obj.optLong("file_size", 0L),
            createdAt = obj.optLong("created_at", SystemClock.elapsedRealtime()),
            showId = obj.optString("show_id").takeIf { it.isNotBlank() },
            showTitle = obj.optString("show_title").takeIf { it.isNotBlank() },
            seasonNumber = obj.optInt("season_number", -1).takeIf { it > 0 },
            episodeNumber = obj.optInt("episode_number", -1).takeIf { it > 0 },
        )
    }
}
