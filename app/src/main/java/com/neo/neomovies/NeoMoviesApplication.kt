package com.neo.neomovies

import android.app.Application
import com.neo.neomovies.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class NeoMoviesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NeoMoviesApplication)
            modules(appModule)
        }
    }
}
