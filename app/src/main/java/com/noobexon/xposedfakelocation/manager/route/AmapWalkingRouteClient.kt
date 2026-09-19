package com.noobexon.xposedfakelocation.manager.route

import android.util.Log
import com.google.gson.JsonSyntaxException
import com.noobexon.xposedfakelocation.manager.ui.map.CoordinateTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale

/**
 * Plans walking routes through the Amap Web Service API 2.0
 * (`GET https://restapi.amap.com/v5/direction/walking`).
 *
 * Constraints honoured here:
 *  - Uses the JDK's [HttpURLConnection]; no new network dependency is introduced.
 *  - All I/O runs on [Dispatchers.IO] with explicit connect/read timeouts.
 *  - The API key is never logged and never appears in exception messages; request URLs are
 *    logged at most as path + parameter names.
 *  - The Amap API works in GCJ-02: WGS-84 inputs are converted before the call and the
 *    returned polyline is converted back to WGS-84 by [AmapRouteParser].
 */
class AmapWalkingRouteClient : WalkingRouteClient {

    override suspend fun planWalkingRoute(
        apiKey: String,
        origin: Coordinate,
        destination: Coordinate,
    ): Result<WalkingRoute> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Missing API key" }
            require(origin.isValid() && destination.isValid()) { "Invalid origin/destination" }

            val connection = openConnection(apiKey, origin, destination)
            try {
                val httpStatus = connection.responseCode
                val body = readBody(connection, httpStatus)
                if (httpStatus !in 200..299) {
                    throw WalkingException(WalkingErrorCode.NETWORK_UNAVAILABLE, "HTTP $httpStatus from route service")
                }
                AmapRouteParser.parse(body, origin, destination)
            } finally {
                connection.disconnect()
            }
        }.recoverCatching { error ->
            throw when (error) {
                is WalkingException -> error
                is AmapRouteParser.AmapApiException ->
                    WalkingException(error.errorCode, error.message ?: "Route API error", error.infocode)
                is JsonSyntaxException -> WalkingException(WalkingErrorCode.INVALID_ROUTE_DATA, "Unparseable route response")
                is SocketTimeoutException -> WalkingException(WalkingErrorCode.NETWORK_TIMEOUT, "Route request timed out")
                is UnknownHostException -> WalkingException(WalkingErrorCode.NETWORK_UNAVAILABLE, "Network unavailable")
                is IOException -> WalkingException(WalkingErrorCode.NETWORK_UNAVAILABLE, "Network error: ${error.javaClass.simpleName}")
                else -> {
                    Log.e(TAG, "Unexpected route planning failure: ${error.javaClass.simpleName}")
                    WalkingException(WalkingErrorCode.INVALID_ROUTE_DATA, "Unexpected route planning failure")
                }
            }
        }
    }

    private fun openConnection(apiKey: String, origin: Coordinate, destination: Coordinate): HttpURLConnection {
        val originGcj = origin.toGcj02().toRequestParam()
        val destinationGcj = destination.toGcj02().toRequestParam()
        val url = buildString {
            append(BASE_URL)
            append("?key=").append(URLEncoder.encode(apiKey, Charsets.UTF_8.name()))
            append("&origin=").append(URLEncoder.encode(originGcj, Charsets.UTF_8.name()))
            append("&destination=").append(URLEncoder.encode(destinationGcj, Charsets.UTF_8.name()))
            append("&show_fields=").append(SHOW_FIELDS)
        }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readBody(connection: HttpURLConnection, httpStatus: Int): String {
        val stream = (if (httpStatus in 200..299) connection.inputStream else connection.errorStream)
            ?: return ""
        return stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
    }

    private fun Coordinate.toGcj02(): Coordinate {
        val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(latitude, longitude)
        return Coordinate(gcjLat, gcjLon)
    }

    /** "lon,lat" with at most 6 decimal places, as the API requires (longitude first). */
    private fun Coordinate.toRequestParam(): String =
        String.format(Locale.US, "%.6f,%.6f", longitude, latitude)

    companion object {
        private const val TAG = "AmapWalkingRouteClient"
        private const val BASE_URL = "https://restapi.amap.com/v5/direction/walking"
        private const val SHOW_FIELDS = "cost,polyline"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}

/** Minimal client abstraction so tests/UI can swap the route source. */
interface WalkingRouteClient {
    suspend fun planWalkingRoute(apiKey: String, origin: Coordinate, destination: Coordinate): Result<WalkingRoute>
}

/** Carrier for a sanitized walking-feature failure: code for the UI, never a key or full URL. */
class WalkingException(
    val errorCode: WalkingErrorCode,
    safeMessage: String,
    val infocode: String? = null,
) : Exception(safeMessage)
