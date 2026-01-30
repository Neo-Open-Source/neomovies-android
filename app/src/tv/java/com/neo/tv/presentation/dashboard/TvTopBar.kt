package com.neo.tv.presentation.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.neo.neomovies.R

sealed class TvDashboardTabs(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Home : TvDashboardTabs("home", R.string.tab_home, Icons.Default.Home)
    data object Search : TvDashboardTabs("search", R.string.action_search, Icons.Default.Search)
    data object Favorites : TvDashboardTabs("favorites", R.string.tab_favorites, Icons.Default.FavoriteBorder)
    data object Profile : TvDashboardTabs("profile", R.string.profile_title, Icons.Default.Person)

    companion object {
        val tabs = listOf(Home, Search, Favorites, Profile)
    }
}

val TopBarFocusRequesters = List(size = TvDashboardTabs.tabs.size + 1) { FocusRequester() }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvTopBar(
    modifier: Modifier = Modifier,
    selectedTabIndex: Int,
    focusRequesters: List<FocusRequester> = remember { TopBarFocusRequesters },
    onTabSelected: (TvDashboardTabs) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .focusRestorer(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var isTabRowFocused by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.width(20.dp))
                TabRow(
                    modifier = Modifier.onFocusChanged {
                        isTabRowFocused = it.isFocused || it.hasFocus
                    },
                    selectedTabIndex = selectedTabIndex,
                    indicator = { _, _ ->
                        if (selectedTabIndex >= 0) {
                            TvTopBarIndicator(
                                anyTabFocused = isTabRowFocused,
                            )
                        }
                    },
                    separator = { Spacer(modifier = Modifier) },
                ) {
                    TvDashboardTabs.tabs.forEachIndexed { index, tab ->
                        key(index) {
                            Tab(
                                modifier = Modifier
                                    .height(40.dp)
                                    .focusRequester(focusRequesters[index + 1]),
                                selected = index == selectedTabIndex,
                                onFocus = { onTabSelected(tab) },
                                onClick = { focusManager.moveFocus(FocusDirection.Down) },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        tab.icon,
                                        contentDescription = null,
                                        tint = LocalContentColor.current,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = LocalContentColor.current,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}
