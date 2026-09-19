package com.noobexon.xposedfakelocation.xposed.utils

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_WALKING_SPEED
import com.noobexon.xposedfakelocation.data.PI
import com.noobexon.xposedfakelocation.data.RADIUS_EARTH
import com.noobexon.xposedfakelocation.data.WALKING_STATE_STALE_MS
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.attemptHideMockProvider
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.createFakeLocation
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.updateLocation
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Singleton holding the current spoofed location state and utilities for building
 * fake [Location] objects.
 *
 * All mutable fields are updated exclusively by [updateLocation], which pulls the
 * latest values from [PreferencesUtil] on every call. External callers (hooks) read
 * the fields but cannot write them directly.
 */
object LocationUtil {
    private const val TAG = "[LocationUtil]"

    /** Walking phases during which the dynamic position takes priority over the fixed point. */
    private val ACTIVE_WALKING_PHASES = setOf("WALKING", "PAUSED", "ARRIVED")

    /**
     * Optional logger wired in by [com.noobexon.xposedfakelocation.xposed.ModuleEntry].
     * When set, all [log] calls are routed through the libxposed logging channel.
     * Must be `@Volatile` because it is written from one thread and read from many.
     */
    @Volatile
    var logger: ((priority: Int, tag: String, message: String) -> Unit)? = null
    private fun log(message: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, message)

    /**
     * `true` while a valid dynamic walking position is being spoofed. Read by the Location
     * getter hooks to decide whether speed/bearing must follow the walking state.
     */
    @Volatile
    var isWalkingActive: Boolean = false
        private set

    /**
     * Bearing (degrees clockwise from north) of the current walking segment, or `null` when no
     * walking state is active. Applied by [createFakeLocation] and the `getBearing` hook.
     */
    @Volatile
    var walkingBearingDegrees: Float? = null
        private set

    /** Last time a stale-walking-state warning was logged; keeps the log to one line per minute. */
    @Volatile
    private var lastStaleLogElapsedMillis = 0L

    private const val STALE_LOG_INTERVAL_MS = 60_000L

    /** Current spoofed latitude in decimal degrees. Updated by [updateLocation]. */
    var latitude: Double = 0.0
        private set
    /** Current spoofed longitude in decimal degrees. Updated by [updateLocation]. */
    var longitude: Double = 0.0
        private set
    /** Current spoofed horizontal accuracy in metres. Zero means "not overridden". */
    var accuracy: Float = 0F
        private set
    /** Current spoofed altitude in metres above WGS-84. Zero means "not overridden". */
    var altitude: Double = 0.0
        private set
    /** Current spoofed vertical accuracy in metres. Zero means "not overridden". */
    var verticalAccuracy: Float = 0F
        private set
    /** Current spoofed MSL altitude in metres (API 34+). Zero means "not overridden". */
    var meanSeaLevel: Double = 0.0
        private set
    /** Current spoofed MSL altitude accuracy in metres (API 34+). Zero means "not overridden". */
    var meanSeaLevelAccuracy: Float = 0F
        private set
    /** Current spoofed ground speed in m/s. Zero means "not overridden". */
    var speed: Float = 0F
        private set
    /** Current spoofed speed accuracy in m/s. Zero means "not overridden". */
    var speedAccuracy: Float = 0F
        private set

