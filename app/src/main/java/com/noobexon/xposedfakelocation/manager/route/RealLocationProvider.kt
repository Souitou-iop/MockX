package com.noobexon.xposedfakelocation.manager.route

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Fetches the device's *real* location for use as the walking-route origin.
 *
 * The walking feature must always plan from the true device position — never from the current
 * virtual position — otherwise repeated planning compounds the offset. This object reads the
 * platform [LocationManager] directly and bypasses every spoofed coordinate.
 */
object RealLocationProvider {

    /**
     * Returns the freshest last-known fix across all enabled providers as a WGS-84
     * [Coordinate], or `null` when no permission is granted or no fix exists yet.
     */
    fun getLastKnown(context: Context): Coordinate? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = try {
            locationManager.getProviders(true)
        } catch (_: SecurityException) {
            return null
        }

        var best: android.location.Location? = null
        for (provider in providers) {
            val fix = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } ?: continue
            if (best == null || fix.time > best.time) best = fix
        }
        return best?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
            ?.let { Coordinate(it.latitude, it.longitude) }
    }
}
