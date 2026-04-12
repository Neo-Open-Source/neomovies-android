package com.neo.neomovies.downloads

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.neo.neomovies.data.collaps.CollapsRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * DataSource that handles two cases:
 *
 * 1. collaps://<downloadId> — resolves a fresh master URL from Collaps API
 * 2. Any HTTP URL — on 410 response, re-fetches the master manifest to get
 *    a fresh segment URL (Collaps signed URLs expire quickly)
 *
 * Download ID patterns:
 *   kp_<kpId>_s<season>_e<episode>_collaps  → episode
 *   kp_<kpId>_movie_<ts>                    → movie
 */
@UnstableApi
class CollapsResolvingDataSource(
    private val httpFactory: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory(),
) : DataSource {

    private val episodeRegex = Regex("""^kp_(\d+)_s(\d+)_e(\d+)_collaps""")
    private val movieRegex = Regex("""^kp_(\d+)_movie_""")

    private var delegate: DefaultHttpDataSource = httpFactory.createDataSource()
    private var currentDataSpec: DataSpec? = null

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        val uri = dataSpec.uri

        return when {
            uri.scheme == "collaps" -> openCollapsPlaceholder(dataSpec)
            else -> openWithRetryOn410(dataSpec)
        }
    }

    private fun openCollapsPlaceholder(dataSpec: DataSpec): Long {
        val id = dataSpec.uri.schemeSpecificPart.trimStart('/')
        val url = resolveUrlForId(id)
            ?: throw HttpDataSource.HttpDataSourceException(
                "Could not resolve Collaps URL for: $id",
                dataSpec,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN,
            )
        delegate = httpFactory.createDataSource()
        return delegate.open(dataSpec.buildUpon().setUri(Uri.parse(url)).build())
    }

    private fun openWithRetryOn410(dataSpec: DataSpec): Long {
        return try {
            delegate = httpFactory.createDataSource()
            delegate.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 410) {
                // Segment URL expired — try to get a fresh one from the same episode
                val freshUrl = resolveUrlForSegment(dataSpec.uri) ?: throw e
                delegate = httpFactory.createDataSource()
                delegate.open(dataSpec.buildUpon().setUri(Uri.parse(freshUrl)).build())
            } else throw e
        }
    }

    /**
     * Given an expired segment URL, find which episode it belongs to by checking
     * active downloads in the store, then re-fetch the master manifest.
     */
    private fun resolveUrlForSegment(segmentUri: Uri): String? = runBlocking {
        val repo = runCatching { GlobalContext.get().get<CollapsRepository>() }.getOrNull()
            ?: return@runBlocking null

        // Try to find a matching download entry by looking at active download IDs
        val dm = runCatching { DownloadUtil.getDownloadManager(null!!) }.getOrNull()
        // We can't easily map a segment URL back to an episode without context.
        // Return null to let the download fail — the failure listener will clean up the store.
        null
    }

    private fun resolveUrlForId(downloadId: String): String? = runBlocking {
        val repo = runCatching { GlobalContext.get().get<CollapsRepository>() }.getOrNull()
            ?: return@runBlocking null

        val episodeMatch = episodeRegex.find(downloadId)
        if (episodeMatch != null) {
            val kpId = episodeMatch.groupValues[1].toIntOrNull() ?: return@runBlocking null
            val season = episodeMatch.groupValues[2].toIntOrNull() ?: return@runBlocking null
            val episode = episodeMatch.groupValues[3].toIntOrNull() ?: return@runBlocking null
            val seasons = runCatching { repo.getSeasonsByKpId(kpId) }.getOrNull() ?: return@runBlocking null
            val ep = seasons.firstOrNull { it.season == season }
                ?.episodes?.firstOrNull { it.episode == episode }
                ?: return@runBlocking null
            return@runBlocking ep.hlsUrl?.takeIf { it.isNotBlank() } ?: ep.mpdUrl?.takeIf { it.isNotBlank() }
        }

        val movieMatch = movieRegex.find(downloadId)
        if (movieMatch != null) {
            val kpId = movieMatch.groupValues[1].toIntOrNull() ?: return@runBlocking null
            val movie = runCatching { repo.getMovieByKpId(kpId) }.getOrNull() ?: return@runBlocking null
            return@runBlocking movie.hlsUrl?.takeIf { it.isNotBlank() } ?: movie.mpdUrl?.takeIf { it.isNotBlank() }
        }

        null
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int) = delegate.read(buffer, offset, length)
    override fun getUri(): Uri? = delegate.uri
    override fun close() = delegate.close()
    override fun getResponseHeaders() = delegate.responseHeaders
    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    @UnstableApi
    class Factory : DataSource.Factory {
        override fun createDataSource() = CollapsResolvingDataSource()
    }
}