    /**
     * Builds a [Location] object populated with the current spoofed field values.
     *
     * The spoofed state is refreshed from [PreferencesUtil] on every call ([updateLocation]),
     * so any hook — including the system_server hooks that have no other refresh point — always
     * injects the latest dynamic walking or fixed coordinates, never a stale snapshot. Hook-side
     * `updateLocation()` calls become redundant but harmless.
     *
     * If [originalLocation] is provided its metadata (time, elapsed realtime, etc.) is preserved;
     * otherwise a fresh [Location] is created with a slightly backdated timestamp to satisfy
     * recency checks in some apps.
     *
     * Only non-zero spoofed fields are applied, so unset optional fields fall back to whatever
     * the [originalLocation] carried. The mock-provider flag is cleared via [attemptHideMockProvider].
     *
     * This method is `@Synchronized` to prevent reading partially-updated fields if
     * [updateLocation] is called concurrently (both synchronize on this object, so the nested
     * call is reentrant).
     *
     * @param originalLocation Optional real location whose metadata is copied into the result.
     * @param provider Location provider string written into the returned [Location].
     */
    @Synchronized
    fun createFakeLocation(originalLocation: Location? = null, provider: String = LocationManager.GPS_PROVIDER): Location {
        updateLocation()

        val targetProvider = originalLocation?.provider?.takeIf { it.isNotEmpty() } ?: provider
        val fakeLocation = Location(targetProvider).apply {
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            if (originalLocation != null) {
                bearing = originalLocation.bearing
                bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees
                verticalAccuracyMeters = originalLocation.verticalAccuracyMeters
            }
        }

        fakeLocation.latitude = latitude
        fakeLocation.longitude = longitude

        // While walking, the direction must follow the route, not the original fix.
        if (walkingBearingDegrees != null) {
            fakeLocation.bearing = walkingBearingDegrees!!
        }

        if (accuracy != 0F) {
            fakeLocation.accuracy = accuracy
        } else if (originalLocation != null && originalLocation.accuracy > 0F) {
            fakeLocation.accuracy = originalLocation.accuracy
        } else {
            // Provide a realistic GPS accuracy (3.0m) if unset, preventing map SDKs from discarding it as invalid.
            fakeLocation.accuracy = 3.0f
        }

        if (altitude != 0.0) {
            fakeLocation.altitude = altitude
        }

        if (verticalAccuracy != 0F) {
            fakeLocation.verticalAccuracyMeters = verticalAccuracy
        }

        if (speed != 0F) {
            fakeLocation.speed = speed
        }

        if (speedAccuracy != 0F) {
            fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (meanSeaLevel != 0.0) {
                fakeLocation.mslAltitudeMeters = meanSeaLevel
            }

            if (meanSeaLevelAccuracy != 0F) {
                fakeLocation.mslAltitudeAccuracyMeters = meanSeaLevelAccuracy
            }
        }

        attemptHideMockProvider(fakeLocation)

        return fakeLocation
    }

    /**
     * Reads the latest spoofed location settings from [PreferencesUtil] and updates all
     * mutable fields on this object.
     *
     * Priority (规划.md §7.4): a valid dynamic walking position ([WALKING]/[PAUSED]/[ARRIVED]
     * state written by the manager's foreground service) always wins over the fixed
     * `last_clicked_location`; the fixed-point logic is the fallback.
     *
     * Coordinates are either taken from the walking state, the last clicked location, or
     * randomized within a user-configured radius using the Haversine formula. Optional fields
     * (accuracy, altitude, speed, etc.) are only updated when their corresponding "use" flag is
     * enabled in preferences — except during walking, where speed and bearing always follow
     * the route state.
     *
     * This method is `@Synchronized` to guarantee that [createFakeLocation] always sees a
     * consistent snapshot even when called from a different thread.
     */
    @Synchronized
    fun updateLocation() {
        runCatching {
            if (tryApplyWalkingLocation()) return

            val location = PreferencesUtil.getLastClickedLocation() ?: run {
                log("Last clicked location is null")
                return
            }

            if (PreferencesUtil.getUseRandomize() == true) {
                val randomizationRadius = PreferencesUtil.getRandomizeRadius() ?: DEFAULT_RANDOMIZE_RADIUS
                val (randomLat, randomLon) = getRandomLocation(location.latitude, location.longitude, randomizationRadius)
                latitude = randomLat
                longitude = randomLon
            } else {
                latitude = location.latitude
                longitude = location.longitude
            }

            if (PreferencesUtil.getUseAccuracy() == true) {
                accuracy = (PreferencesUtil.getAccuracy() ?: DEFAULT_ACCURACY).toFloat()
            }

            if (PreferencesUtil.getUseAltitude() == true) {
                altitude = PreferencesUtil.getAltitude() ?: DEFAULT_ALTITUDE
            }

            if (PreferencesUtil.getUseVerticalAccuracy() == true) {
                verticalAccuracy = PreferencesUtil.getVerticalAccuracy() ?: DEFAULT_VERTICAL_ACCURACY
            }

            if (PreferencesUtil.getUseMeanSeaLevel() == true) {
                meanSeaLevel = PreferencesUtil.getMeanSeaLevel() ?: DEFAULT_MEAN_SEA_LEVEL
            }

            if (PreferencesUtil.getUseMeanSeaLevelAccuracy() == true) {
                meanSeaLevelAccuracy = PreferencesUtil.getMeanSeaLevelAccuracy() ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
            }

            // Reset (not just overwrite) so a finished walking session or a disabled toggle can
            // never leave a stale speed/bearing behind.
            speed = if (PreferencesUtil.getUseSpeed() == true) {
                PreferencesUtil.getSpeed() ?: DEFAULT_SPEED
            } else {
                0F
            }
            if (PreferencesUtil.getUseSpeedAccuracy() == true) {
                speedAccuracy = PreferencesUtil.getSpeedAccuracy() ?: DEFAULT_SPEED_ACCURACY
            }
            walkingBearingDegrees = null
            isWalkingActive = false
        }.onFailure { log("Error - ${it.message}", priority = Log.ERROR) }
    }

