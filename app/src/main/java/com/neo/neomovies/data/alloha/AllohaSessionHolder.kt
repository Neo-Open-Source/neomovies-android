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

    fun setTranslations(names: List<String>, urls: List<String>, current: String) {
        translationNames = names
        translationUrls = urls
        currentTranslation = current
    }

    fun clear() {
        session = null
        translationNames = emptyList()
        translationUrls = emptyList()
        currentTranslation = ""
    }
}
