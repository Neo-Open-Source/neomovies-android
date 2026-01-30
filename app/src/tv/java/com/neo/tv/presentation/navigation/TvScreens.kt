package com.neo.tv.presentation.navigation

import androidx.compose.runtime.Immutable

@Immutable
sealed class TvScreens(val route: String) {
    data object Dashboard : TvScreens("dashboard")
    data object Search : TvScreens("search")
    data object Favorites : TvScreens("favorites")
    data object Profile : TvScreens("profile")
    data object CategoryList : TvScreens("category/{type}") {
        fun create(type: String) = "category/$type"
    }
    data object Details : TvScreens("details/{sourceId}") {
        fun create(sourceId: String) = "details/$sourceId"
    }
    data object WatchSelector : TvScreens("watch/{sourceId}") {
        fun create(sourceId: String) = "watch/$sourceId"
    }
    data object Player : TvScreens("player")
    data object Settings : TvScreens("settings")
    data object Language : TvScreens("settings/language")
    data object TorrServer : TvScreens("settings/torrserver")
    data object SourceSettings : TvScreens("settings/source")
    data object PlayerSettings : TvScreens("settings/player")
    data object About : TvScreens("about")
    data object Credits : TvScreens("credits")
    data object Changes : TvScreens("changes")
}
