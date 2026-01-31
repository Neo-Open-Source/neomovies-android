package com.neo.neomovies.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

private const val PREFS_NAME = "settings"
private const val KEY_LANGUAGE_MODE = "language_mode"

enum class LanguageMode(val value: String) {
    SYSTEM("system"),
    RU("ru"),
    EN("en"),
    ;

    companion object {
        fun from(value: String?): LanguageMode {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}

object LanguageManager {
    fun getMode(context: Context): LanguageMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LanguageMode.from(prefs.getString(KEY_LANGUAGE_MODE, null))
    }

    fun setMode(context: Context, mode: LanguageMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE_MODE, mode.value).apply()
        apply(context)
    }

    fun apply(context: Context) {
        val mode = getMode(context)
        if (mode == LanguageMode.SYSTEM) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            return
        }

        val tag = when (mode) {
            LanguageMode.RU -> "ru"
            LanguageMode.EN -> "en"
            LanguageMode.SYSTEM -> ""
        }

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun wrap(context: Context): Context {
        val mode = getMode(context)
        if (mode == LanguageMode.SYSTEM) return context

        val locale = when (mode) {
            LanguageMode.RU -> Locale.forLanguageTag("ru")
            LanguageMode.EN -> Locale.forLanguageTag("en")
            LanguageMode.SYSTEM -> Locale.getDefault()
        }

        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
        }
        return context.createConfigurationContext(config)
    }
}
