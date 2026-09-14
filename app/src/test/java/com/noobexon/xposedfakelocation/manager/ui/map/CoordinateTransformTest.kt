package com.noobexon.xposedfakelocation.manager.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoordinateTransformTest {

    @Test
    fun outOfChinaBoundaries() {
        // Points inside China
        assertFalse(CoordinateTransform.outOfChina(39.9042, 116.4074)) // Beijing
        assertFalse(CoordinateTransform.outOfChina(31.2304, 121.4737)) // Shanghai
        assertFalse(CoordinateTransform.outOfChina(23.1291, 113.2644)) // Guangzhou

        // Points outside China
        assertTrue(CoordinateTransform.outOfChina(40.7128, -74.0060)) // New York
        assertTrue(CoordinateTransform.outOfChina(51.5074, -0.1278))  // London
        assertTrue(CoordinateTransform.outOfChina(35.6762, 139.6503)) // Tokyo (longitude > 137.8347)
        assertTrue(CoordinateTransform.outOfChina(0.0, 0.0))          // Null Island
    }

    @Test
    fun outsideChinaCoordinatesDoNotShift() {
        val nyLat = 40.7128
        val nyLon = -74.0060

        val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(nyLat, nyLon)
        assertEquals(nyLat, gcjLat, 1e-9)
        assertEquals(nyLon, gcjLon, 1e-9)

        val (wgsLat, wgsLon) = CoordinateTransform.gcj02ToWgs84(nyLat, nyLon)
        assertEquals(nyLat, wgsLat, 1e-9)
        assertEquals(nyLon, wgsLon, 1e-9)
    }

    @Test
    fun beijingTiananmenSquareTransformationAndReversibility() {
        // Tiananmen Square WGS-84
        val originalWgsLat = 39.908722
        val originalWgsLon = 116.397499

        val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(originalWgsLat, originalWgsLon)

        // In China, GCJ-02 offset shifts coordinates by roughly a few hundred meters
        assertTrue(abs(gcjLat - originalWgsLat) > 0.0001)
        assertTrue(abs(gcjLon - originalWgsLon) > 0.001)

        // Inverse transformation should match original WGS-84 within < 1e-7 degrees (~1 cm)
        val (revertedWgsLat, revertedWgsLon) = CoordinateTransform.gcj02ToWgs84(gcjLat, gcjLon)
        assertEquals(originalWgsLat, revertedWgsLat, 1e-7)
        assertEquals(originalWgsLon, revertedWgsLon, 1e-7)
    }

    @Test
    fun shanghaiOrientalPearlReversibility() {
        val originalWgsLat = 31.2397
        val originalWgsLon = 121.4998

        val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(originalWgsLat, originalWgsLon)
        val (revertedWgsLat, revertedWgsLon) = CoordinateTransform.gcj02ToWgs84(gcjLat, gcjLon)

        assertEquals(originalWgsLat, revertedWgsLat, 1e-7)
        assertEquals(originalWgsLon, revertedWgsLon, 1e-7)
    }

    @Test
    fun guangzhouTowerReversibility() {
        val originalWgsLat = 23.10647
        val originalWgsLon = 113.32446

        val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(originalWgsLat, originalWgsLon)
        val (revertedWgsLat, revertedWgsLon) = CoordinateTransform.gcj02ToWgs84(gcjLat, gcjLon)

        assertEquals(originalWgsLat, revertedWgsLat, 1e-7)
        assertEquals(originalWgsLon, revertedWgsLon, 1e-7)
    }
}
