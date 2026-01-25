package com.neo.neomovies.di

import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.network.MoviesApi
import com.neo.neomovies.data.network.createMoshi
import com.neo.neomovies.data.network.createOkHttpClient
import com.neo.neomovies.data.network.createRetrofit
import com.neo.neomovies.ui.home.HomeViewModel
import com.neo.neomovies.ui.list.CategoryListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { createOkHttpClient() }
    single { createMoshi() }
    single { createRetrofit(okHttpClient = get(), moshi = get()) }
    single { get<retrofit2.Retrofit>().create(MoviesApi::class.java) }

    single { MoviesRepository(api = get()) }

    viewModel { HomeViewModel(repository = get()) }
    viewModel { (category: com.neo.neomovies.ui.navigation.CategoryType) ->
        CategoryListViewModel(repository = get(), category = category)
    }
}
