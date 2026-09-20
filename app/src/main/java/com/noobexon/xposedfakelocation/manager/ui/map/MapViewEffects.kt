package com.noobexon.xposedfakelocation.manager.ui.map

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_DELAY_MS
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_MAX_ATTEMPTS
import com.noobexon.xposedfakelocation.data.WORLD_MAP_ZOOM
import com.noobexon.xposedfakelocation.manager.route.RealLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Ensures [locationOverlay] is present in [mapView]'s overlay list.
 *
 * Guarded by a containment check so repeated recompositions never add the overlay twice. Keyed on
 * [Unit] so it runs exactly once per composition.
 *
 * @param mapView The map to add the overlay to.
 * @param locationOverlay The "blue dot" overlay that shows the user's real device location.
 */
@Composable
internal fun AddLocationOverlayToMap(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay
) {
    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
    }
}

/**
 * Collects [centerMapEvent] in a lifecycle-aware coroutine and animates the camera to the user's
 * real location on each emission.
 *
 * Collection is scoped to [Lifecycle.State.STARTED] via [repeatOnLifecycle] so that events
 * buffered while the screen is backgrounded are drained immediately on resume rather than being
 * silently dropped or processed off-screen.
 *
 * If the location overlay has no fix yet, a short Toast is shown instead of attempting an
 * animation to a null point.
 *
 * @param mapView The map whose camera is animated.
 * @param locationOverlay Source of the current device location.
 * @param centerMapEvent Hot [Flow] backed by [MapViewModel._centerMapEvent].
 */
