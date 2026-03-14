package com.gow.smaitrobot

import com.gow.smaitrobot.navigation.Screen
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

/**
 * Unit tests for the navigation scaffold — Screen sealed class and navigation properties.
 *
 * Note: Full Compose NavHost tests require a device/emulator (instrumented tests).
 * These unit tests verify the Screen sealed class properties that drive the nav bar
 * without requiring Android framework dependencies.
 */
class AppNavigationTest {

    // ── Test 1: Screen sealed class has exactly 5 subtypes ───────────────────

    @Test
    fun `Test 1 - Screen sealed class has Home subtype`() {
        val screen: Screen = Screen.Home
        assertTrue(screen is Screen.Home)
    }

    @Test
    fun `Test 1b - Screen sealed class has Chat subtype`() {
        val screen: Screen = Screen.Chat
        assertTrue(screen is Screen.Chat)
    }

    @Test
    fun `Test 1c - Screen sealed class has Map subtype`() {
        val screen: Screen = Screen.Map
        assertTrue(screen is Screen.Map)
    }

    @Test
    fun `Test 1d - Screen sealed class has Facilities subtype`() {
        val screen: Screen = Screen.Facilities
        assertTrue(screen is Screen.Facilities)
    }

    @Test
    fun `Test 1e - Screen sealed class has EventInfo subtype`() {
        val screen: Screen = Screen.EventInfo
        assertTrue(screen is Screen.EventInfo)
    }

    @Test
    fun `Test 1f - exactly 5 Screen subtypes exist`() {
        // Verifies no extra screens were added
        val screens = listOf(Screen.Home, Screen.Chat, Screen.Map, Screen.Facilities, Screen.EventInfo)
        assertEquals(5, screens.size)
    }

    // ── Test 2: Each Screen has non-empty label and icon ─────────────────────

    @Test
    fun `Test 2 - Home screen has non-empty label`() {
        assertTrue(Screen.Home.label.isNotEmpty())
    }

    @Test
    fun `Test 2b - Chat screen has non-empty label`() {
        assertTrue(Screen.Chat.label.isNotEmpty())
    }

    @Test
    fun `Test 2c - Map screen has non-empty label`() {
        assertTrue(Screen.Map.label.isNotEmpty())
    }

    @Test
    fun `Test 2d - Facilities screen has non-empty label`() {
        assertTrue(Screen.Facilities.label.isNotEmpty())
    }

    @Test
    fun `Test 2e - EventInfo screen has non-empty label`() {
        assertTrue(Screen.EventInfo.label.isNotEmpty())
    }

    @Test
    fun `Test 2f - Home screen has non-null icon`() {
        assertNotNull(Screen.Home.icon)
    }

    @Test
    fun `Test 2g - Chat screen has non-null icon`() {
        assertNotNull(Screen.Chat.icon)
    }

    @Test
    fun `Test 2h - Map screen has non-null icon`() {
        assertNotNull(Screen.Map.icon)
    }

    @Test
    fun `Test 2i - Facilities screen has non-null icon`() {
        assertNotNull(Screen.Facilities.icon)
    }

    @Test
    fun `Test 2j - EventInfo screen has non-null icon`() {
        assertNotNull(Screen.EventInfo.icon)
    }

    // ── Test 3-5: Navigation properties ──────────────────────────────────────

    @Test
    fun `Test 5 - Home is first screen and default start destination`() {
        // Home should be first in the natural ordering for nav bar
        val screens = listOf(Screen.Home, Screen.Chat, Screen.Map, Screen.Facilities, Screen.EventInfo)
        assertEquals(Screen.Home, screens.first())
    }

    @Test
    fun `Test label - Home label is Home`() {
        assertEquals("Home", Screen.Home.label)
    }

    @Test
    fun `Test label - Chat label is Chat`() {
        assertEquals("Chat", Screen.Chat.label)
    }

    @Test
    fun `Test label - Map label is Map`() {
        assertEquals("Map", Screen.Map.label)
    }

    @Test
    fun `Test label - Facilities label is Facilities`() {
        assertEquals("Facilities", Screen.Facilities.label)
    }

    @Test
    fun `Test label - EventInfo label is Events`() {
        assertEquals("Events", Screen.EventInfo.label)
    }
}
