package com.noobexon.xposedfakelocation.manager.ui.theme

import android.content.SharedPreferences
import com.noobexon.xposedfakelocation.data.KEY_THEME_OPTION
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * App appearance mode, mirroring miuix's [ColorSchemeMode] one-to-one so the persisted index can
 * be handed straight to the [ThemeController][top.yukonga.miuix.kmp.theme.ThemeController].
 *
 * The four non-Monet modes use miuix's stock HyperOS palette; the Monet modes generate the
 * palette from the platform's dynamic colors, optionally seeded with a [MonetColor].
 */
enum class ThemeMode(val id: Int, val colorSchemeMode: ColorSchemeMode) {
    SYSTEM(0, ColorSchemeMode.System),
    LIGHT(1, ColorSchemeMode.Light),
    DARK(2, ColorSchemeMode.Dark),
    MONET_SYSTEM(3, ColorSchemeMode.MonetSystem),
    MONET_LIGHT(4, ColorSchemeMode.MonetLight),
    MONET_DARK(5, ColorSchemeMode.MonetDark);

    val isMonet: Boolean get() = this >= MONET_SYSTEM

    companion object {
        const val DEFAULT_ID = 0

        fun fromId(id: Int): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM

        /**
         * Reads the persisted mode, migrating the pre-miuix string tag (`theme_option`:
         * ""/"light"/"dark") when the new int key has never been written.
         */
        fun readFromPrefs(prefs: SharedPreferences, key: String, defaultId: Int): Int {
            if (prefs.contains(key)) return prefs.getInt(key, defaultId)
            return when (prefs.getString(KEY_THEME_OPTION, null)) {
                "light" -> LIGHT.id
                "dark" -> DARK.id
                else -> SYSTEM.id
            }
        }
    }
}
