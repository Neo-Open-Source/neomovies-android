package com.neo.neomovies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neo.neomovies.ui.favorites.FavoritesScreen
import com.neo.neomovies.ui.home.HomeScreen
import com.neo.neomovies.ui.list.CategoryListScreen
import com.neo.neomovies.ui.navigation.CategoryType
import com.neo.neomovies.ui.navigation.NavRoute
import com.neo.neomovies.ui.profile.ProfileScreen
import com.neo.neomovies.ui.theme.NeoMoviesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeoMoviesTheme {
                NeoMoviesApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun NeoMoviesApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val currentTopLevel = when {
        currentDestination?.route == NavRoute.Favorites.route -> AppDestinations.FAVORITES
        currentDestination?.route == NavRoute.Profile.route -> AppDestinations.PROFILE
        else -> AppDestinations.HOME
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentTopLevel,
                    onClick = {
                        navController.navigate(it.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = NavRoute.Home.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(NavRoute.Home.route) {
                    HomeScreen(
                        onOpenCategory = { type ->
                            navController.navigate(NavRoute.CategoryList.create(type))
                        },
                    )
                }

                composable(NavRoute.Favorites.route) {
                    FavoritesScreen()
                }

                composable(NavRoute.Profile.route) {
                    ProfileScreen()
                }

                composable(NavRoute.CategoryList.route) { entry ->
                    val type = CategoryType.from(entry.arguments?.getString("type"))
                    CategoryListScreen(
                        categoryType = type,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val route: String,
) {
    HOME("Home", Icons.Filled.Home, NavRoute.Home.route),
    FAVORITES("Favorites", Icons.Filled.Favorite, NavRoute.Favorites.route),
    PROFILE("Profile", Icons.Filled.AccountBox, NavRoute.Profile.route),
}