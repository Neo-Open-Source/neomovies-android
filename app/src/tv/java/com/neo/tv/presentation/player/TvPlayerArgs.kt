package com.neo.tv.presentation.player

object TvPlayerArgs {
    var urls: ArrayList<String>? = null
    var names: ArrayList<String>? = null
    var startIndex: Int = 0
    var title: String? = null
    var useExo: Boolean = false
    var useCollapsHeaders: Boolean = false

    fun set(
        urls: ArrayList<String>,
        names: ArrayList<String>,
        startIndex: Int,
        title: String?,
        useExo: Boolean,
        useCollapsHeaders: Boolean,
    ) {
        this.urls = urls
        this.names = names
        this.startIndex = startIndex
        this.title = title
        this.useExo = useExo
        this.useCollapsHeaders = useCollapsHeaders
    }

    fun clear() {
        urls = null
        names = null
        startIndex = 0
        title = null
        useExo = false
        useCollapsHeaders = false
    }
}
