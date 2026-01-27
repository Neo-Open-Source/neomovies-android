package com.neo.neomovies

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.google.android.material.color.DynamicColors
import com.neo.neomovies.di.appModule
import com.neo.neomovies.torrserver.TorServerService
import com.neo.neomovies.ui.settings.LanguageManager
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin


class NeoMoviesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        DynamicColors.applyToActivitiesIfAvailable(this)

        LanguageManager.apply(this)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("torrserver_autostart", false)) {
            TorServerService.start(this)
        }

        val koinApp = startKoin {
            androidContext(this@NeoMoviesApplication)
            modules(appModule)
        }

        val okHttpClient = koinApp.koin.get<OkHttpClient>()
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .build(),
        )
    }

    companion object {
        lateinit var instance: NeoMoviesApplication
            private set
    }
}
