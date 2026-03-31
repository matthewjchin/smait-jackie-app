package com.gow.smaitrobot.data.model

import com.google.gson.annotations.SerializedName

/**
 * Root theme configuration loaded from a JSON asset file (e.g. babmdc2026_theme.json).
 *
 * All fields have defaults so that a minimal JSON (or an empty JSON) will never
 * cause a NullPointerException. Gson populates fields present in the JSON and
 * leaves absent fields at their Kotlin default values.
 *
 * Swap the JSON file to change the event branding with zero code changes.
 */
data class ThemeConfig(
    @SerializedName("eventName")
    val eventName: String = "SMAIT Robot",

    @SerializedName("tagline")
    val tagline: String = "Your Intelligent Campus Guide",

    @SerializedName("logoAsset")
    val logoAsset: String = "smait_logo.png",

    @SerializedName("colors")
    val colors: ThemeColors = ThemeColors(),

    @SerializedName("fonts")
    val fonts: ThemeFonts = ThemeFonts(),

    @SerializedName("sponsors")
    val sponsors: List<SponsorConfig> = emptyList(),

    @SerializedName("cards")
    val cards: List<CardConfig> = emptyList(),

    @SerializedName("schedule")
    val schedule: List<ScheduleItem> = emptyList(),

    @SerializedName("speakers")
    val speakers: List<SpeakerInfo> = emptyList()
) {
    companion object {
        /**
         * Returns a fully-populated default configuration for use when no JSON is loaded
         * or when the JSON cannot be parsed. Colors match the neutral default theme.
         */
        fun default(): ThemeConfig = ThemeConfig(
            eventName = "SMAIT Robot",
            tagline = "Your Intelligent Campus Guide",
            logoAsset = "smait_logo.png",
            colors = ThemeColors(),
            fonts = ThemeFonts(),
            sponsors = emptyList(),
            cards = emptyList(),
            schedule = emptyList(),
            speakers = emptyList()
        )
    }
}

/**
 * Color palette for the theme.
 *
 * All hex values are in #RRGGBB format for compatibility with
 * [android.graphics.Color.parseColor] and Compose's [androidx.compose.ui.graphics.Color].
 */
data class ThemeColors(
    @SerializedName("primary")
    val primary: String = "#0956A4",

    @SerializedName("secondary")
    val secondary: String = "#E8A317",

    @SerializedName("tertiary")
    val tertiary: String = "#1A3D6D",

    @SerializedName("background")
    val background: String = "#F8F9FA",

    @SerializedName("onPrimary")
    val onPrimary: String = "#FFFFFF",

    @SerializedName("onBackground")
    val onBackground: String = "#1A1A2E",

    @SerializedName("surface")
    val surface: String = "#FFFFFF",

    @SerializedName("onSurface")
    val onSurface: String = "#1A1A2E"
)

/**
 * Font configuration (currently unused — Roboto default applies).
 * Kept for future customization.
 */
data class ThemeFonts(
    @SerializedName("bodyFont")
    val bodyFont: String = "Roboto",

    @SerializedName("headingFont")
    val headingFont: String = "Roboto"
)

/**
 * A home-screen card entry.
 *
 * [action] follows the pattern "navigate:destination" or "inline:content":
 * - "navigate:chat"        → open chat screen
 * - "navigate:map"         → open wayfinding/map screen
 * - "navigate:facilities"  → open facilities screen
 * - "navigate:eventinfo"   → open event info screen
 * - "inline:keynote"       → display keynote info inline
 * - "inline:sessions"      → display session tracks inline
 */
data class CardConfig(
    @SerializedName("label")
    val label: String = "",

    @SerializedName("action")
    val action: String = "",

    @SerializedName("icon")
    val icon: String = "",

    @SerializedName("description")
    val description: String = ""
)

/**
 * A sponsor entry shown on the home screen sponsor strip.
 */
data class SponsorConfig(
    @SerializedName("name")
    val name: String = "",

    @SerializedName("logoAsset")
    val logoAsset: String = ""
)

/**
 * A schedule entry for the event program.
 */
data class ScheduleItem(
    @SerializedName("time")
    val time: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("speaker")
    val speaker: String = "",

    @SerializedName("location")
    val location: String = "",

    @SerializedName("track")
    val track: String = ""
)

/**
 * A speaker profile shown in the speakers section.
 */
data class SpeakerInfo(
    @SerializedName("name")
    val name: String = "",

    @SerializedName("title")
    val title: String = "",

    @SerializedName("bio")
    val bio: String = "",

    @SerializedName("photoAsset")
    val photoAsset: String = ""
)
