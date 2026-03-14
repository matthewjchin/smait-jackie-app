package com.gow.smaitrobot.data.model

import com.gow.smaitrobot.navigation.Screen

/**
 * One-shot UI events emitted from ViewModels to their composable screens.
 *
 * These events represent actions that should happen exactly once — navigation
 * commands, snackbar messages, etc. They are sent via a [kotlinx.coroutines.channels.Channel]
 * to guarantee delivery without replay.
 *
 * Usage in a Composable:
 * ```kotlin
 * LaunchedEffect(Unit) {
 *     viewModel.uiEvents.collect { event ->
 *         when (event) {
 *             is UiEvent.NavigateTo -> navController.navigate(event.screen)
 *         }
 *     }
 * }
 * ```
 */
sealed class UiEvent {

    /**
     * Navigate to the given [screen], replacing the current back-stack entry.
     * Typically used for auto-return-to-Home after session end or silence timeout.
     */
    data class NavigateTo(val screen: Screen) : UiEvent()
}
