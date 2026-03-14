package com.gow.smaitrobot

import com.google.gson.Gson
import com.gow.smaitrobot.data.model.ChatMessage
import com.gow.smaitrobot.data.model.FeedbackData
import com.gow.smaitrobot.data.model.NavStatus
import com.gow.smaitrobot.data.model.PoiItem
import com.gow.smaitrobot.data.model.ThemeConfig
import com.gow.smaitrobot.ui.theme.WiEColors
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for WiE color constants, data models, and theme swap verification.
 */
class WiEThemeTest {

    private val gson = Gson()

    // ─── WiEColors constant tests ───

    @Test
    fun `WiEColors PRIMARY is WiE purple`() {
        assertEquals("PRIMARY must be WiE purple", "#7B2D8B", WiEColors.PRIMARY)
    }

    @Test
    fun `WiEColors SECONDARY is teal`() {
        assertEquals("SECONDARY must be WiE teal", "#00A99D", WiEColors.SECONDARY)
    }

    @Test
    fun `WiEColors TERTIARY is orange`() {
        assertEquals("TERTIARY must be WiE orange", "#F7941D", WiEColors.TERTIARY)
    }

    // ─── Theme swap verification ───

    private val DEFAULT_JSON = """
        {
          "eventName": "Default Event",
          "tagline": "Default Theme",
          "colors": {
            "primary": "#1565C0",
            "secondary": "#546E7A",
            "tertiary": "#78909C",
            "background": "#F5F5F5",
            "onPrimary": "#FFFFFF",
            "onBackground": "#212121",
            "surface": "#FFFFFF",
            "onSurface": "#212121"
          },
          "cards": [],
          "sponsors": [],
          "schedule": [],
          "speakers": []
        }
    """.trimIndent()

    private val WIE_JSON = """
        {
          "eventName": "WiE 2026",
          "tagline": "Engineering Beyond Imagination",
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
          "cards": [],
          "sponsors": [],
          "schedule": [],
          "speakers": []
        }
    """.trimIndent()

    @Test
    fun `default theme primary color differs from WiE theme primary`() {
        val defaultConfig = gson.fromJson(DEFAULT_JSON, ThemeConfig::class.java)
        val wieConfig = gson.fromJson(WIE_JSON, ThemeConfig::class.java)
        assertNotEquals(
            "default and WiE primary colors must differ (swap verification)",
            defaultConfig.colors.primary,
            wieConfig.colors.primary
        )
    }

    // ─── ChatMessage data model ───

    @Test
    fun `ChatMessage has id field`() {
        val msg = ChatMessage(id = "abc", text = "Hello", isUser = true)
        assertEquals("id must match", "abc", msg.id)
    }

    @Test
    fun `ChatMessage has text field`() {
        val msg = ChatMessage(id = "1", text = "Hello robot", isUser = false)
        assertEquals("text must match", "Hello robot", msg.text)
    }

    @Test
    fun `ChatMessage has isUser field`() {
        val userMsg = ChatMessage(id = "1", text = "Hi", isUser = true)
        val robotMsg = ChatMessage(id = "2", text = "Hello", isUser = false)
        assertTrue("user message isUser must be true", userMsg.isUser)
        assertFalse("robot message isUser must be false", robotMsg.isUser)
    }

    @Test
    fun `ChatMessage has timestamp field with default`() {
        val before = System.currentTimeMillis()
        val msg = ChatMessage(id = "1", text = "Test", isUser = true)
        val after = System.currentTimeMillis()
        assertTrue("timestamp must be >= before", msg.timestamp >= before)
        assertTrue("timestamp must be <= after", msg.timestamp <= after)
    }

    // ─── NavStatus data model ───

    @Test
    fun `NavStatus has destination field`() {
        val status = NavStatus(destination = "Room 101", status = "navigating")
        assertEquals("destination must match", "Room 101", status.destination)
    }

    @Test
    fun `NavStatus has status field`() {
        val status = NavStatus(destination = "Room 101", status = "arrived")
        assertEquals("status must match", "arrived", status.status)
    }

    @Test
    fun `NavStatus has progress field with default 0`() {
        val status = NavStatus(destination = "Room 101", status = "navigating")
        assertEquals("default progress must be 0f", 0f, status.progress, 0.001f)
    }

    @Test
    fun `NavStatus progress can be set`() {
        val status = NavStatus(destination = "Room 101", status = "navigating", progress = 0.5f)
        assertEquals("progress must be 0.5f", 0.5f, status.progress, 0.001f)
    }
}
