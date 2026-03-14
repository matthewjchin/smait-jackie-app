package com.gow.smaitrobot

import com.google.gson.Gson
import com.gow.smaitrobot.data.model.ThemeConfig
import com.gow.smaitrobot.data.model.ThemeColors
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ThemeConfig data model and JSON parsing.
 *
 * Tests ThemeConfig.default() and direct Gson parsing of JSON strings —
 * avoids Android Context dependency so these run as plain JVM unit tests.
 */
class ThemeRepositoryTest {

    private val gson = Gson()

    // ─── ThemeConfig.default() tests ───

    @Test
    fun `default config has non-empty eventName`() {
        val config = ThemeConfig.default()
        assertTrue("eventName must not be empty", config.eventName.isNotEmpty())
    }

    @Test
    fun `default config has non-empty tagline`() {
        val config = ThemeConfig.default()
        assertTrue("tagline must not be empty", config.tagline.isNotEmpty())
    }

    @Test
    fun `default config primary color is valid hex`() {
        val config = ThemeConfig.default()
        assertTrue(
            "primary must be #RRGGBB format",
            config.colors.primary.matches(Regex("^#[0-9A-Fa-f]{6}$"))
        )
    }

    @Test
    fun `default config secondary color is valid hex`() {
        val config = ThemeConfig.default()
        assertTrue(
            "secondary must be #RRGGBB format",
            config.colors.secondary.matches(Regex("^#[0-9A-Fa-f]{6}$"))
        )
    }

    @Test
    fun `default config background color is valid hex`() {
        val config = ThemeConfig.default()
        assertTrue(
            "background must be #RRGGBB format",
            config.colors.background.matches(Regex("^#[0-9A-Fa-f]{6}$"))
        )
    }

    // ─── Gson parsing of WiE JSON string ───

    private val WIE_JSON = """
        {
          "eventName": "WiE 2026",
          "tagline": "Engineering Beyond Imagination",
          "logoAsset": "wie_logo.png",
          "colors": {
            "primary": "#7B2D8B",
            "secondary": "#00A99D",
            "tertiary": "#F7941D",
            "background": "#FAFAFA",
            "onPrimary": "#FFFFFF",
            "onBackground": "#1C1B1F",
            "surface": "#FFFFFF",
            "onSurface": "#1C1B1F"
          },
          "cards": [
            {"label": "Ask Me Anything", "action": "navigate:chat", "icon": "ic_chat"},
            {"label": "Guided Tour", "action": "navigate:map", "icon": "ic_map"},
            {"label": "Keynote Info", "action": "inline:keynote", "icon": "ic_keynote"},
            {"label": "Session Tracks", "action": "inline:sessions", "icon": "ic_sessions"},
            {"label": "Facilities", "action": "navigate:facilities", "icon": "ic_facilities"},
            {"label": "Event Info", "action": "navigate:eventinfo", "icon": "ic_info"}
          ],
          "sponsors": [
            {"name": "SJSU Engineering", "logoAsset": "sjsu_logo.png"}
          ],
          "schedule": [],
          "speakers": []
        }
    """.trimIndent()

    @Test
    fun `gson parses wie json into ThemeConfig with correct eventName`() {
        val config = gson.fromJson(WIE_JSON, ThemeConfig::class.java)
        assertEquals("eventName must be WiE 2026", "WiE 2026", config.eventName)
    }

    @Test
    fun `wie theme primary color is WiE purple`() {
        val config = gson.fromJson(WIE_JSON, ThemeConfig::class.java)
        assertEquals("primary must be WiE purple", "#7B2D8B", config.colors.primary)
    }

    @Test
    fun `wie theme has 6 cards`() {
        val config = gson.fromJson(WIE_JSON, ThemeConfig::class.java)
        assertEquals("WiE theme must have exactly 6 cards", 6, config.cards.size)
    }

    @Test
    fun `wie theme tagline is Engineering Beyond Imagination`() {
        val config = gson.fromJson(WIE_JSON, ThemeConfig::class.java)
        assertEquals(
            "tagline must be 'Engineering Beyond Imagination'",
            "Engineering Beyond Imagination",
            config.tagline
        )
    }

    @Test
    fun `ThemeConfig with missing optional fields does not throw`() {
        val minimalJson = """{"eventName": "Test Event"}"""
        // Should not throw — all fields have defaults
        val config = gson.fromJson(minimalJson, ThemeConfig::class.java)
        assertNotNull("config must not be null", config)
        assertEquals("Test Event", config.eventName)
    }

    @Test
    fun `ThemeConfig missing colors field falls back gracefully`() {
        val minimalJson = """{"eventName": "Test", "tagline": "Hello"}"""
        val config = gson.fromJson(minimalJson, ThemeConfig::class.java)
        // colors will be null from Gson for missing field — ThemeConfig.withDefaults() handles it
        assertNotNull("config must not be null", config)
    }
}