@Composable
internal fun HandleCenterMapEvent(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    centerMapEvent: Flow<Unit>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val userLocationNotAvailable = stringResource(R.string.toast_user_location_not_available)
    LaunchedEffect(lifecycleOwner, centerMapEvent) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            centerMapEvent.collect {
                val userLocation = locationOverlay.myLocation
                if (userLocation != null) {
                    mapView.controller.animateTo(userLocation)
                } else {
                    Toast.makeText(context, userLocationNotAvailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * Collects [goToPointEvent] in a lifecycle-aware coroutine, animates the camera to the target
 * coordinate, and updates the spoof marker via [onClickedLocationChange].
 *
 * Like [HandleCenterMapEvent], collection is scoped to [Lifecycle.State.STARTED] to prevent
 * navigation events from being processed while the screen is in the back stack.
 *
 * @param mapView The map whose camera is animated.
 * @param goToPointEvent Hot [Flow] backed by [MapViewModel._goToPointEvent].
 * @param onClickedLocationChange Callback that updates [MapViewModel.uiState.lastClickedLocation].
 */
@Composable
internal fun HandleGoToPointEvent(
    mapView: MapView,
    mapSource: MapSourceOption,
    goToPointEvent: Flow<GeoPoint>,
    onClickedLocationChange: (GeoPoint?) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, goToPointEvent, mapSource) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            goToPointEvent.collect { geoPoint ->
                val displayPoint = if (mapSource.isGcj02) {
                    val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(
                        geoPoint.latitude,
                        geoPoint.longitude
                    )
                    GeoPoint(gcjLat, gcjLon)
                } else {
                    geoPoint
                }
                mapView.controller.animateTo(displayPoint)
                onClickedLocationChange(geoPoint)
            }
        }
    }
}

/**
 * Keeps the spoof-target [Marker] on the map in sync with [lastClickedLocation].
 *
 * - When [lastClickedLocation] is non-null the marker is added to [mapView]'s overlay list (if not
 *   already present), moved to the new position, and the camera is animated to it.
 * - When [lastClickedLocation] is `null` the marker is removed and the map is invalidated.
 *
 * The effect is keyed on [lastClickedLocation] and [mapSource] so it reruns when the marker moves
 * or the active tile projection changes.
 *
 * @param mapView The map whose overlays are updated.
 * @param userMarker The persistent spoof-target marker instance.
 * @param lastClickedLocation The current spoof target in WGS-84, or `null` if none.
 * @param mapSource Current tile source option.
 */
@Composable
internal fun HandleMarkerUpdates(
    mapView: MapView,
    userMarker: Marker,
    lastClickedLocation: GeoPoint?,
    mapSource: MapSourceOption,
) {
    LaunchedEffect(lastClickedLocation, mapSource) {
        if (lastClickedLocation != null) {
            // Add the marker to the map if not already added
            if (!mapView.overlays.contains(userMarker)) {
                mapView.overlays.add(userMarker)
            }
            val displayPoint = if (mapSource.isGcj02) {
                val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(
                    lastClickedLocation.latitude,
                    lastClickedLocation.longitude
                )
                GeoPoint(gcjLat, gcjLon)
            } else {
                lastClickedLocation
            }
            userMarker.position = displayPoint
            mapView.controller.animateTo(displayPoint)
            mapView.invalidate()
        } else {
            // Remove the marker from the map if it exists
            if (mapView.overlays.contains(userMarker)) {
                mapView.overlays.remove(userMarker)
                mapView.invalidate()
            }
        }
    }
}

/**
 * Installs a [MapEventsOverlay] that translates single-tap gestures into spoof-marker placements.
 *
 * The overlay is created once and lives for the lifetime of [mapView] (keyed on it). [isPlaying]
 * and [onClickedLocationChange] are captured via [rememberUpdatedState] so that toggling spoofing
 * on/off never causes the overlay to be torn down and recreated — avoiding the resulting map
 * flicker and gesture interruption.
 *
 * Taps are ignored while [isPlaying] is `true` so the marker cannot be accidentally moved during
 * an active spoofing session.
 *
 * @param mapView The map to install the click listener on.
 * @param isPlaying Whether spoofing is currently active.
 * @param mapSource Current tile source option used to un-obfuscate tap coordinates.
 * @param onClickedLocationChange Callback fired with the tapped [GeoPoint] in WGS-84.
 */
@Composable
internal fun SetupMapClickListener(
    mapView: MapView,
    isPlaying: Boolean,
    mapSource: MapSourceOption,
    onClickedLocationChange: (GeoPoint?) -> Unit
) {
    // Keep the latest values without re-creating the overlay each time spoofing toggles.
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentMapSource by rememberUpdatedState(mapSource)
    val currentOnClickedLocationChange by rememberUpdatedState(onClickedLocationChange)
    DisposableEffect(mapView) {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!currentIsPlaying) {
                    val wgsPoint = if (currentMapSource.isGcj02) {
                        val (wgsLat, wgsLon) = CoordinateTransform.gcj02ToWgs84(p.latitude, p.longitude)
                        GeoPoint(wgsLat, wgsLon)
                    } else {
                        p
                    }
                    currentOnClickedLocationChange(wgsPoint)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(mapEventsOverlay)

        onDispose {
            mapView.overlays.remove(mapEventsOverlay)
        }
    }
}

/**
 * Positions the camera once per [mapView].
 *
 * - **First ever load** ([hasResolvedInitialLocation] == false): resolves an initial target — saved
 *   marker, else last-known device location, else a short GPS poll, else a world-view fallback —
 *   clearing the loading state when done and reporting completion via [onInitialLocationResolved].
 * - **Re-entry** ([hasResolvedInitialLocation] == true): restores the last camera instantly with no
 *   spinner and no detection. The remembered [mapZoom] is re-applied; for the no-marker case the
 *   last [userLocation] is re-centered. Marker centering is left to [HandleMarkerUpdates].
 *
 * Either way, once positioned it never re-centers again for this [mapView], so later marker changes
 * (taps, go-to-point, clear) don't reset the zoom. The effect is keyed on [lastClickedLocation] so
 * an asynchronously-loaded saved marker can supersede an in-flight device-location lookup.
 */
@Composable
internal fun CenterMapOnUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    lastClickedLocation: GeoPoint?,
    userLocation: GeoPoint?,
    mapZoom: Double?,
    mapSource: MapSourceOption,
    hasResolvedInitialLocation: Boolean,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit,
    onInitialLocationResolved: () -> Unit,
) {
    val context = LocalContext.current
    val centeredOnce = remember(mapView) { mutableStateOf(false) }
    LaunchedEffect(mapView, lastClickedLocation) {
        if (centeredOnce.value) return@LaunchedEffect

        if (hasResolvedInitialLocation) {
            // Re-entry: restore the last camera without re-detecting or showing a spinner.
            mapView.controller.setZoom(mapZoom ?: DEFAULT_MAP_ZOOM)
            if (lastClickedLocation == null && userLocation != null) {
                val displayUserLoc = if (mapSource.isGcj02) {
                    val (lat, lon) = CoordinateTransform.wgs84ToGcj02(userLocation.latitude, userLocation.longitude)
                    GeoPoint(lat, lon)
                } else {
                    userLocation
                }
                mapView.controller.setCenter(displayUserLoc)
            }
            centeredOnce.value = true
            return@LaunchedEffect
        }

        if (lastClickedLocation != null) {
            centerOnMarkerLocation(mapView, lastClickedLocation, mapZoom, mapSource, onMapZoomChange, onLoadingFinished)
        } else {
            val lastKnown = getLastKnownDeviceLocation(context)
            if (lastKnown != null) {
                centerOnGeoPoint(mapView, lastKnown, mapZoom, mapSource, onUserLocationChange, onMapZoomChange, onLoadingFinished)
            } else {
                val found = tryToFindAndCenterUserLocation(mapView, locationOverlay, mapZoom, mapSource, onUserLocationChange, onMapZoomChange, onLoadingFinished)
                if (!found) {
                    centerOnDefaultLocation(mapView, onMapZoomChange, onLoadingFinished)
                }
            }
        }
        onInitialLocationResolved()
        centeredOnce.value = true
    }
}

