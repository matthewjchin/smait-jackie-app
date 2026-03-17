package com.gow.smaitrobot.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the Jackie robot app.
 *
 * Each screen is a Kotlin object — sealed class subtypes work as navigation routes
 * with kotlinx-serialization 1.7+ and Navigation Compose 2.8+.
 *
 * Navigation is driven by the Home screen card grid (no bottom nav bar).
 *
 * Example navigation:
 * ```kotlin
 * navController.navigate(Screen.Chat)
 * ```
 */
@Serializable
sealed class Screen {

    /** The label displayed on the screen's top bar. */
    abstract val label: String

    /** Icon identifier for home screen cards. */
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

    @Serializable
    object Settings : Screen() {
        override val label: String = "Settings"
        override val iconName: String = "Settings"
    }
}
