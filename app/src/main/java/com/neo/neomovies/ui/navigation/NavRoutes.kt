package com.neo.neomovies.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.annotation.StringRes
import android.net.Uri
import com.neo.neomovies.R

@Immutable
sealed class NavRoute(val route: String) {
    data object Home : NavRoute("home")
    data object Favorites : NavRoute("favorites")
    data object Profile : NavRoute("profile")
    data object Search : NavRoute("search")
    data object WatchSelector : NavRoute("watch/{sourceId}") {
        fun create(sourceId: String) = "watch/$sourceId"
    }
    data object Player : NavRoute("player/{sourceId}") {
        fun create(sourceId: String) = "player/${Uri.encode(sourceId)}"
    }
    data object Settings : NavRoute("settings")
    data object Language : NavRoute("settings/language")
    data object TorrServer : NavRoute("settings/torrserver")
    data object SourceSettings : NavRoute("settings/source")
    data object PlayerSettings : NavRoute("settings/player")
    data object About : NavRoute("about")
    data object Credits : NavRoute("credits")
    data object Changes : NavRoute("changes")
    data object Details : NavRoute("details/{sourceId}") {
        fun create(sourceId: String) = "details/$sourceId"
    }
    data object CategoryList : NavRoute("category/{type}") {
        fun create(type: CategoryType) = "category/${type.value}"
    }
}

enum class CategoryType(val value: String, @StringRes val titleRes: Int) {
    POPULAR("popular", R.string.category_popular),
    TOP_MOVIES("top_movies", R.string.category_top_movies),
    TOP_TV("top_tv", R.string.category_top_tv),

    ;

    companion object {
        fun from(value: String?): CategoryType {
            return entries.firstOrNull { it.value == value } ?: POPULAR
        }
    }
}
