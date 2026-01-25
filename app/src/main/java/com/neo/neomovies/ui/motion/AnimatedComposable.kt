package com.neo.neomovies.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val initialOffset = 0.10f

fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) =
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = {
            materialSharedAxisXIn(initialOffsetX = { (it * initialOffset).toInt() })
        },
        exitTransition = {
            materialSharedAxisXOut(targetOffsetX = { -(it * initialOffset).toInt() })
        },
        popEnterTransition = {
            materialSharedAxisXIn(initialOffsetX = { -(it * initialOffset).toInt() })
        },
        popExitTransition = {
            materialSharedAxisXOut(targetOffsetX = { (it * initialOffset).toInt() })
        },
        content = content,
    )
