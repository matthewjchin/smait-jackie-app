package com.gow.smaitrobot.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.gow.smaitrobot.data.model.ThemeConfig

/**
 * Root Composable that provides the 5-tab bottom navigation scaffold and NavHost.
 *
 * Renders a [Scaffold] with a persistent [NavigationBar] at the bottom and a
 * [NavHost] that hosts 5 placeholder screens. Later plans (03–05) replace the
 * placeholder composables with fully-implemented screens.
 *
 * Navigation behavior:
 * - Each tab uses `popUpTo(Home) { saveState = true }` + `launchSingleTop = true`
 *   + `restoreState = true` so back-stack is kept lean and screen state is preserved.
 * - The bottom nav bar is always visible (never hidden).
 * - 60dp minimum touch target height on nav bar items for kiosk UX accessibility.
 *
 * @param navController  NavHostController created by the caller via [rememberNavController].
 * @param themeConfig    Active theme config from [ThemeRepository]; passed to screens.
 */
@Composable
fun AppScaffold(
    navController: NavHostController,
    themeConfig: ThemeConfig
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.navBarItems.forEach { screen ->
                    val routeName = screen::class.qualifiedName ?: screen::class.simpleName
                    val isSelected = currentRoute?.contains(screen::class.simpleName ?: "") == true

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen) {
                                popUpTo(Screen.Home) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screenIcon(screen),
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        modifier = Modifier.padding(vertical = 8.dp) // contributes to 60dp+ touch target
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable<Screen.Home> {
                PlaceholderScreen("Home")
            }
            composable<Screen.Chat> {
                PlaceholderScreen("Chat")
            }
            composable<Screen.Map> {
                PlaceholderScreen("Map")
            }
            composable<Screen.Facilities> {
                PlaceholderScreen("Facilities")
            }
            composable<Screen.EventInfo> {
                PlaceholderScreen("Event Info")
            }
        }
    }
}

/**
 * Placeholder screen composable used until the real screen is implemented in plans 03–05.
 * Each screen name is displayed centered in the available space.
 */
@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name)
    }
}

/**
 * Maps a [Screen] to its Material Icons [ImageVector].
 *
 * Kept in the Compose layer (not in [Screen]) so [Screen] remains free of
 * Compose dependencies and can be used in plain JVM unit tests.
 */
fun screenIcon(screen: Screen): ImageVector = when (screen) {
    is Screen.Home -> Icons.Filled.Home
    is Screen.Chat -> Icons.Filled.Chat
    is Screen.Map -> Icons.Filled.Map
    is Screen.Facilities -> Icons.Filled.LocationOn
    is Screen.EventInfo -> Icons.Filled.Info
}
