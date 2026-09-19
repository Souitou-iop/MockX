package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.MAX_ROUTE_POINTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapRouteParserTest {

    private val origin = Coordinate(39.989643, 116.481028)
    private val destination = Coordinate(39.989000, 116.482000)

    @Test
    fun `parses flat comma separated walking polyline`() {
        // Amap walking v5 returns "lon1,lat1,lon2,lat2,…" (longitude first).
        val json = """
            {
              "status": "1",
              "info": "OK",
              "infocode": "10000",
              "count": "1",
              "route": {
                "paths": [
                  {
                    "distance": "250",
                    "cost": { "duration": "180" },
                    "steps": [
                      { "polyline": "116.481028,39.989643,116.481500,39.989700" },
                      { "polyline": "116.481500,39.989700,116.482000,39.989000" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val route = AmapRouteParser.parse(json, origin, destination)

        assertEquals(3, route.points.size)
        assertEquals("amap", route.provider)
        assertEquals(180, route.expectedDurationSeconds)
        assertEquals(250.0, route.apiReportedDistanceMeters!!, 1e-9)
        // Longitude came first on the wire; the GCJ-02 input was converted to WGS-84, so the
        // stored coordinates sit near (but not exactly at) the raw GCJ-02 values.
        assertEquals(116.481, route.points[0].longitude, 0.01)
        assertEquals(39.9896, route.points[0].latitude, 0.01)
        // WGS-84 stored distance is computed from the points themselves and is positive.
        assertTrue(route.totalDistanceMeters > 0)
    }

    @Test
    fun `parses semicolon separated polyline as well`() {
        val pairs = AmapRouteParser.parsePolyline("116.48,39.98;116.49,39.99")

        assertEquals(2, pairs.size)
        assertEquals(116.48, pairs[0].first, 1e-9)
        assertEquals(39.98, pairs[0].second, 1e-9)
    }

    @Test
    fun `status zero maps to auth error for invalid key`() {
        val json = """{"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}"""
        val error = try {
            AmapRouteParser.parse(json, origin, destination)
            null
        } catch (e: AmapRouteParser.AmapApiException) {
            e
        }!!

        assertEquals(WalkingErrorCode.API_AUTH_FAILED, error.errorCode)
        assertEquals("10001", error.infocode)
    }

    @Test
    fun `status zero maps to quota error`() {
        val json = """{"status":"0","info":"USER_DAILY_QUERY_OVER_LIMIT","infocode":"10044"}"""
        val error = try {
            AmapRouteParser.parse(json, origin, destination)
            null
        } catch (e: AmapRouteParser.AmapApiException) {
            e
        }!!

        assertEquals(WalkingErrorCode.API_QUOTA_EXCEEDED, error.errorCode)
    }

    @Test(expected = AmapRouteParser.AmapApiException::class)
    fun `empty paths yields NO_ROUTE`() {
        val json = """{"status":"1","infocode":"10000","route":{"paths":[]}}"""
        AmapRouteParser.parse(json, origin, destination)
    }

    @Test(expected = AmapRouteParser.AmapApiException::class)
    fun `empty steps yields invalid route data`() {
        val json = """{"status":"1","infocode":"10000","route":{"paths":[{"distance":"10","steps":[]}]}}"""
        AmapRouteParser.parse(json, origin, destination)
    }

    @Test(expected = AmapRouteParser.AmapApiException::class)
    fun `missing polyline yields invalid route data`() {
        val json = """{"status":"1","infocode":"10000","route":{"paths":[{"distance":"10","steps":[{}]}]}}"""
        AmapRouteParser.parse(json, origin, destination)
    }

    @Test
    fun `odd numbered flat polyline drops the orphan tail`() {
        // "lon1,lat1,lon2" — the dangling half-coordinate is invalid and gets discarded.
        val pairs = AmapRouteParser.parsePolyline("116.48,39.98,116.49")

        assertEquals(1, pairs.size)
        assertEquals(116.48, pairs[0].first, 1e-9)
        assertEquals(39.98, pairs[0].second, 1e-9)
    }

    @Test
    fun `non numeric tokens are rejected`() {
        assertTrue(AmapRouteParser.parsePolyline("116.48,abc,116.49,39.99").isEmpty())
    }

    @Test
    fun `out of range points are dropped`() {
        val pairs = AmapRouteParser.parsePolyline("116.48,39.98,116.49,139.99")

        assertEquals(1, pairs.size)
        assertEquals(39.98, pairs[0].second, 1e-9)
    }

    @Test
    fun `consecutive duplicate points are removed`() {
        val deduped = AmapRouteParser.dedupeConsecutive(
            listOf(
                Coordinate(39.0, 116.0),
                Coordinate(39.0, 116.0),          // exact duplicate
                Coordinate(39.0000001, 116.0),    // ~1 cm, below epsilon
                Coordinate(39.001, 116.0),        // real step (~111 m)
            )
        )

        assertEquals(2, deduped.size)
    }

    @Test
    fun `route point cap keeps the destination`() {
        val many = (0 until MAX_ROUTE_POINTS + 500).map { Coordinate(it * 1e-6, 116.0) }
        val capped = AmapRouteParser.capPoints(many)

        assertTrue(capped.size <= MAX_ROUTE_POINTS)
        assertEquals(many.last(), capped.last())
    }

    @Test
    fun `codec round trip preserves the route`() {
        val route = WalkingRoute(
            origin = origin,
            destination = destination,
            points = listOf(origin, Coordinate(39.9894, 116.4815), destination),
            totalDistanceMeters = 123.4,
            apiReportedDistanceMeters = 125.0,
            expectedDurationSeconds = 90,
        )

        val decoded = WalkingRouteCodec.decode(WalkingRouteCodec.encode(route))!!

        assertEquals(route.points.size, decoded.points.size)
        assertEquals(route.totalDistanceMeters, decoded.totalDistanceMeters, 1e-9)
        assertEquals(route.origin.latitude, decoded.origin.latitude, 1e-7)
        assertNull(WalkingRouteCodec.decode(null))
        assertNull(WalkingRouteCodec.decode("not json {"))
    }
}
