package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.MAX_ROUTE_POINTS
import com.noobexon.xposedfakelocation.data.ROUTE_POINT_DEDUP_EPSILON_METERS
import com.noobexon.xposedfakelocation.manager.ui.map.CoordinateTransform

/**
 * Parses an Amap Web Service API 2.0 walking-route response into a [WalkingRoute].
 *
 * Contract (verified against the official docs at lbs.amap.com, 2026-09):
 *  - Top level: `status` ("1" success / "0" failure), `info`, `infocode` ("10000" = OK), `route`.
 *  - `route.paths[]` — one path per planned alternative; each has `distance` (metres, string)
 *    and `steps[]`; `paths[].cost.duration` (seconds) is present when `show_fields=cost`.
 *  - Each step has a `polyline` coordinate string. For walking the point list is comma
 *    separated ("lon,lat,lon,lat,…"); older/other endpoints use semicolons between points, so
 *    both forms are accepted. Longitude always precedes latitude, in GCJ-02.
 *
 * The parser is pure (no Android framework calls) so it is directly unit-testable.
 */
object AmapRouteParser {

    /** Business error thrown when Amap replies with `status = "0"` or unusable route data. */
    class AmapApiException(
        val errorCode: WalkingErrorCode,
        val infocode: String?,
        safeMessage: String,
    ) : Exception(safeMessage)

    // Gson DTOs — field names mirror the wire format exactly.
    private class AmapResponse(
        val status: String?,
        val info: String?,
        val infocode: String?,
        val route: AmapRoute?,
    )

    private class AmapRoute(val paths: List<AmapPath>?)

    private class AmapPath(
        val distance: String?,
        val cost: AmapCost?,
        val steps: List<AmapStep>?,
    )

    private class AmapCost(val duration: String?)

    private class AmapStep(val polyline: String?)

    /**
     * Parses [json] into a [WalkingRoute] whose points are converted to WGS-84.
     *
     * @param originWgs84 the route origin already stored in WGS-84 (used verbatim as first point).
     * @param destinationWgs84 the route destination already stored in WGS-84.
     * @throws AmapApiException on API business errors or unusable route payloads.
     * @throws com.google.gson.JsonSyntaxException when [json] is not valid JSON at all.
     */
    fun parse(
        json: String,
        originWgs84: Coordinate,
        destinationWgs84: Coordinate,
    ): WalkingRoute {
        val response = com.google.gson.Gson().fromJson(json, AmapResponse::class.java)
            ?: throw AmapApiException(WalkingErrorCode.INVALID_ROUTE_DATA, null, "Empty response body")

        if (response.status != "1") {
            throw AmapApiException(
                errorCodeFor(response.infocode),
                response.infocode,
                "Amap API error: ${response.info ?: "unknown"} (infocode=${response.infocode ?: "?"})",
            )
        }

        val path = response.route?.paths?.firstOrNull()
            ?: throw AmapApiException(WalkingErrorCode.NO_ROUTE, response.infocode, "No walking path returned")

        val rawPoints = path.steps
            ?.flatMap { step -> parsePolyline(step.polyline.orEmpty()) }
            .orEmpty()
        if (rawPoints.isEmpty()) {
            throw AmapApiException(WalkingErrorCode.INVALID_ROUTE_DATA, response.infocode, "Route has no polyline points")
        }

        // GCJ-02 -> WGS-84 (identity outside mainland China), then drop consecutive duplicates.
        val wgsPoints = dedupeConsecutive(rawPoints.map { (lon, lat) ->
            val (wgsLat, wgsLon) = CoordinateTransform.gcj02ToWgs84(lat, lon)
            Coordinate(wgsLat, wgsLon)
        })
        if (wgsPoints.size < 2) {
            throw AmapApiException(WalkingErrorCode.INVALID_ROUTE_DATA, response.infocode, "Route degenerated to fewer than 2 points")
        }

        val points = capPoints(wgsPoints)
        val apiDistance = path.distance?.toDoubleOrNull()

        return WalkingRoute(
            origin = originWgs84,
            destination = destinationWgs84,
            points = points,
            totalDistanceMeters = RouteProgressEngine.totalDistanceMeters(points),
            apiReportedDistanceMeters = apiDistance?.takeIf { it.isFinite() && it >= 0 },
            expectedDurationSeconds = path.cost?.duration?.toIntOrNull()?.takeIf { it > 0 },
        )
    }

    /**
     * Splits an Amap polyline string into (longitude, latitude) pairs in GCJ-02.
     * Accepts both the walking "lon,lat,lon,lat" flat form and the semicolon-separated
     * "lon,lat;lon,lat" form. Invalid or out-of-range points are dropped.
     */
    fun parsePolyline(raw: String): List<Pair<Double, Double>> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        val pairs: List<Pair<Double?, Double?>?> = if (trimmed.contains(';')) {
            trimmed.split(';').map { segment ->
                val parts = segment.split(',')
                if (parts.size < 2) null else {
                    val lon = parts[0].trim().toDoubleOrNull()
                    val lat = parts[1].trim().toDoubleOrNull()
                    lon to lat
                }
            }
        } else {
            val numbers = trimmed.split(',').map { it.trim().toDoubleOrNull() }
            if (numbers.any { it == null }) return emptyList()
            @Suppress("UNCHECKED_CAST")
            (numbers as List<Double>).chunked(2).map { chunk ->
                if (chunk.size < 2) null else chunk[0] to chunk[1]
            }
        }

        return pairs.mapNotNull { pair ->
            val lon = pair?.first ?: return@mapNotNull null
            val lat = pair.second ?: return@mapNotNull null
            if (!lon.isFinite() || !lat.isFinite()) return@mapNotNull null
            if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return@mapNotNull null
            lon to lat
        }
    }

    /** Removes consecutive points closer than [ROUTE_POINT_DEDUP_EPSILON_METERS] to each other. */
    fun dedupeConsecutive(points: List<Coordinate>): List<Coordinate> {
        if (points.size < 2) return points
        val result = ArrayList<Coordinate>(points.size)
        result.add(points.first())
        for (point in points.asSequence().drop(1)) {
            val last = result.last()
            if (RouteProgressEngine.haversineMeters(last, point) >= ROUTE_POINT_DEDUP_EPSILON_METERS) {
                result.add(point)
            }
        }
        return result
    }

    /**
     * Bounds the persisted point count at [MAX_ROUTE_POINTS] by uniformly decimating the
     * trajectory. Uniform sampling keeps the shape on-road (adjacent points are a few metres
     * apart); the final point is always kept so arrival lands on the requested destination.
     */
    fun capPoints(points: List<Coordinate>): List<Coordinate> {
        if (points.size <= MAX_ROUTE_POINTS) return points
        val stride = (points.size + MAX_ROUTE_POINTS - 1) / MAX_ROUTE_POINTS
        val capped = points.filterIndexed { index, _ -> index % stride == 0 }.toMutableList()
        val last = points.last()
        if (capped.last() != last) capped.add(last)
        return capped
    }

    private fun errorCodeFor(infocode: String?): WalkingErrorCode = when (infocode) {
        "10001", "10009", "10013", "10014" -> WalkingErrorCode.API_AUTH_FAILED
        "10003", "10020", "10021", "10044" -> WalkingErrorCode.API_QUOTA_EXCEEDED
        else -> WalkingErrorCode.INVALID_ROUTE_DATA
    }
}
