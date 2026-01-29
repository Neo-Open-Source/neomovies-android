package com.neo.neomovies.ui.settings

import android.content.Context

enum class SourceMode {
    COLLAPS,
    TORRENTS,
}

object SourceManager {
    private const val PREFS = "settings"
    private const val KEY = "source_mode"

    fun getMode(context: Context): SourceMode {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null)
        return runCatching { if (raw != null) SourceMode.valueOf(raw) else SourceMode.TORRENTS }.getOrDefault(SourceMode.TORRENTS)
    }

    fun setMode(context: Context, mode: SourceMode) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, mode.name).apply()
    }
}
