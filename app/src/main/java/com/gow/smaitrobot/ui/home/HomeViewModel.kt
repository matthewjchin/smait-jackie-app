package com.gow.smaitrobot.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gow.smaitrobot.data.model.CardConfig
import com.gow.smaitrobot.data.model.SponsorConfig
import com.gow.smaitrobot.data.theme.ThemeRepository
import com.gow.smaitrobot.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Sealed class representing the action to execute when a home screen card is tapped.
 *
 * Parsed from [CardConfig.action] strings by [HomeViewModel.parseCardAction].
 */
sealed class CardAction {
    /**
     * Navigate to a bottom-nav tab destination.
     *
     * @param screen  The [Screen] to navigate to.
     */
    data class NavigateToTab(val screen: Screen) : CardAction()

    /**
     * Display an inline content sheet/dialog.
     *
     * @param contentKey  The content identifier (e.g. "keynote", "sessions").
     */
    data class ShowInlineContent(val contentKey: String) : CardAction()
}

/**
 * ViewModel for the Home screen.
 *
 * Exposes [StateFlow]s derived from [ThemeRepository.config] so the screen composable
 * can react to theme changes without directly depending on [ThemeRepository].
 *
 * @param themeRepository  Repository holding the active [com.gow.smaitrobot.data.model.ThemeConfig].
 * @param scope            CoroutineScope for StateFlow sharing. Defaults to [viewModelScope].
 *                         Inject a [kotlinx.coroutines.test.TestScope] in unit tests.
 */
class HomeViewModel(
    private val themeRepository: ThemeRepository,
    scope: CoroutineScope? = null
) : ViewModel() {

    private val coroutineScope: CoroutineScope by lazy { scope ?: viewModelScope }

    /** List of home screen cards from the active theme configuration. */
    val cards: StateFlow<List<CardConfig>> by lazy {
        themeRepository.config
            .map { it.cards }
            .stateIn(coroutineScope, SharingStarted.Eagerly, themeRepository.config.value.cards)
    }

    /** Event name from the active theme configuration. */
    val eventName: StateFlow<String> by lazy {
        themeRepository.config
            .map { it.eventName }
            .stateIn(coroutineScope, SharingStarted.Eagerly, themeRepository.config.value.eventName)
    }

    /** Tagline from the active theme configuration. */
    val tagline: StateFlow<String> by lazy {
        themeRepository.config
            .map { it.tagline }
            .stateIn(coroutineScope, SharingStarted.Eagerly, themeRepository.config.value.tagline)
    }

    /** Sponsors list from the active theme configuration. */
    val sponsors: StateFlow<List<SponsorConfig>> by lazy {
        themeRepository.config
            .map { it.sponsors }
            .stateIn(coroutineScope, SharingStarted.Eagerly, themeRepository.config.value.sponsors)
    }

    /**
     * Parses a [CardConfig.action] string into a [CardAction].
     *
     * Supported formats:
     * - `"navigate:chat"` → [CardAction.NavigateToTab] with [Screen.Chat]
     * - `"navigate:map"` → [CardAction.NavigateToTab] with [Screen.Map]
     * - `"navigate:facilities"` → [CardAction.NavigateToTab] with [Screen.Facilities]
     * - `"navigate:eventinfo"` → [CardAction.NavigateToTab] with [Screen.EventInfo]
     * - `"inline:<key>"` → [CardAction.ShowInlineContent] with the content key
     *
     * Unknown actions default to [CardAction.ShowInlineContent] with the raw action string.
     *
     * @param action  Raw action string from [CardConfig.action].
     * @return        The resolved [CardAction].
     */
    fun parseCardAction(action: String): CardAction {
        val parts = action.split(":", limit = 2)
        if (parts.size < 2) return CardAction.ShowInlineContent(action)

        val type = parts[0]
        val target = parts[1]

        return when (type) {
            "navigate" -> {
                val screen: Screen = when (target) {
                    "chat" -> Screen.Chat
                    "map" -> Screen.Map
                    "facilities" -> Screen.Facilities
                    "eventinfo" -> Screen.EventInfo
                    "home" -> Screen.Home
                    "settings" -> Screen.Settings
                    else -> return CardAction.ShowInlineContent(action)
                }
                CardAction.NavigateToTab(screen)
            }
            "inline" -> CardAction.ShowInlineContent(target)
            else -> CardAction.ShowInlineContent(action)
        }
    }
}
