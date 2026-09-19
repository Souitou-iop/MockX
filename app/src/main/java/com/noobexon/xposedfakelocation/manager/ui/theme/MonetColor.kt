package com.noobexon.xposedfakelocation.manager.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Seed color for miuix's Monet dynamic scheme. The default (index 0) passes `null` so miuix uses
 * the platform's own dynamic colors — on HyperOS/Android 12+ this follows the wallpaper.
 */
enum class MonetColor(val id: Int, val seed: Color?) {
    DEFAULT(0, null),
    BLUE(1, Color(0xFF2196F3)),
    GREEN(2, Color(0xFF4CAF50)),
    RED(3, Color(0xFFF44336)),
    YELLOW(4, Color(0xFFFFEB3B)),
    ORANGE(5, Color(0xFFFF9800)),
    PURPLE(6, Color(0xFF9C27B0)),
    PINK(7, Color(0xFFE91E63));

    companion object {
        const val DEFAULT_ID = 0

        fun fromId(id: Int): MonetColor = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
