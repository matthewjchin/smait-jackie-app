package com.gow.smaitrobot.data.theme

import android.content.Context
import com.google.gson.Gson
import com.gow.smaitrobot.data.model.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Loads and exposes the active [ThemeConfig] from an asset JSON file.
 *
 * The theme is swappable at runtime by calling [load] with a different asset filename —
 * changing the JSON file changes all event branding with no code changes.
 *
 * Usage:
 * ```kotlin
 * val repo = ThemeRepository(context)
 * repo.load("babmdc2026_theme.json")
 * val config = repo.config.value
 * ```
 *
 * @param context Android Context used to access [android.content.res.AssetManager].
 */
class ThemeRepository(private val context: Context) {

    private val gson = Gson()

    private val _config = MutableStateFlow(ThemeConfig.default())

    /** The currently active theme configuration. Starts at [ThemeConfig.default()]. */
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    /**
     * Loads [assetFileName] from the app's assets folder, parses it as [ThemeConfig],
     * and updates [config]. On any error (file not found, parse failure, etc.) the
     * config falls back to [ThemeConfig.default()].
     *
     * Must be called from a coroutine; performs I/O on [Dispatchers.IO].
     *
     * @param assetFileName  Filename in `app/src/main/assets/` (e.g. "babmdc2026_theme.json").
     */
    suspend fun load(assetFileName: String = "babmdc2026_theme.json") {
        val loaded = withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetFileName).use { stream ->
                    val json = stream.bufferedReader().readText()
                    val parsed = gson.fromJson(json, ThemeConfig::class.java)
                    // Gson may produce a partially-null object for sparse JSON;
                    // apply safe defaults for any null top-level colors.
                    parsed.withSafeDefaults()
                }
            } catch (e: Exception) {
                ThemeConfig.default()
            }
        }
        _config.value = loaded
    }

    /**
     * Synchronous variant for use from non-coroutine callers (e.g. tests or Application.onCreate).
     * Falls back to [ThemeConfig.default()] on any failure.
     */
    fun loadSync(assetFileName: String = "babmdc2026_theme.json") {
        _config.value = try {
            context.assets.open(assetFileName).use { stream ->
                val json = stream.bufferedReader().readText()
                gson.fromJson(json, ThemeConfig::class.java).withSafeDefaults()
            }
        } catch (e: Exception) {
            ThemeConfig.default()
        }
    }
}

/**
 * Returns a copy of this [ThemeConfig] where any null fields that Gson left unpopulated
 * (via reflection, bypassing Kotlin's non-null guarantees) are replaced with their
 * default values. Prevents NPEs when JSON is sparse.
 *
 * The `@Suppress` annotations are needed because Gson can set non-null Kotlin fields
 * to null at runtime via reflection, so the Elvis operator is actually reachable.
 */
@Suppress("SENSELESS_COMPARISON")
private fun ThemeConfig.withSafeDefaults(): ThemeConfig {
    val defaults = ThemeConfig.default()
    return copy(
        eventName = if (eventName.isNullOrEmpty()) defaults.eventName else eventName,
        tagline = if (tagline.isNullOrEmpty()) defaults.tagline else tagline,
        logoAsset = if (logoAsset.isNullOrEmpty()) defaults.logoAsset else logoAsset,
        colors = if (colors == null) defaults.colors else colors,
        fonts = if (fonts == null) defaults.fonts else fonts,
        sponsors = if (sponsors == null) defaults.sponsors else sponsors,
        cards = if (cards == null) defaults.cards else cards,
        schedule = if (schedule == null) defaults.schedule else schedule,
        speakers = if (speakers == null) defaults.speakers else speakers
    )
}
