package com.neo.neomovies.alloha

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

class AllohaPlayerSession(context: Context) {
    val headers = ConcurrentHashMap<String, String>()
    val proxy = HlsProxyServer(headers).also { it.start() }
    val qualities = mutableMapOf<String, String>()
    val translations = mutableListOf<Pair<String, String>>() // name -> iframeUrl
    val episodes = mutableListOf<Pair<String, String>>()    // displayName -> iframeUrl
    var currentEpisodeIndex = 0
    val parser = AllohaParser(context)
}
