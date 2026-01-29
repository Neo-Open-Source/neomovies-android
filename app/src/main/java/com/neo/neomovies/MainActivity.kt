package com.neo.neomovies

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neo.neomovies.auth.NeoIdAuthManager
import com.neo.neomovies.auth.NeoIdAuthResult
import com.neo.neomovies.ui.favorites.FavoritesScreen
import com.neo.neomovies.ui.details.DetailsScreen
import com.neo.neomovies.ui.home.HomeScreen
import com.neo.neomovies.ui.list.CategoryListScreen
import com.neo.neomovies.ui.navigation.CategoryType
import com.neo.neomovies.ui.navigation.NavRoute
import com.neo.neomovies.ui.about.AboutScreen
import com.neo.neomovies.ui.about.CreditsScreen
import com.neo.neomovies.ui.profile.ProfileScreen
import com.neo.neomovies.ui.search.SearchScreen
import com.neo.neomovies.ui.settings.SettingsScreen
import com.neo.neomovies.ui.settings.TorrServerSettingsScreen
import com.neo.neomovies.ui.settings.LanguageScreen
import com.neo.neomovies.ui.settings.LanguageManager
import com.neo.neomovies.ui.settings.LanguageMode
import com.neo.neomovies.ui.theme.NeoMoviesTheme
import com.neo.neomovies.ui.motion.animatedComposable
import com.neo.neomovies.ui.watch.WatchSelectorScreen
import com.neo.player.PlayerActivity

class MainActivity : AppCompatActivity() {
    private lateinit var neoIdAuthManager: NeoIdAuthManager