/**
 * Restores the camera to a previously placed spoof marker during initial load.
 *
 * Uses the persisted [mapZoom] when available so the zoom level is exactly as the user left it,
 * falling back to [DEFAULT_MAP_ZOOM] for first-ever launches.
 *
 * @param mapView The map to position.
 * @param markerLocation The spoof-target coordinate in WGS-84 to centre on.
 * @param mapZoom Persisted zoom level, or `null` if not yet set.
 * @param mapSource Current tile source option.
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 */
private suspend fun centerOnMarkerLocation(
    mapView: MapView,
    markerLocation: GeoPoint,
    mapZoom: Double?,
    mapSource: MapSourceOption,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    mapView.controller.setZoom(zoom)
    val displayPoint = if (mapSource.isGcj02) {
        val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(markerLocation.latitude, markerLocation.longitude)
        GeoPoint(gcjLat, gcjLon)
    } else {
        markerLocation
    }
    mapView.controller.animateTo(displayPoint)
    onMapZoomChange(zoom)
    onLoadingFinished()
}

/**
 * Centres the camera on [point] and propagates the resolved location and zoom up to the ViewModel.
 * Used when a last-known device location is available instantly without polling.
 *
 * Uses the persisted [mapZoom] when available so the zoom level is exactly as the user left it,
 * falling back to [DEFAULT_MAP_ZOOM] for first-ever launches.
 *
 * @param mapView The map to centre.
 * @param point The device location in WGS-84 to centre on.
 * @param mapZoom Persisted zoom level, or `null` if not yet set.
 * @param mapSource Current tile source option.
 * @param onUserLocationChange Callback to cache the location in [MapViewModel].
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 */
private fun centerOnGeoPoint(
    mapView: MapView,
    point: GeoPoint,
    mapZoom: Double?,
    mapSource: MapSourceOption,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    mapView.controller.setZoom(zoom)
    val displayPoint = if (mapSource.isGcj02) {
        val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(point.latitude, point.longitude)
        GeoPoint(gcjLat, gcjLon)
    } else {
        point
    }
    mapView.controller.setCenter(displayPoint)
    onUserLocationChange(point)
    onMapZoomChange(zoom)
    onLoadingFinished()
}

/**
 * Returns the freshest last-known device fix as a [GeoPoint], or `null` if none is available or
 * permission is not granted. Delegates to [RealLocationProvider]; runs on [Dispatchers.IO]
 * because [LocationManager.getLastKnownLocation] can perform disk I/O on some devices.
 *
 * @param context Application context used to access [LocationManager] and check permissions.
 * @return The freshest available [GeoPoint], or `null`.
 */
private suspend fun getLastKnownDeviceLocation(context: Context): GeoPoint? = withContext(Dispatchers.IO) {
    RealLocationProvider.getLastKnown(context)?.let { GeoPoint(it.latitude, it.longitude) }
}