    /**
     * Attempts to populate the spoofed state from the dynamic walking position.
     *
     * The state is only considered valid when walking is enabled, the phase is one of
     * [ACTIVE_WALKING_PHASES], the coordinates are finite and in range, and — while actively
     * walking — the manager app has written a fresh [updated-at timestamp][WALKING_STATE_STALE_MS].
     * PAUSED/ARRIVED states hold their coordinates indefinitely until the user stops.
     *
     * On a stale WALKING state the module falls back to the fixed-point logic (or real location)
     * instead of endlessly repeating the last dynamic coordinate; the warning is rate-limited
     * to one log line per minute and carries no coordinates.
     *
     * @return `true` when the dynamic state was applied and the caller can skip the fixed path.
     */
    private fun tryApplyWalkingLocation(): Boolean {
        if (!PreferencesUtil.getWalkingEnabled()) return false

        val phase = PreferencesUtil.getWalkingPhase()
        if (phase == null || phase !in ACTIVE_WALKING_PHASES) return false

        val lat = PreferencesUtil.getWalkingCurrentLatitude()
        val lon = PreferencesUtil.getWalkingCurrentLongitude()
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() ||
            lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0
        ) {
            return false
        }

        if (phase == "WALKING") {
            // `walking_updated_at` is wall-clock time (System.currentTimeMillis); evaluate it
            // against the same time base (see 追踪.md P1-001 — never mix with elapsedRealtime).
            val age = WalkingStatePolicy.ageMillis(
                PreferencesUtil.getWalkingUpdatedAt(),
                System.currentTimeMillis(),
            )
            if (WalkingStatePolicy.isStale(age, WALKING_STATE_STALE_MS)) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastStaleLogElapsedMillis > STALE_LOG_INTERVAL_MS) {
                    lastStaleLogElapsedMillis = now
                    log(
                        "Walking state is stale (age=${age?.let { "${it}ms" } ?: "unset"}); falling back to fixed location",
                        priority = Log.WARN,
                    )
                }
                return false
            }
        }

        latitude = lat
        longitude = lon

        // Speed and bearing always follow the walking session; the use_speed/use-accuracy
        // toggles only govern the fixed-point mode.
        speed = PreferencesUtil.getWalkingSpeed() ?: DEFAULT_WALKING_SPEED
        walkingBearingDegrees = PreferencesUtil.getWalkingBearing()
        isWalkingActive = true
        return true
    }

    /**
     * Calculates a uniformly distributed random point within a circle of [radiusInMeters]
     * centred at ([lat], [lon]) using the Haversine formula.
     *
     * @return A [Pair] of (latitude, longitude) in decimal degrees, clamped/normalised
     *         to valid WGS-84 ranges.
     */
    private fun getRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val radiusInRadians = radiusInMeters / RADIUS_EARTH

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)

        val rand1 = Random.nextDouble()
        val rand2 = Random.nextDouble()

        val distance = radiusInRadians * sqrt(rand1)
        val bearing = 2 * PI * rand2

        val sinDistance = sin(distance)
        val cosDistance = cos(distance)

        val newLatRad = asin(sinLat * cosDistance + cosLat * sinDistance * cos(bearing))
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sin(newLatRad)
        )

        val newLat = Math.toDegrees(newLatRad)
        var newLon = Math.toDegrees(newLonRad)

        newLon = ((newLon + 180) % 360 + 360) % 360 - 180

        val finalLat = newLat.coerceIn(-90.0, 90.0)

        return Pair(finalLat, newLon)
    }

    /**
     * Attempts to clear the mock-provider flag on [fakeLocation] via the hidden API
     * `Location.setIsFromMockProvider(false)`, bypassed using [HiddenApiBypass].
     *
     * Failure is logged but silently swallowed — some ROM variants or future API levels
     * may block this call, in which case spoofing still works but the mock flag remains set.
     */
    private fun attemptHideMockProvider(fakeLocation: Location) {
        runCatching {
            HiddenApiBypass.invoke(fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false)
            log("invoked hidden API - setIsFromMockProvider: false)")
        }.onFailure { log("Not possible to mock - ${it.message}", priority = Log.ERROR) }
    }

    /**
     * Logs all current spoofed location values. Not called in production, but useful for debugging.
     */
    @Suppress("unused")
    private fun logCurrentValues() {
        log("Updated fake location values to:")
        log("\tCoordinates: (latitude = $latitude, longitude = $longitude)")
        log("\tAccuracy: $accuracy")
        log("\tAltitude: $altitude")
        log("\tVertical Accuracy: $verticalAccuracy")
        log("\tMean Sea Level: $meanSeaLevel")
        log("\tMean Sea Level Accuracy: $meanSeaLevelAccuracy")
        log("\tSpeed: $speed")
        log("\tSpeed Accuracy: $speedAccuracy")
    }
}
