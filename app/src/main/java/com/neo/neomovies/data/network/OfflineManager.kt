package com.neo.neomovies.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OfflineManager {
    private val offlineFlow = MutableStateFlow(false)
    private var locked = false

    fun isOffline(): StateFlow<Boolean> = offlineFlow.asStateFlow()

    fun enterOfflineOnce() {
        if (locked) return
        offlineFlow.value = true
        locked = true
    }

    fun initFromConnectivity(context: Context) {
        if (locked) return
        if (!hasNetwork(context)) {
            enterOfflineOnce()
        }
    }

    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
