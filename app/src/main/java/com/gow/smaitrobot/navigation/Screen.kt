package com.gow.smaitrobot.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the Jackie robot app.
 *
 * Each screen is a Kotlin object — sealed class subtypes work as navigation routes
 * with kotlinx-serialization 1.7+ and Navigation Compose 2.8+.
 *
 * The [label] and [iconName] properties drive the bottom navigation bar.
 * The actual [ImageVector] is resolved in the AppNavigation composable via
 * [screenIcon()] to keep this class free of Compose/Android dependencies
 * (enabling plain JVM unit tests).
 *
 * Example navigation:
 * ```kotlin
 * navController.navigate(Screen.Chat)
 * ```
 */
@Serializable
sealed class Screen {

    /** The label displayed in the bottom navigation bar. */
    abstract val label: String

    /**
     * Icon identifier for the bottom navigation bar item.
     * Maps to a Material Icons filled icon name.
     * Resolved to an [ImageVector] by [screenIcon] in the Compose layer.
     */
    abstract val iconName: String

    @Serializable
    object Home : Screen() {
        override val label: String = "Home"
        override val iconName: String = "Home"
    }

    @Serializable
    object Chat : Screen() {
        override val label: String = "Chat"
        override val iconName: String = "Chat"
    }

    @Serializable
    object Map : Screen() {
        override val label: String = "Map"
        override val iconName: String = "Map"
    }

    @Serializable
    object Facilities : Screen() {
        override val label: String = "Facilities"
        override val iconName: String = "LocationOn"
    }

    @Serializable
    object EventInfo : Screen() {
        override val label: String = "Events"
        override val iconName: String = "Info"
    }

    companion object {
        /** All screens in the bottom nav bar, in display order. */
        val navBarItems: List<Screen> = listOf(Home, Chat, Map, Facilities, EventInfo)
    }
}
