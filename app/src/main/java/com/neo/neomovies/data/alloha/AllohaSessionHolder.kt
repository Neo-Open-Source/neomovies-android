package com.neo.neomovies.data.alloha

/**
 * Static holder so the PlayerActivity can access the active
 * [AllohaSessionManager] that was created by WatchSelectorScreen.
 *
 * The proxy URL (127.0.0.1:8080/master.m3u8) never changes; only
 * the upstream CDN URL is swapped when the user picks a different
 * translation inside the player.
 */
object AllohaSessionHolder {
    @Volatile
    var session: AllohaSessionManager? = null

    /** Translation names for the current episode (parallel to [translationUrls]). */
    var translationNames: List<String> = emptyList()

    /** Iframe URLs for each translation (parallel to [translationNames]). */
    var translationUrls: List<String> = emptyList()

    /** Currently active translation name. */
    @Volatile
    var currentTranslation: String = ""

    /** Available quality levels from bnsi (e.g. "2160" -> URL, "1080" -> URL). */
    var qualityMap: Map<String, String> = emptyMap()

    /** Currently selected quality key (e.g. "1080"). Empty = auto. */
    @Volatile
    var currentQuality: String = ""

    /** True when quality was set by auto-selection (not manually by user). */
    @Volatile
    var isAutoQuality: Boolean = true

    fun setTranslations(names: List<String>, urls: List<String>, current: String) {
        translationNames = names
        translationUrls = urls
        currentTranslation = current
    }

    /** Skip ranges from bnsi skipTime (start/end in seconds). */
    @Volatile
    var skipRanges: List<LongRange> = emptyList()

    /** Episode list for in-player switching: iframe URLs (parallel to [episodeNames]). */
    var episodeIframeUrls: List<String> = emptyList()
    var episodeNames: List<String> = emptyList()
    /** Per-episode voiceover map: episodeIndex → (translationName → iframeUrl) */
    var episodeVoiceoverUrls: List<Map<String, String>> = emptyList()
    @Volatile
    var currentEpisodeIndex: Int = 0

    fun clear() {
        session = null
        translationNames = emptyList()
        translationUrls = emptyList()
        currentTranslation = ""
        qualityMap = emptyMap()
        currentQuality = ""
        isAutoQuality = true
        skipRanges = emptyList()
        episodeIframeUrls = emptyList()
        episodeNames = emptyList()
        episodeVoiceoverUrls = emptyList()
        currentEpisodeIndex = 0
    }
}
