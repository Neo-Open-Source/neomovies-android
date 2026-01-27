package com.neo.neomovies.ui.settings

import android.content.Context

private const val PREFS_NAME = "settings"
private const val KEY_PLAYER_ENGINE = "player_engine"

enum class PlayerEngineMode(val value: String) {
    EXO("exo"),
    MPV("mpv"),
    ;

    companion object {
        fun from(value: String?): PlayerEngineMode {
            return entries.firstOrNull { it.value == value } ?: EXO
        }
    }
}

object PlayerEngineManager {
    fun getMode(context: Context): PlayerEngineMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PLAYER_ENGINE, null)
        if (raw == "vlc") {
            prefs.edit().putString(KEY_PLAYER_ENGINE, PlayerEngineMode.EXO.value).apply()
            return PlayerEngineMode.EXO
        }
        return PlayerEngineMode.from(raw)
    }

    fun setMode(context: Context, mode: PlayerEngineMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PLAYER_ENGINE, mode.value).apply()
    }
}
