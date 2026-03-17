package com.gow.smaitrobot

import com.google.gson.Gson
import com.gow.smaitrobot.data.model.ChatMessage
import com.gow.smaitrobot.data.model.NavStatus
import com.gow.smaitrobot.data.model.ThemeConfig
import com.gow.smaitrobot.ui.theme.BioRobColors
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for BioRob color constants, data models, and theme swap verification.
 */
class WiEThemeTest {

    private val gson = Gson()

    // ─── BioRobColors constant tests ───

    @Test
    fun `BioRobColors PRIMARY is BioRob blue`() {
        assertEquals("PRIMARY must be BioRob blue", "#0956A4", BioRobColors.PRIMARY)
    }

    @Test
    fun `BioRobColors SECONDARY is BioRob gold`() {
        assertEquals("SECONDARY must be BioRob gold", "#E8A317", BioRobColors.SECONDARY)
    }

    @Test
    fun `BioRobColors TERTIARY is dark navy`() {
        assertEquals("TERTIARY must be dark navy", "#1A3D6D", BioRobColors.TERTIARY)
    }

    // ─── Theme swap verification ───

    private val BIOROB_JSON = """
        {
          "eventName": "BioRob Lab",
          "tagline": "Your Intelligent Campus Guide",
          "colors": {
            "primary": "#0956A4",
            "secondary": "#E8A317",
            "tertiary": "#1A3D6D",
            "background": "#F8F9FA",
            "onPrimary": "#FFFFFF",
            "onBackground": "#1A1A2E",
            "surface": "#FFFFFF",
            "onSurface": "#1A1A2E"
          },
          "cards": [],
          "sponsors": [],
          "schedule": [],
          "speakers": []
        }
    """.trimIndent()

    private val ALT_JSON = """
        {
          "eventName": "Alt Event",
          "tagline": "Different Theme",
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
    fun `BioRob theme primary color differs from alt theme primary`() {
        val biorobConfig = gson.fromJson(BIOROB_JSON, ThemeConfig::class.java)
        val altConfig = gson.fromJson(ALT_JSON, ThemeConfig::class.java)
        assertNotEquals(
            "BioRob and alt primary colors must differ (swap verification)",
            biorobConfig.colors.primary,
            altConfig.colors.primary
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
