package com.noobexon.xposedfakelocation.manager.ui.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision coordinate transformation between WGS-84 (standard GPS) and GCJ-02 (Mars coordinates).
 *
 * GCJ-02 is the encrypted coordinate system mandated in mainland China. Outside mainland China,
 * coordinates are identical to WGS-84. The inverse transformation (GCJ-02 -> WGS-84) uses a
 * fixed-point iteration approach to reach sub-millimeter precision (< 1e-8 degrees).
 */
object CoordinateTransform {
    private const val A = 6378245.0 // Semi-major axis of the Krasovsky 1940 ellipsoid
    private const val EE = 0.00669342162296594323 // Eccentricity squared

    /**
     * Determines whether [lat] and [lon] fall outside the territory of mainland China.
     * Coordinate obfuscation is only applied within China's bounds.
     */
    fun outOfChina(lat: Double, lon: Double): Boolean {
        if (lon < 72.004 || lon > 137.8347) return true
        if (lat < 0.8293 || lat > 55.8271) return true
        return false
    }

    /**
     * Converts WGS-84 coordinates to GCJ-02.
     *
     * @param lat WGS-84 latitude in degrees.
     * @param lon WGS-84 longitude in degrees.
     * @return Pair of (gcjLatitude, gcjLongitude).
     */
    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (outOfChina(lat, lon)) return Pair(lat, lon)
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1.0 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1.0 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return Pair(lat + dLat, lon + dLon)
    }

    /**
     * Converts GCJ-02 coordinates to WGS-84 using 4 rounds of fixed-point iteration.
     *
     * Achieves sub-millimeter precision (< 1e-8 degrees error), eliminating the 1-2 meter
     * residual error of single-step inverse approximations.
     *
     * @param gcjLat GCJ-02 latitude in degrees.
     * @param gcjLon GCJ-02 longitude in degrees.
     * @return Pair of (wgsLatitude, wgsLongitude).
     */
    fun gcj02ToWgs84(gcjLat: Double, gcjLon: Double): Pair<Double, Double> {
        if (outOfChina(gcjLat, gcjLon)) return Pair(gcjLat, gcjLon)
        var wgsLat = gcjLat
        var wgsLon = gcjLon
        repeat(4) {
            val (currGcjLat, currGcjLon) = wgs84ToGcj02(wgsLat, wgsLon)
            val dLat = currGcjLat - gcjLat
            val dLon = currGcjLon - gcjLon
            wgsLat -= dLat
            wgsLon -= dLon
            if (abs(dLat) < 1e-8 && abs(dLon) < 1e-8) return Pair(wgsLat, wgsLon)
        }
        return Pair(wgsLat, wgsLon)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
