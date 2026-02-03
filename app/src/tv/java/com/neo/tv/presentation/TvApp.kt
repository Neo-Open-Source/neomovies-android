package com.neo.tv.presentation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neo.neomovies.ui.about.CreditsScreen
import com.neo.neomovies.ui.navigation.CategoryType
import com.neo.tv.presentation.about.TvAboutScreen
import com.neo.tv.presentation.dashboard.TvDashboardScreen
import com.neo.tv.presentation.details.TvDetailsScreen
import com.neo.tv.presentation.favorites.TvFavoritesScreen
import com.neo.tv.presentation.list.TvCategoryListScreen
import com.neo.tv.presentation.navigation.TvScreens
import com.neo.tv.presentation.player.TvPlayerArgs
import com.neo.tv.presentation.player.TvVideoPlayerScreen
import com.neo.tv.presentation.profile.TvProfileScreen
import com.neo.tv.presentation.search.TvSearchScreen
import com.neo.tv.presentation.settings.TvLanguageScreen
import com.neo.tv.presentation.settings.TvPlayerSettingsScreen
import com.neo.tv.presentation.settings.TvSettingsScreen
import com.neo.tv.presentation.settings.TvSourceSettingsScreen
import com.neo.tv.presentation.settings.TvTorrServerSettingsScreen
import com.neo.tv.presentation.watch.TvWatchSelectorScreen
import com.neo.neomovies.ui.settings.PlayerEngineManager
import com.neo.neomovies.ui.settings.PlayerEngineMode
import com.neo.neomovies.ui.settings.SourceManager
import com.neo.neomovies.ui.settings.SourceMode

@Composable
fun TvApp(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = TvScreens.Dashboard.route,
        modifier = modifier,
    ) {
        composable(TvScreens.Dashboard.route) {
            TvDashboardScreen(
                openCategoryList = { category ->
                    navController.navigate(TvScreens.CategoryList.create(category.value))
                },
                openDetails = { sourceId ->
                    navController.navigate(TvScreens.Details.create(sourceId))
                },
                openSearch = { navController.navigate(TvScreens.Search.route) },
                openSettings = { navController.navigate(TvScreens.Settings.route) },
                openAbout = { navController.navigate(TvScreens.About.route) },
                onBackPressed = onBackPressed,
            )
        }

        composable(
            route = TvScreens.CategoryList.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            val type = CategoryType.from(entry.arguments?.getString("type"))
            TvCategoryListScreen(
                categoryType = type,
                onBack = { navController.popBackStack() },
                onOpenDetails = { sourceId ->
                    navController.navigate(TvScreens.Details.create(sourceId))
                },
            )
        }

        composable(
            route = TvScreens.Details.route,
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { entry ->
            val sourceId = entry.arguments?.getString("sourceId") ?: return@composable
            TvDetailsScreen(
                sourceId = sourceId,
                onBack = { navController.popBackStack() },
                onWatch = { navController.navigate(TvScreens.WatchSelector.create(sourceId)) },
            )
        }

        composable(
            route = TvScreens.WatchSelector.route,
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
        ) { entry ->
            val sourceId = entry.arguments?.getString("sourceId") ?: return@composable
            TvWatchSelectorScreen(
                sourceId = sourceId,
                onBack = { navController.popBackStack() },
                onWatch = { urls, names, startIndex, title, kinopoiskId, episodeProgressCallback ->
                    val mode = PlayerEngineManager.getMode(context)
                    val useCollapsHeaders = SourceManager.getMode(context) == SourceMode.COLLAPS
                    TvPlayerArgs.set(
                        urls = urls,
                        names = names,
                        startIndex = startIndex,
                        title = title,
                        useExo = mode == PlayerEngineMode.EXO,
                        useCollapsHeaders = useCollapsHeaders,
                        sourceId = sourceId,
                        kinopoiskId = kinopoiskId,
                        episodeProgressCallback = episodeProgressCallback,
                    )

                    navController.navigate(TvScreens.Player.route) {
                        popUpTo(TvScreens.WatchSelector.route) { inclusive = true }
                    }
                },
            )
        }

        composable(TvScreens.Player.route) {
            TvVideoPlayerScreen(
                onBack = {
                    val backSourceId = TvPlayerArgs.sourceId
                    if (backSourceId != null) {
                        navController.popBackStack(TvScreens.Details.create(backSourceId), false)
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(TvScreens.Settings.route) {
            TvSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLanguage = { navController.navigate(TvScreens.Language.route) },
                onOpenTorrServer = { navController.navigate(TvScreens.TorrServer.route) },
                onOpenSource = { navController.navigate(TvScreens.SourceSettings.route) },
                onOpenPlayer = { navController.navigate(TvScreens.PlayerSettings.route) },
            )
        }

        composable(TvScreens.TorrServer.route) {
            TvTorrServerSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(TvScreens.Language.route) {
            TvLanguageScreen(onBack = { navController.popBackStack() })
        }

        composable(TvScreens.SourceSettings.route) {
            TvSourceSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(TvScreens.PlayerSettings.route) {
            TvPlayerSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(TvScreens.About.route) {
            TvAboutScreen(
                onBack = { navController.popBackStack() },
                onOpenCredits = { navController.navigate(TvScreens.Credits.route) },
                onOpenChanges = { navController.navigate(TvScreens.Changes.route) },
                onOpenSettings = { navController.navigate(TvScreens.Settings.route) },
            )
        }

        composable(TvScreens.Credits.route) {
            CreditsScreen(onBack = { navController.popBackStack() })
        }

        composable(TvScreens.Changes.route) {
            com.neo.neomovies.ui.about.ChangesScreen(onBack = { navController.popBackStack() })
        }

        composable(TvScreens.Search.route) {
            TvSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenDetails = { sourceId ->
                    navController.navigate(TvScreens.Details.create(sourceId))
                },
            )
        }

        composable(TvScreens.Favorites.route) {
            TvFavoritesScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(TvScreens.Profile.route) },
                onOpenDetails = { sourceId ->
                    navController.navigate(TvScreens.Details.create(sourceId))
                },
            )
        }

        composable(TvScreens.Profile.route) {
            val authManager = androidx.compose.runtime.remember { com.neo.neomovies.auth.NeoIdAuthManager(context) }
            TvProfileScreen(
                onLoginWithNeoId = { authManager.startLogin() },
                onLogout = { authManager.logout() },
                onOpenSettings = { navController.navigate(TvScreens.Settings.route) },
                onOpenAbout = { navController.navigate(TvScreens.About.route) },
            )
        }
    }
}