/**
 * Polls [locationOverlay] up to [LOCATION_DETECTION_MAX_ATTEMPTS] times (with
 * [LOCATION_DETECTION_DELAY_MS] between each attempt) and centres the camera if a fix is obtained.
 *
 * This path is taken when [getLastKnownDeviceLocation] returns `null` — i.e. no cached fix exists —
 * and gives the GPS/network provider a short window to acquire one before falling back to
 * [centerOnDefaultLocation].
 *
 * Uses the persisted [mapZoom] when available so the zoom level is exactly as the user left it,
 * falling back to [DEFAULT_MAP_ZOOM] for first-ever launches.
 *
 * @param mapView The map to centre if a location is found.
 * @param locationOverlay Source of live location fixes.
 * @param mapZoom Persisted zoom level, or `null` if not yet set.
 * @param onUserLocationChange Callback to cache the location in [MapViewModel].
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 * @return `true` if a location was found and the camera was centred; `false` if the timeout elapsed.
 */
private suspend fun tryToFindAndCenterUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    mapZoom: Double?,
    mapSource: MapSourceOption,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
): Boolean {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    repeat(LOCATION_DETECTION_MAX_ATTEMPTS) {
        val userLocation = locationOverlay.myLocation
        if (userLocation != null) {
            val wgsLocation = if (mapSource.isGcj02) {
                val (wgsLat, wgsLon) = CoordinateTransform.gcj02ToWgs84(userLocation.latitude, userLocation.longitude)
                GeoPoint(wgsLat, wgsLon)
            } else {
                userLocation
            }
            onUserLocationChange(wgsLocation)
            mapView.controller.setZoom(zoom)
            mapView.controller.animateTo(userLocation)
            onMapZoomChange(zoom)
            onLoadingFinished()
            return true
        }
        delay(LOCATION_DETECTION_DELAY_MS)
    }
    return false
}

/**
 * Last-resort fallback: centres the camera on (0°, 0°) at [WORLD_MAP_ZOOM] when no device
 * location could be determined after [LOCATION_DETECTION_MAX_ATTEMPTS] polling attempts.
 *
 * @param mapView The map to centre.
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 */
private fun centerOnDefaultLocation(
    mapView: MapView,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    mapView.controller.setZoom(WORLD_MAP_ZOOM)
    mapView.controller.setCenter(GeoPoint(0.0, 0.0))
    onMapZoomChange(WORLD_MAP_ZOOM)
    onLoadingFinished()
}

/**
 * Ties the osmdroid [MapView] and [MyLocationNewOverlay] to the Compose lifecycle.
 *
 * On entry: calls [MapView.onResume] and re-enables location updates so that the overlay starts
 * receiving GPS fixes as soon as the screen becomes active.
 *
 * On dispose (i.e. when the user navigates away):
 * 1. Captures the current zoom via [onMapZoomChange] so it can be restored on re-entry without
 *    re-running location detection or showing a spinner (see [CenterMapOnUserLocation]).
 * 2. Disables location updates to stop battery drain while the screen is off-stack.
 * 3. Clears all overlays, calls [MapView.onPause] and [MapView.onDetach] to release osmdroid
 *    resources correctly.
 *
 * Keyed on [Unit] so it runs exactly once per composition, matching the [MapView] lifetime.
 *
 * @param mapView The osmdroid map view to manage.
 * @param locationOverlay The location overlay whose updates are started/stopped.
 * @param onMapZoomChange Callback to persist the final zoom level on dispose.
 */
@Composable
internal fun ManageMapViewLifecycle(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    onMapZoomChange: (Double) -> Unit
) {
    DisposableEffect(Unit) {
        mapView.onResume()
        locationOverlay.enableMyLocation()
        onDispose {
            // Capture the final zoom so it can be restored when the map screen is returned to.
            onMapZoomChange(mapView.zoomLevelDouble)
            locationOverlay.disableMyLocation()
            mapView.overlays.clear()
            mapView.onPause()
            mapView.onDetach()
        }
    }
}

/**
 * Draws the prepared walking route as a polyline, converting the internally stored WGS-84
 * points to GCJ-02 when the active tile source requires it ([MapSourceOption.isGcj02]).
 *
 * The overlay is inserted at the bottom of the overlay stack so markers and the blue-dot
 * location overlay always render above it. When [route] is `null` the overlay is removed.
 * On a new route the camera is fitted to the route's bounding box.
 *
 * @param mapView The map to draw on.
 * @param route The prepared route in WGS-84, or `null` to clear.
 * @param mapSource Current tile source option, determining the display coordinate system.
 */
