package com.noobexon.xposedfakelocation.manager.route

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * JSON (de)serialization for [WalkingRoute] in the shared remote preference
 * `walking_route_json`. Both the manager app and the walking service use this single codec so
 * the stored format stays consistent.
 *
 * Coordinates are rounded to 7 decimal places (~1 cm) when encoding to keep the preference
 * blob small on long routes.
 */
object WalkingRouteCodec {
    private val gson = Gson()

    fun encode(route: WalkingRoute): String = gson.toJson(route.copy(points = route.points.map { it.rounded() }))

    fun decode(json: String?): WalkingRoute? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson(json, WalkingRoute::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    private fun Coordinate.rounded() = Coordinate(
        latitude = latitude.toBigDecimal().setScale(7, java.math.RoundingMode.HALF_UP).toDouble(),
        longitude = longitude.toBigDecimal().setScale(7, java.math.RoundingMode.HALF_UP).toDouble(),
    )
}
