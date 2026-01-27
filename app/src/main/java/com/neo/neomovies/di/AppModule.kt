package com.neo.neomovies.di

import com.neo.neomovies.data.FavoritesRepository
import com.neo.neomovies.data.MoviesRepository
import com.neo.neomovies.data.SupportRepository
import com.neo.neomovies.data.torrents.JacredTorrentsRepository
import com.neo.neomovies.data.network.MoviesApi
import com.neo.neomovies.data.network.createMoshi
import com.neo.neomovies.data.network.createOkHttpClient
import com.neo.neomovies.data.network.createRetrofit
import com.neo.neomovies.ui.about.CreditsViewModel
import com.neo.neomovies.ui.favorites.FavoritesViewModel
import com.neo.neomovies.ui.home.HomeViewModel
import com.neo.neomovies.ui.list.CategoryListViewModel
import com.neo.neomovies.ui.details.DetailsViewModel
import com.neo.neomovies.ui.search.SearchViewModel
import com.neo.neomovies.ui.watch.WatchSelectorViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

val appModule = module {
    single { createOkHttpClient() }
    single { createMoshi() }
    single { createRetrofit(okHttpClient = get(), moshi = get()) }
    single { get<retrofit2.Retrofit>().create(MoviesApi::class.java) }

    single { MoviesRepository(api = get()) }
    single { FavoritesRepository(api = get()) }
    single { JacredTorrentsRepository(okHttpClient = get()) }
    single {
        SupportRepository(
            api = get(),
            moshi = get(),
            context = GlobalContext.get().get(),
        )
    }

    viewModel { HomeViewModel(repository = get()) }
    viewModel { FavoritesViewModel(repository = get()) }
    viewModel { (category: com.neo.neomovies.ui.navigation.CategoryType) ->
        CategoryListViewModel(repository = get(), category = category)
    }
    viewModel { (sourceId: String) ->
        DetailsViewModel(repository = get(), favoritesRepository = get(), sourceId = sourceId)
    }

    viewModel { (sourceId: String) ->
        WatchSelectorViewModel(moviesRepository = get(), torrentsRepository = get(), sourceId = sourceId)
    }

    viewModel { SearchViewModel(repository = get()) }
    viewModel { CreditsViewModel(repository = get()) }
}
