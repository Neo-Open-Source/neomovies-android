package com.neo.neomovies.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neo.neomovies.ui.details.DetailsScreen
import com.neo.neomovies.ui.home.HomeScreen
import com.neo.neomovies.ui.list.CategoryListScreen

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
            )
        }
    }
}
