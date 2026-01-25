package com.neo.neomovies.ui.navigation

import androidx.compose.runtime.Immutable

@Immutable
sealed class NavRoute(val route: String) {
    data object Home : NavRoute("home")
    data object Favorites : NavRoute("favorites")
    data object Profile : NavRoute("profile")
    data object Search : NavRoute("search")
    data object Details : NavRoute("details/{sourceId}") {
        fun create(sourceId: String) = "details/$sourceId"
    }
    data object CategoryList : NavRoute("category/{type}") {
        fun create(type: CategoryType) = "category/${type.value}"
    }
}

enum class CategoryType(val value: String, val title: String) {
    POPULAR("popular", "Популярное"),
    TOP_MOVIES("top_movies", "Топ фильмов"),
    TOP_TV("top_tv", "Топ сериалов"),

    ;

    companion object {
        fun from(value: String?): CategoryType {
            return entries.firstOrNull { it.value == value } ?: POPULAR
        }
    }
}
