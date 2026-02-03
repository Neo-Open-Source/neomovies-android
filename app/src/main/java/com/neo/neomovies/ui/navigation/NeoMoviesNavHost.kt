package com.neo.neomovies.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neo.neomovies.ui.details.DetailsScreen
import com.neo.neomovies.ui.home.HomeScreen
import com.neo.neomovies.ui.list.CategoryListScreen
import com.neo.neomovies.ui.search.SearchScreen
import com.neo.neomovies.ui.watch.WatchSelectorScreen
import com.neo.player.PlayerActivity

@Composable
fun NeoMoviesNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoute.Home.route,
        modifier = modifier,
    ) {
        composable(NavRoute.Home.route) {
            HomeScreen(
                onOpenCategory = { type -> navController.navigate(NavRoute.CategoryList.create(type)) },
                onOpenDetails = { sourceId -> navController.navigate(NavRoute.Details.create(sourceId)) },
                onOpenSearch = { navController.navigate(NavRoute.Search.route) },
            )
        }

        composable(NavRoute.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenDetails = { sourceId -> navController.navigate(NavRoute.Details.create(sourceId)) },
            )
        }

        composable(
            route = NavRoute.CategoryList.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            val type = CategoryType.from(entry.arguments?.getString("type"))
            CategoryListScreen(
                categoryType = type,
                onBack = { navController.popBackStack() },
                onOpenDetails = { sourceId -> navController.navigate(NavRoute.Details.create(sourceId)) },
            )
        }

        composable(
            route = NavRoute.Details.route,
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { entry ->
            val sourceId = entry.arguments?.getString("sourceId") ?: return@composable
            DetailsScreen(
                sourceId = sourceId,
                onBack = { navController.popBackStack() },
                onWatch = { navController.navigate(NavRoute.WatchSelector.create(sourceId)) },
            )
        }

        composable(
            route = NavRoute.WatchSelector.route,
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { entry ->
            val sourceId = entry.arguments?.getString("sourceId") ?: return@composable
            val context = LocalContext.current
            WatchSelectorScreen(
                sourceId = sourceId,
                onBack = { navController.popBackStack() },
                onWatch = { urls, names, startIndex, title, episodeProgressCallback ->
                    val mode = com.neo.neomovies.ui.settings.PlayerEngineManager.getMode(context)
                    val useCollapsHeaders =
                        com.neo.neomovies.ui.settings.SourceManager.getMode(context) ==
                            com.neo.neomovies.ui.settings.SourceMode.COLLAPS
                    val intent =
                        when (mode) {
                            com.neo.neomovies.ui.settings.PlayerEngineMode.EXO ->
                                com.neo.player.PlayerActivity.intentExo(
                                    context,
                                    urls = urls,
                                    names = names,
                                    startIndex = startIndex,
                                    title = title,
                                    useCollapsHeaders = useCollapsHeaders,
                                    kinopoiskId = null,
                                    episodeProgressCallback = episodeProgressCallback,
                                )
                            com.neo.neomovies.ui.settings.PlayerEngineMode.MPV ->
                                com.neo.player.PlayerActivity.intent(
                                    context,
                                    urls = urls,
                                    names = names,
                                    startIndex = startIndex,
                                    title = title,
                                    useCollapsHeaders = useCollapsHeaders,
                                    kinopoiskId = null,
                                    episodeProgressCallback = episodeProgressCallback,
                                )
                        }
                    context.startActivity(intent)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = NavRoute.Player.route,
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { entry ->
            val sourceId = entry.arguments?.getString("sourceId") ?: return@composable
            val context = LocalContext.current
            LaunchedEffect(sourceId) {
                val mode = com.neo.neomovies.ui.settings.PlayerEngineManager.getMode(context)
                val intent =
                    when (mode) {
                        com.neo.neomovies.ui.settings.PlayerEngineMode.EXO ->
                            PlayerActivity.intentExo(context, url = sourceId)
                        com.neo.neomovies.ui.settings.PlayerEngineMode.MPV ->
                            PlayerActivity.intent(context, url = sourceId)
                    }
                context.startActivity(intent)
                navController.popBackStack()
            }
        }
    }
}
