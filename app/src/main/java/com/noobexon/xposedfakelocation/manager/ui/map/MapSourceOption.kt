package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.annotation.StringRes
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_SOURCE

/**
 * The set of map tile sources available in the manager app.
 *
 * @property tag Short string key persisted to [SharedPreferences].
 * @property labelRes String resource for this option's name.
 * @property isGcj02 Whether this tile source requires GCJ-02 (Mars coordinates) projection.
 */
enum class MapSourceOption(
    val tag: String,
    @StringRes val labelRes: Int,
    val isGcj02: Boolean
) {
    AMAP_VECTOR("amap_vector", R.string.map_source_amap_vector, isGcj02 = true),
    AMAP_SATELLITE("amap_satellite", R.string.map_source_amap_satellite, isGcj02 = true),
    TIANDITU_VECTOR("tianditu_vector", R.string.map_source_tianditu_vector, isGcj02 = false),
    OPEN_STREET_MAP("osm", R.string.map_source_osm, isGcj02 = false);

    companion object {
        /**
         * Returns the option whose [tag] matches [tag], falling back to [AMAP_VECTOR] for unknown
         * or empty tags.
         */
        fun fromTag(tag: String): MapSourceOption =
            entries.firstOrNull { it.tag == tag } ?: fromTag(DEFAULT_MAP_SOURCE)
    }
}
