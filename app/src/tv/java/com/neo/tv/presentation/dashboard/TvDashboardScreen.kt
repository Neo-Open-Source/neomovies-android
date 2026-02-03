package com.neo.tv.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neo.neomovies.ui.navigation.CategoryType
import com.neo.tv.presentation.favorites.TvFavoritesScreen
import com.neo.tv.presentation.profile.TvProfileScreen
import com.neo.tv.presentation.search.TvSearchScreen

private val ParentPadding = PaddingValues(vertical = 16.dp, horizontal = 58.dp)

@Composable
fun rememberChildPadding(direction: LayoutDirection = LocalLayoutDirection.current): PaddingValues {
    return remember {
        PaddingValues(
            start = ParentPadding.calculateStartPadding(direction) + 8.dp,
            top = ParentPadding.calculateTopPadding(),
            end = ParentPadding.calculateEndPadding(direction) + 8.dp,
            bottom = ParentPadding.calculateBottomPadding(),
        )
    }
}

@Composable
fun TvDashboardScreen(
    openCategoryList: (CategoryType) -> Unit,
    openDetails: (String) -> Unit,
    openSearch: () -> Unit,
    openSettings: () -> Unit,
    openAbout: () -> Unit,
    onBackPressed: () -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val navController = rememberNavController()

    var isTopBarVisible by remember { mutableStateOf(true) }
    var isTopBarFocused by remember { mutableStateOf(false) }

    var currentDestination: String? by remember { mutableStateOf(null) }
    val currentTopBarSelectedTabIndex by remember(currentDestination) {
        derivedStateOf {
            TvDashboardTabs.tabs.indexOfFirst { it.route == currentDestination }.coerceAtLeast(0)
        }
    }

    DisposableEffect(Unit) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            currentDestination = destination.route
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    BackPressHandledArea(
        onBackPressed = {
            if (!isTopBarVisible) {
                isTopBarVisible = true
                TopBarFocusRequesters[currentTopBarSelectedTabIndex + 1].requestFocus()
            } else if (currentTopBarSelectedTabIndex == 0) onBackPressed()
            else if (!isTopBarFocused) {
                TopBarFocusRequesters[currentTopBarSelectedTabIndex + 1].requestFocus()
            } else TopBarFocusRequesters[1].requestFocus()
        }
    ) {
        var wasTopBarFocusRequestedBefore by rememberSaveable { mutableStateOf(false) }
        var topBarHeightPx: Int by rememberSaveable { mutableIntStateOf(0) }

        val topBarYOffsetPx = if (isTopBarVisible) 0 else -topBarHeightPx
        val navHostTopPaddingDp = if (isTopBarVisible) with(density) { topBarHeightPx.toDp() } else 0.dp

        LaunchedEffect(Unit) {
            if (!wasTopBarFocusRequestedBefore) {
                TopBarFocusRequesters[currentTopBarSelectedTabIndex + 1].requestFocus()
                wasTopBarFocusRequestedBefore = true
            }
        }

        TvTopBar(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = topBarYOffsetPx) }
                .onSizeChanged { topBarHeightPx = it.height }
                .onFocusChanged { isTopBarFocused = it.hasFocus }
                .padding(
                    horizontal = ParentPadding.calculateStartPadding(
                        LocalLayoutDirection.current,
                    ) + 8.dp,
                )
                .padding(
                    top = ParentPadding.calculateTopPadding(),
                    bottom = ParentPadding.calculateBottomPadding(),
                ),
            selectedTabIndex = currentTopBarSelectedTabIndex,
        ) { target ->
            if (currentDestination != target.route) {
                navController.navigate(target.route) {
                    if (target == TvDashboardTabs.tabs.first()) {
                        popUpTo(TvDashboardTabs.tabs.first().route)
                    }
                    launchSingleTop = true
                }
            }
        }

        TvDashboardBody(
            openCategoryList = openCategoryList,
            openDetails = openDetails,
            openSearch = openSearch,
            openSettings = openSettings,
            openAbout = openAbout,
            updateTopBarVisibility = { isTopBarVisible = it },
            navController = navController,
            modifier = Modifier.offset(y = navHostTopPaddingDp),
        )
    }
}

@Composable
private fun BackPressHandledArea(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = Modifier
        .onPreviewKeyEvent {
            if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                onBackPressed()
                true
            } else {
                false
            }
        }
        .then(modifier),
    content = content,
)

@Composable
private fun TvDashboardBody(
    openCategoryList: (CategoryType) -> Unit,
    openDetails: (String) -> Unit,
    openSearch: () -> Unit,
    openSettings: () -> Unit,
    openAbout: () -> Unit,
    updateTopBarVisibility: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = TvDashboardTabs.tabs.first().route,
    ) {
        composable(TvDashboardTabs.Home.route) {
            com.neo.tv.presentation.home.TvHomeScreen(
                onOpenCategory = openCategoryList,
                onOpenDetails = openDetails,
                onOpenSearch = openSearch,
                onScroll = updateTopBarVisibility,
            )
        }
        composable(TvDashboardTabs.Search.route) {
            TvSearchScreen(
                onBack = { navController.navigate(TvDashboardTabs.tabs.first().route) },
                onOpenDetails = openDetails,
            )
        }
        composable(TvDashboardTabs.Favorites.route) {
            TvFavoritesScreen(
                onBack = { navController.navigate(TvDashboardTabs.tabs.first().route) },
                onOpenProfile = { navController.navigate(TvDashboardTabs.Profile.route) },
                onOpenDetails = openDetails,
            )
        }
        composable(TvDashboardTabs.Profile.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val authManager = androidx.compose.runtime.remember { com.neo.neomovies.auth.NeoIdAuthManager(context) }
            TvProfileScreen(
                onLoginWithNeoId = { authManager.startLogin() },
                onLogout = { authManager.logout() },
                onOpenSettings = openSettings,
                onOpenAbout = openAbout,
            )
        }
    }
}