@Composable
internal fun HandleWalkingRouteOverlay(
    mapView: MapView,
    route: com.noobexon.xposedfakelocation.manager.route.WalkingRoute?,
    mapSource: MapSourceOption,
) {
    val polyline = remember(mapView) {
        org.osmdroid.views.overlay.Polyline(mapView).apply {
            outlinePaint.color = ROUTE_LINE_COLOR
            outlinePaint.strokeWidth = ROUTE_LINE_WIDTH_DP * mapView.context.resources.displayMetrics.density
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
    }

    LaunchedEffect(route, mapSource) {
        val exists = mapView.overlays.contains(polyline)
        if (route == null) {
            if (exists) {
                mapView.overlays.remove(polyline)
                mapView.invalidate()
            }
            return@LaunchedEffect
        }

        val displayPoints = route.points.map { point ->
            if (mapSource.isGcj02) {
                val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(point.latitude, point.longitude)
                GeoPoint(gcjLat, gcjLon)
            } else {
                GeoPoint(point.latitude, point.longitude)
            }
        }
        polyline.setPoints(displayPoints)
        if (!exists) {
            // Index 0 keeps the route beneath every marker and the location overlay.
            mapView.overlays.add(0, polyline)
        }
        fitCameraToRoute(mapView, displayPoints)
        mapView.invalidate()
    }
}

/**
 * Fits the camera to the route's bounding box without ever spinning the main thread.
 *
 * osmdroid's `zoomToBoundingBox` can busy-loop (100% CPU → ANR) when the [MapView] has no
 * layout yet — exactly what happens when the app is revived from the Super Island notification
 * while a walking session is live. We therefore only fit once the view reports real dimensions,
 * and even then guard it with [runCatching]; if the view is still unlaid-out we centre on the
 * route midpoint now and re-fit on the next frame via [android.view.View.post].
 */
private fun fitCameraToRoute(
    mapView: MapView,
    displayPoints: List<GeoPoint>,
) {
    if (displayPoints.isEmpty()) return
    val bbox = runCatching { org.osmdroid.util.BoundingBox.fromGeoPoints(displayPoints) }.getOrNull()
        ?: return
    val fitAction = {
        val fit = runCatching {
            mapView.zoomToBoundingBox(bbox, false, ROUTE_FIT_PADDING_PX)
        }
        if (fit.isFailure) {
            // Fall back to a manual centre at the previous zoom to keep the route visible.
            mapView.controller.setCenter(displayPoints[displayPoints.size / 2])
            mapView.invalidate()
        }
    }
    if (mapView.width > 0 && mapView.height > 0) {
        fitAction()
    } else {
        // Defer until the AndroidView has laid the map out; retry once next frame.
        mapView.controller.setCenter(bbox.centerWithDateLine)
        mapView.post { fitAction() }
    }
}

/**
 * Keeps the moving "simulated walker" marker in sync with the dynamic walking position.
 *
 * Same coordinate rule as [HandleWalkingRouteOverlay]: input is WGS-84, converted for display
 * on GCJ-02 tile sources. `null` removes the marker.
 *
 * @param mapView The map to draw on.
 * @param position The simulated walker's current WGS-84 position, or `null` to clear.
 * @param mapSource Current tile source option.
 */
@Composable
internal fun HandleWalkingPositionOverlay(
    mapView: MapView,
    position: GeoPoint?,
    mapSource: MapSourceOption,
) {
    val context = LocalContext.current
    val walkerMarker = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_mylocation)
        }
    }

    LaunchedEffect(position, mapSource) {
        val exists = mapView.overlays.contains(walkerMarker)
        if (position == null) {
            if (exists) {
                mapView.overlays.remove(walkerMarker)
                mapView.invalidate()
            }
            return@LaunchedEffect
        }

        val displayPoint = if (mapSource.isGcj02) {
            val (gcjLat, gcjLon) = CoordinateTransform.wgs84ToGcj02(position.latitude, position.longitude)
            GeoPoint(gcjLat, gcjLon)
        } else {
            position
        }
        walkerMarker.position = displayPoint
        if (!exists) mapView.overlays.add(walkerMarker)
        mapView.invalidate()
    }
}

/** Route line tint (iOS green) matching the active-spoof status dot. */
private const val ROUTE_LINE_COLOR = 0xFF34C759.toInt()
private const val ROUTE_LINE_WIDTH_DP = 6f
private const val ROUTE_FIT_PADDING_PX = 140