    private val notificationsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        neoIdAuthManager = NeoIdAuthManager(this)
        handleAuthCallbackIfNeeded(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                PermissionChecker.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PermissionChecker.PERMISSION_GRANTED
            if (!granted) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            NeoMoviesTheme {
                NeoMoviesApp(
                    onLoginWithNeoId = { neoIdAuthManager.startLogin() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (LanguageManager.getMode(this) == LanguageMode.SYSTEM) {
            LanguageManager.apply(this)
        }

        val authPrefs = getSharedPreferences("auth", MODE_PRIVATE)
        val token = authPrefs.getString("token", null)
        if (!token.isNullOrBlank()) {
            Thread {
                neoIdAuthManager.fetchAndPersistProfile(token)
            }.start()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleAuthCallbackIfNeeded(intent)
        }
    }

    private fun handleAuthCallbackIfNeeded(intent: Intent) {
        val data: Uri = intent.data ?: return
        if (data.scheme == "neomovies" && data.host == "auth" && data.path == "/callback") {
            val result = neoIdAuthManager.handleCallback(data)
            when (result) {
                is NeoIdAuthResult.Success -> {
                    Thread {
                        neoIdAuthManager.fetchAndPersistProfile(result.token)
                        neoIdAuthManager.verifyAndPersistUser(result.token)
                    }.start()
                }
                is NeoIdAuthResult.Error -> Unit
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun NeoMoviesApp(
    onLoginWithNeoId: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authManager = androidx.compose.runtime.remember { NeoIdAuthManager(context) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isTopLevelRoute = when (currentDestination?.route) {
        NavRoute.Home.route,
        NavRoute.Favorites.route,
        NavRoute.Profile.route,
        -> true
        else -> false
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Home.route,
            modifier = modifier,
        ) {
                animatedComposable(NavRoute.Home.route) {
                    HomeScreen(
                        onOpenCategory = { type ->
                            navController.navigate(NavRoute.CategoryList.create(type))
                        },
                        onOpenDetails = { sourceId ->
                            navController.navigate(NavRoute.Details.create(sourceId))
                        },
                        onOpenSearch = {
                            navController.navigate(NavRoute.Search.route)
                        },
                    )
                }

                animatedComposable(NavRoute.Search.route) {
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDetails = { sourceId ->
                            navController.navigate(NavRoute.Details.create(sourceId))
                        },
                    )
                }

                animatedComposable(NavRoute.Favorites.route) {
                    FavoritesScreen(
                        onOpenProfile = {
                            navController.navigate(NavRoute.Profile.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
                        },
                        onOpenDetails = { sourceId ->
                            navController.navigate(NavRoute.Details.create(sourceId))
                        },
                    )
                }

                animatedComposable(NavRoute.Profile.route) {
                    ProfileScreen(
                        onLoginWithNeoId = onLoginWithNeoId,
                        onLogout = { authManager.logout() },
                        onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
                        onOpenAbout = { navController.navigate(NavRoute.About.route) },
                    )
                }

                animatedComposable(NavRoute.Settings.route) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLanguage = { navController.navigate(NavRoute.Language.route) },
                        onOpenTorrServer = { navController.navigate(NavRoute.TorrServer.route) },
                        onOpenSource = { navController.navigate(NavRoute.SourceSettings.route) },
                        onOpenPlayer = { navController.navigate(NavRoute.PlayerSettings.route) },
                    )
                }

                animatedComposable(NavRoute.TorrServer.route) {
                    TorrServerSettingsScreen(onBack = { navController.popBackStack() })
                }

                animatedComposable(NavRoute.Language.route) {
                    LanguageScreen(onBack = { navController.popBackStack() })
                }

                animatedComposable(NavRoute.SourceSettings.route) {
                    com.neo.neomovies.ui.settings.SourceSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                animatedComposable(NavRoute.PlayerSettings.route) {
                    com.neo.neomovies.ui.settings.PlayerSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                animatedComposable(NavRoute.About.route) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCredits = { navController.navigate(NavRoute.Credits.route) },
                        onOpenChanges = { navController.navigate(NavRoute.Changes.route) },
                        onOpenSettings = { navController.navigate(NavRoute.Settings.route) },
                    )
                }

                animatedComposable(NavRoute.Changes.route) {
                    com.neo.neomovies.ui.about.ChangesScreen(onBack = { navController.popBackStack() })
                }

                animatedComposable(NavRoute.Credits.route) {
                    CreditsScreen(onBack = { navController.popBackStack() })
                }

                animatedComposable(NavRoute.CategoryList.route) { entry ->
                    val type = CategoryType.from(entry.arguments?.getString("type"))
                    CategoryListScreen(
                        categoryType = type,
                        onBack = { navController.popBackStack() },
                        onOpenDetails = { sourceId ->
                            navController.navigate(NavRoute.Details.create(sourceId))
                        },
                    )
                }

                animatedComposable(NavRoute.Details.route) { entry ->
                    val sourceId = entry.arguments?.getString("sourceId") ?: return@animatedComposable
                    DetailsScreen(
                        sourceId = sourceId,
                        onBack = { navController.popBackStack() },
                        onWatch = { navController.navigate(NavRoute.WatchSelector.create(sourceId)) },
                    )
                }

                animatedComposable(NavRoute.WatchSelector.route) { entry ->
                    val sourceId = entry.arguments?.getString("sourceId") ?: return@animatedComposable
                    val context = LocalContext.current
                    WatchSelectorScreen(
                        sourceId = sourceId,
                        onBack = { navController.popBackStack() },
                        onWatch = { urls, names, startIndex, title ->
                            val mode = com.neo.neomovies.ui.settings.PlayerEngineManager.getMode(context)
                            val useCollapsHeaders =
                                com.neo.neomovies.ui.settings.SourceManager.getMode(context) ==
                                    com.neo.neomovies.ui.settings.SourceMode.COLLAPS
                            context.startActivity(
                                when (mode) {
                                    com.neo.neomovies.ui.settings.PlayerEngineMode.EXO ->
                                        com.neo.player.PlayerActivity.intentExo(
                                            context,
                                            urls = urls,
                                            names = names,
                                            startIndex = startIndex,
                                            title = title,
                                            useCollapsHeaders = useCollapsHeaders,
                                        )
                                    com.neo.neomovies.ui.settings.PlayerEngineMode.MPV ->
                                        com.neo.player.PlayerActivity.intent(
                                            context,
                                            urls = urls,
                                            names = names,
                                            startIndex = startIndex,
                                            title = title,
                                            useCollapsHeaders = useCollapsHeaders,
                                        )
                                },
                            )
                            navController.popBackStack()
                        },
                    )
                }

                animatedComposable(NavRoute.Player.route) { entry ->
                    val sourceId = entry.arguments?.getString("sourceId") ?: return@animatedComposable
                    val context = LocalContext.current
                    LaunchedEffect(sourceId) {
                        context.startActivity(
                            when (com.neo.neomovies.ui.settings.PlayerEngineManager.getMode(context)) {
                                com.neo.neomovies.ui.settings.PlayerEngineMode.EXO ->
                                    PlayerActivity.intentExo(context, url = sourceId)
                                com.neo.neomovies.ui.settings.PlayerEngineMode.MPV ->
                                    PlayerActivity.intent(context, url = sourceId)
                            },
                        )
                        navController.popBackStack()
                    }
                }
            }
    }

    if (isTopLevelRoute) {
        val currentTopLevel = when (currentDestination?.route) {
            NavRoute.Favorites.route -> AppDestinations.FAVORITES
            NavRoute.Profile.route -> AppDestinations.PROFILE
            else -> AppDestinations.HOME
        }

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach {
                    item(
                        icon = {
                            Icon(
                                it.icon,
                                contentDescription = stringResource(it.labelRes),
                            )
                        },
                        label = { Text(stringResource(it.labelRes)) },
                        selected = it == currentTopLevel,
                        onClick = {
                            navController.navigate(it.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                content(Modifier.padding(innerPadding))
            }
        }
    } else {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            content(Modifier.padding(innerPadding))
        }
    }
}

enum class AppDestinations(
    val labelRes: Int,
    val icon: ImageVector,
    val route: String,
) {
    HOME(R.string.tab_home, Icons.Filled.Home, NavRoute.Home.route),
    FAVORITES(R.string.tab_favorites, Icons.Filled.Favorite, NavRoute.Favorites.route),
    PROFILE(R.string.profile_title, Icons.Filled.AccountBox, NavRoute.Profile.route),
}
