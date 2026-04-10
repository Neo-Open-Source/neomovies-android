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

        com.neo.neomovies.data.network.OfflineManager.initFromConnectivity(this)
        com.neo.neomovies.auth.NeoIdAuthManager.refreshAuthState(this, reason = "app_start")

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

        // Remove any stale Collaps downloads from Media3's DB —
        // Collaps downloads are managed by CollapsDownloadQueue/DownloadsStore,
        // not by ExoPlayer DownloadManager — nothing to purge here.
    }

    private fun purgeStalledCollapsDownloads() {
        // No-op: Collaps downloads are stored in DownloadsStore (JSON), not in ExoPlayer DownloadManager.
        // Attempting to remove them from DownloadManager causes "Failed to remove nonexistent download" spam.
    }

    companion object {
        lateinit var instance: NeoMoviesApplication
            private set
    }
}
