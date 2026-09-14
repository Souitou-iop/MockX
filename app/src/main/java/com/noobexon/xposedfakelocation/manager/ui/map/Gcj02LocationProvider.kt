package com.noobexon.xposedfakelocation.manager.ui.map

import android.content.Context
import android.location.Location
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider

/**
 * An osmdroid [GpsMyLocationProvider] that converts live GPS location updates (WGS-84)
 * to GCJ-02 when the active map source requires Mars coordinates.
 *
 * This ensures the device's "blue dot" overlay and [MyLocationNewOverlay.myLocation]
 * align precisely with the underlying road network and satellite imagery on GCJ-02 maps.
 */
class Gcj02LocationProvider(
    context: Context,
    private val isGcj02: () -> Boolean
) : GpsMyLocationProvider(context) {

    override fun onLocationChanged(location: Location) {
        val transformed = if (isGcj02()) {
            val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(location.latitude, location.longitude)
            Location(location).apply {
                latitude = gcjLat
                longitude = gcjLon
            }
        } else {
            location
        }
        super.onLocationChanged(transformed)
    }

    override fun getLastKnownLocation(): Location? {
        val loc = super.getLastKnownLocation() ?: return null
        return if (isGcj02()) {
            val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(loc.latitude, loc.longitude)
            Location(loc).apply {
                latitude = gcjLat
                longitude = gcjLon
            }
        } else {
            loc
        }
    }
}
