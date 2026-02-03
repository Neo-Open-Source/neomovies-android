package com.neo.tv.presentation.player

object TvPlayerArgs {
    var urls: ArrayList<String>? = null
    var names: ArrayList<String>? = null
    var startIndex: Int = 0
    var title: String? = null
    var useExo: Boolean = false
    var useCollapsHeaders: Boolean = false
    var sourceId: String? = null
    var kinopoiskId: Int? = null
    var episodeProgressCallback: ((Int, Int, Int, Long, Long) -> Unit)? = null

    fun set(
        urls: ArrayList<String>,
        names: ArrayList<String>,
        startIndex: Int,
        title: String?,
        useExo: Boolean,
        useCollapsHeaders: Boolean,
        sourceId: String?,
        kinopoiskId: Int?,
        episodeProgressCallback: ((Int, Int, Int, Long, Long) -> Unit)?,
    ) {
        this.urls = urls
        this.names = names
        this.startIndex = startIndex
        this.title = title
        this.useExo = useExo
        this.useCollapsHeaders = useCollapsHeaders
        this.sourceId = sourceId
        this.kinopoiskId = kinopoiskId
        this.episodeProgressCallback = episodeProgressCallback
    }

    fun clear() {
        urls = null
        names = null
        startIndex = 0
        title = null
        useExo = false
        useCollapsHeaders = false
        sourceId = null
        kinopoiskId = null
        episodeProgressCallback = null
    }
}
