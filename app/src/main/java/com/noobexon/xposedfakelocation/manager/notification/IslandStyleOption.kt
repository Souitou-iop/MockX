package com.noobexon.xposedfakelocation.manager.notification

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_ISLAND_STYLE

/**
 * The styled notification surface used while a session is active (walking simulation and fixed
 * virtual location): Xiaomi HyperOS Super Island vs. the Android 16 Google Live Update progress
 * style ([AndroidLiveUpdateAdapter]). [AUTO] keeps the device-appropriate default — Super Island
 * on Xiaomi hardware, Google Live Update elsewhere. The two styles are mutually exclusive: on
 * OS3 the Google style would win the render and the island extras would be ignored.
 *
 * @property tag Short string key persisted to [android.content.SharedPreferences].
 * @property labelRes String resource for this option's name.
 */
enum class IslandStyleOption(
    val tag: String,
    @StringRes val labelRes: Int,
) {
    AUTO("auto", R.string.island_style_auto),
    SUPER_ISLAND("super_island", R.string.island_style_super_island),
    GOOGLE_LIVE_UPDATE("google_live_update", R.string.island_style_google_live_update);

    companion object {
        /**
         * Returns the option whose [tag] matches [tag], falling back to [AUTO] for unknown or
         * empty tags.
         */
        fun fromTag(tag: String): IslandStyleOption =
            entries.firstOrNull { it.tag == tag } ?: fromTag(DEFAULT_ISLAND_STYLE)
    }
}