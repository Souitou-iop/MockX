package com.noobexon.xposedfakelocation.manager.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RouteProgressEngineTest {

    // A tiny L-shaped route near the equator: 0.001° latitude ≈ 111 m, 0.001° longitude ≈ 111 m.
    private val points = listOf(
        Coordinate(0.0000, 0.0000),
        Coordinate(0.0010, 0.0000),
        Coordinate(0.0010, 0.0020),
    )

    private fun engine() = RouteProgressEngine(points)

    @Test
    fun `cumulative distances and total match segment lengths`() {
        val e = engine()
        val leg1 = RouteProgressEngine.haversineMeters(points[0], points[1])
        val leg2 = RouteProgressEngine.haversineMeters(points[1], points[2])

        assertEquals(0.0, e.cumulativeDistances[0], 1e-9)
        assertEquals(leg1, e.cumulativeDistances[1], 1e-6)
        assertEquals(leg1 + leg2, e.totalDistanceMeters, 1e-6)
        assertEquals(leg1 + leg2, RouteProgressEngine.totalDistanceMeters(points), 1e-6)
    }

    @Test
    fun `origin resolves to first point with north bearing when heading up`() {
        val snapshot = engine().positionAt(0.0)

        assertEquals(points[0].latitude, snapshot.coordinate.latitude, 1e-12)
        assertEquals(points[0].longitude, snapshot.coordinate.longitude, 1e-12)
        assertEquals(0f, snapshot.bearingDegrees, 0.5f)
        assertFalse(snapshot.arrived)
    }

    @Test
    fun `destination resolves to last point and reports arrival`() {
        val e = engine()
        val snapshot = e.positionAt(e.totalDistanceMeters)

        assertEquals(points[2].latitude, snapshot.coordinate.latitude, 1e-12)
        assertEquals(points[2].longitude, snapshot.coordinate.longitude, 1e-12)
        assertTrue(snapshot.arrived)
    }

    @Test
    fun `midpoint of first leg is interpolated linearly`() {
        val e = engine()
        val leg1 = e.cumulativeDistances[1]
        val half = e.positionAt(leg1 / 2)

        assertEquals(0.0005, half.coordinate.latitude, 1e-7)
        assertEquals(0.0, half.coordinate.longitude, 1e-9)
        assertFalse(half.arrived)
    }

    @Test
    fun `east-bound segment bearing is around 90 degrees`() {
        val east = listOf(Coordinate(10.0, 20.0), Coordinate(10.0, 20.001))
        val snapshot = RouteProgressEngine(east).positionAt(1.0)

        assertEquals(90f, snapshot.bearingDegrees, 0.5f)
    }

    @Test
    fun `out-of-range distances are clamped`() {
        val e = engine()

        val beforeStart = e.positionAt(-50.0)
        assertEquals(points[0].latitude, beforeStart.coordinate.latitude, 1e-12)
        assertFalse(beforeStart.arrived)

        val pastEnd = e.positionAt(e.totalDistanceMeters + 500.0)
        assertEquals(points[2].latitude, pastEnd.coordinate.latitude, 1e-12)
        assertTrue(pastEnd.arrived)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `single point route is rejected`() {
        RouteProgressEngine(listOf(Coordinate(1.0, 1.0)))
    }

    @Test
    fun `haversine matches known distance`() {
        // One degree of latitude ≈ 111.2 km.
        val meters = RouteProgressEngine.haversineMeters(Coordinate(0.0, 0.0), Coordinate(1.0, 0.0))
        assertTrue("actual=$meters", abs(meters - 111_195.0) < 200.0)
    }
}
