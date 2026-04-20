package com.gow.smaitrobot.ui.theme

/**
 * BioRob Lab brand color constants as #RRGGBB hex strings.
 *
 * These are compile-time constants for use in tests, previews, and hardcoded
 * fallbacks. At runtime the [ThemeRepository] loads from the JSON asset.
 */
object BioRobColors {
    /** Primary brand color — BioRob blue */
    const val PRIMARY = "#0956A4"

    /** Secondary accent — BioRob gold (circuit nodes) */
    const val SECONDARY = "#E8A317"

    /** Tertiary accent — dark navy */
    const val TERTIARY = "#1A3D6D"

    /** Page background — near-white */
    const val BACKGROUND = "#F8F9FA"

    /** Text/icon color on primary surfaces */
    const val ON_PRIMARY = "#FFFFFF"

    /** Text color on background */
    const val ON_BACKGROUND = "#1A1A2E"

    /** Card/sheet surface */
    const val SURFACE = "#FFFFFF"

    /** Text color on surface */
    const val ON_SURFACE = "#1A1A2E"
}
