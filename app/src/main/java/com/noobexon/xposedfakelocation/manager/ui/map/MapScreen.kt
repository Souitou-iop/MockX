package com.noobexon.xposedfakelocation.manager.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.route.WalkingErrorCode
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import com.noobexon.xposedfakelocation.manager.route.WalkingRoute
import com.noobexon.xposedfakelocation.manager.route.WalkingSpeedPreset
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixBottomPanel
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixChip
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixDialog
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixDialogButton
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixFloatingIsland
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixPillButton
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * HyperOS / Miuix Full-Screen Immersive Map Screen.
 *
 * Replaces the legacy Material 3 top-bar and isolated FAB with:
 * - Full-screen edge-to-edge interactive map canvas.
 * - Top floating capsule status island (Drawer trigger, live spoof status & coordinates, layer switcher, center map).
 * - Bottom floating dashboard control sheet (Direct pill action button, location info, favorites, quick clear).
 */
@Composable
fun MapScreen(
    navController: NavController,
    mapViewModel: MapViewModel
) {
    val context = LocalContext.current
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying = uiState.isPlaying
    val isFabClickable = uiState.isFabClickable
    val clickedLocation = uiState.lastClickedLocation
    val showGoToPointDialog = uiState.isGoToPointDialogVisible
    val showAddToFavoritesDialog = uiState.isAddToFavoritesDialogVisible
    val showMapSourceDialog = uiState.isMapSourceDialogVisible
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val fakeLocationSet = stringResource(R.string.toast_fake_location_set)
    val fakeLocationUnset = stringResource(R.string.toast_unset_fake_location)

    // ---- Walking simulation ----
    val walkingPhase = uiState.walkingPhase
    val isWalkingActive = uiState.isWalkingActive

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> mapViewModel.startWalking() }

    fun beginWalkingWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            mapViewModel.startWalking()
        }
    }

    LaunchedEffect(mapViewModel.walkingEvents) {
        mapViewModel.walkingEvents.collect { event ->
            when (event) {
                is WalkingEvent.WalkFailed ->
                    Toast.makeText(context, context.getString(walkingErrorRes(event.errorCode)), Toast.LENGTH_LONG).show()
                is WalkingEvent.RouteReady ->
                    Toast.makeText(context, context.getString(R.string.walk_route_ready_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(Unit) {
        if (mapViewModel.consumeReopenDrawerRequest()) {
            drawerState.open()
        }
    }

    LaunchedEffect(mapViewModel.navigateToFavoritesEvent) {
        mapViewModel.navigateToFavoritesEvent.collect {
            navController.navigate(Screen.Favorites.route) { launchSingleTop = true }
        }
    }

    ModalNavigationDrawer(
        drawerContent = {
            DrawerContent(
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onNavigate = { mapViewModel.requestReopenDrawer() },
                navController = navController
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.45f),
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-screen Immersive Map Canvas
            MapViewContainer(
                isLoading = uiState.isLoading,
                lastClickedLocation = uiState.lastClickedLocation,
                userLocation = uiState.userLocation,
                mapZoom = uiState.mapZoom,
                mapSource = uiState.mapSource,
                tiandituToken = uiState.tiandituToken,
                hasResolvedInitialLocation = uiState.hasResolvedInitialLocation,
                walkingRoute = uiState.walkingRoute,
                walkingCurrentPosition = uiState.walkingCurrentPosition,
                goToPointEvent = mapViewModel.goToPointEvent,
                centerMapEvent = mapViewModel.centerMapEvent,
                onClickedLocationChange = mapViewModel::updateClickedLocation,
                onUserLocationChange = mapViewModel::updateUserLocation,
                onMapZoomChange = mapViewModel::updateMapZoom,
                onLoadingFinished = mapViewModel::setLoadingFinished,
                onInitialLocationResolved = mapViewModel::markInitialLocationResolved,
                isMapInteractionEnabled = !uiState.isMapInteractionLocked,
            )

            // 2. HyperOS Floating Top Status Island
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                MiuixFloatingIsland(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Drawer Hamburger Button
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = stringResource(R.string.cd_menu),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Middle Status & Location summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        // Live Status indicator dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        walkingPhase == WalkingPhase.WALKING -> Color(0xFF34C759)
                                        walkingPhase == WalkingPhase.PAUSED -> Color(0xFFFF9500)
                                        isPlaying -> Color(0xFF34C759)
                                        isFabClickable -> MaterialTheme.colorScheme.primary
                                        else -> Color(0xFF8E8E93)
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = walkingStatusText(walkingPhase, isPlaying, isFabClickable),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (clickedLocation != null) {
                                Text(
                                    text = String.format(Locale.US, "%.4f, %.4f", clickedLocation.latitude, clickedLocation.longitude),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Action Icons: Center, Switch Source, More
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { mapViewModel.triggerCenterMapEvent() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = stringResource(R.string.cd_center),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { mapViewModel.showMapSourceDialog() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = stringResource(R.string.map_switch_source),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        val menuItems = remember(isFabClickable) {
                            listOf(
                                DropdownItem(
                                    text = context.getString(R.string.map_go_to_point),
                                    icon = {
                                        Icon(
                                            imageVector = MiuixIcons.Location,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = { mapViewModel.showGoToPointDialog() }
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.map_add_to_favorites),
                                    icon = {
                                        Icon(
                                            imageVector = MiuixIcons.Favorites,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    enabled = isFabClickable,
                                    onClick = { mapViewModel.showAddToFavoritesDialog() }
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.map_clear_location),
                                    icon = {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    enabled = isFabClickable,
                                    onClick = { mapViewModel.updateClickedLocation(null) }
                                )
                            )
                        }

                        WindowIconDropdownMenu(
                            entry = DropdownEntry(items = menuItems),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.cd_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 3. HyperOS Bottom Floating Dashboard Control Sheet
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                MiuixBottomPanel {
                    if (clickedLocation != null) {
                        // Location Info & Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "伪装目标位置",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.US, "%.6f°, %.6f°", clickedLocation.latitude, clickedLocation.longitude),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiuixChip(
                                    text = "收藏",
                                    isActive = true,
                                    modifier = Modifier.clickable { mapViewModel.showAddToFavoritesDialog() }
                                )
                                MiuixChip(
                                    text = "步行",
                                    isActive = true,
                                    modifier = Modifier.clickable {
                                        if (!isWalkingActive && walkingPhase != WalkingPhase.PLANNING) {
                                            mapViewModel.planWalkingRoute()
                                        }
                                    }
                                )
                                MiuixChip(
                                    text = "清除",
                                    isActive = false,
                                    modifier = Modifier.clickable { mapViewModel.updateClickedLocation(null) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        // Helpful Prompt when no pin is placed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationSearching,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "轻触地图任意位置放置图钉以选定虚拟位置",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Walking simulation control card (hidden while idle without a route)
                    if (walkingPhase != WalkingPhase.IDLE) {
                        WalkingControlCard(
                            uiState = uiState,
                            onPlanRoute = mapViewModel::planWalkingRoute,
                            onStart = ::beginWalkingWithPermissionCheck,
                            onPause = mapViewModel::pauseWalking,
                            onResume = mapViewModel::resumeWalking,
                            onStop = mapViewModel::stopWalking,
                            onSpeedChange = mapViewModel::setWalkingSpeedPreset,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Main Action Pill Button
                    MiuixPillButton(
                        text = when {
                            isWalkingActive -> stringResource(R.string.walk_active_pill)
                            walkingPhase == WalkingPhase.PLANNING -> stringResource(R.string.walk_planning_pill)
                            isPlaying -> "停止虚拟定位"
                            isFabClickable -> "开启虚拟定位"
                            else -> "请先选定地图位置"
                        },
                        icon = when {
                            walkingPhase == WalkingPhase.PLANNING -> Icons.Default.DirectionsWalk
                            isPlaying -> Icons.Default.Stop
                            else -> Icons.Default.PlayArrow
                        },
                        onClick = {
                            if (isFabClickable || isPlaying) {
                                val wasPlaying = uiState.isPlaying
                                mapViewModel.togglePlaying()
                                Toast.makeText(
                                    context,
                                    if (!wasPlaying) fakeLocationSet else fakeLocationUnset,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        isPrimary = !isPlaying,
                        isDestructive = isPlaying,
                        enabled = !isWalkingActive && walkingPhase != WalkingPhase.PLANNING && (isFabClickable || isPlaying),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Dialogs
        if (showGoToPointDialog) {
            val goToPoint = uiState.goToPointState
            GoToPointDialog(
                latitude = goToPoint.latitude.value,
                longitude = goToPoint.longitude.value,
                latitudeErrorRes = goToPoint.latitude.errorMessageRes,
                longitudeErrorRes = goToPoint.longitude.errorMessageRes,
                onLatitudeChange = mapViewModel::onGoToPointLatitudeChange,
                onLongitudeChange = mapViewModel::onGoToPointLongitudeChange,
                onConfirm = mapViewModel::confirmGoToPoint,
                onDismissRequest = mapViewModel::hideGoToPointDialog,
            )
        }

        if (showAddToFavoritesDialog) {
            val favorite = uiState.addToFavoritesState
            AddToFavoritesDialog(
                name = favorite.name.value,
                description = favorite.description.value,
                latitude = favorite.latitude.value,
                longitude = favorite.longitude.value,
                nameErrorRes = favorite.name.errorMessageRes,
                latitudeErrorRes = favorite.latitude.errorMessageRes,
                longitudeErrorRes = favorite.longitude.errorMessageRes,
                onNameChange = mapViewModel::onFavoriteNameChange,
                onDescriptionChange = mapViewModel::onFavoriteDescriptionChange,
                onLatitudeChange = mapViewModel::onFavoriteLatitudeChange,
                onLongitudeChange = mapViewModel::onFavoriteLongitudeChange,
                onConfirm = mapViewModel::confirmAddFavorite,
                onDismissRequest = mapViewModel::hideAddToFavoritesDialog,
            )
        }

        if (showMapSourceDialog) {
            MapSourceSelectionDialog(
                selectedSource = uiState.mapSource,
                onSourceSelected = { option ->
                    mapViewModel.setMapSource(option)
                    mapViewModel.hideMapSourceDialog()
                },
                onDismiss = mapViewModel::hideMapSourceDialog
            )
        }

        if (uiState.isReplaceSessionDialogVisible) {
            MiuixDialog(
                onDismissRequest = mapViewModel::dismissReplaceSessionDialog,
                title = stringResource(R.string.walk_replace_title),
                confirmButton = {
                    MiuixDialogButton(
                        text = stringResource(R.string.walk_replace_confirm),
                        isPrimary = true,
                        onClick = mapViewModel::confirmStartWalking
                    )
                },
                dismissButton = {
                    MiuixDialogButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = mapViewModel::dismissReplaceSessionDialog
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.walk_replace_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Status-island title for the current walking phase, falling back to the fixed-spoof labels.
 */
@Composable
private fun walkingStatusText(walkingPhase: WalkingPhase, isPlaying: Boolean, isFabClickable: Boolean): String = when (walkingPhase) {
    WalkingPhase.PLANNING -> stringResource(R.string.walk_status_planning)
    WalkingPhase.READY -> stringResource(R.string.walk_status_ready)
    WalkingPhase.WALKING -> stringResource(R.string.walk_status_walking)
    WalkingPhase.PAUSED -> stringResource(R.string.walk_status_paused)
    WalkingPhase.ARRIVED -> stringResource(R.string.walk_status_arrived)
    WalkingPhase.STOPPING -> stringResource(R.string.walk_status_stopping)
    WalkingPhase.FAILED -> stringResource(R.string.walk_status_failed)
    else -> if (isPlaying) "伪装运行中" else if (isFabClickable) "位置已选定" else stringResource(R.string.app_name)
}

/** Localized, user-safe message for a walking failure code. */
@Composable
private fun walkingErrorText(errorCode: WalkingErrorCode): String =
    stringResource(walkingErrorRes(errorCode))

/** Maps a [WalkingErrorCode] to its message resource; usable from non-composable contexts. */
private fun walkingErrorRes(errorCode: WalkingErrorCode): Int = when (errorCode) {
    WalkingErrorCode.MISSING_API_KEY -> R.string.walk_error_missing_api_key
    WalkingErrorCode.LOCATION_UNAVAILABLE -> R.string.walk_error_location_unavailable
    WalkingErrorCode.INVALID_DESTINATION -> R.string.walk_error_invalid_destination
    WalkingErrorCode.NETWORK_TIMEOUT -> R.string.walk_error_network_timeout
    WalkingErrorCode.NETWORK_UNAVAILABLE -> R.string.walk_error_network_unavailable
    WalkingErrorCode.API_AUTH_FAILED -> R.string.walk_error_api_auth_failed
    WalkingErrorCode.API_QUOTA_EXCEEDED -> R.string.walk_error_api_quota_exceeded
    WalkingErrorCode.NO_ROUTE -> R.string.walk_error_no_route
    WalkingErrorCode.INVALID_ROUTE_DATA -> R.string.walk_error_invalid_route_data
    WalkingErrorCode.PREFERENCE_UNAVAILABLE -> R.string.walk_error_preference_unavailable
    WalkingErrorCode.SERVICE_START_FAILED -> R.string.walk_error_service_start_failed
    WalkingErrorCode.LOCATION_STATE_STALE -> R.string.walk_error_location_state_stale
}

/**
 * Compact control card for the walking simulation, rendered above the main pill button.
 *
 * Renders according to [MapUiState.walkingPhase]:
 *  - PLANNING: an indeterminate "planning" row.
 *  - READY: route summary (total distance + estimated time), speed preset chips and the
 *    start action.
 *  - WALKING / PAUSED / ARRIVED: live progress bar, remaining controls (pause/resume/stop).
 *  - FAILED: the sanitized error message plus a "re-plan" action; the chosen destination is
 *    kept so planning can simply be retried.
 */
@Composable
private fun WalkingControlCard(
    uiState: MapUiState,
    onPlanRoute: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (WalkingSpeedPreset) -> Unit,
) {
    val phase = uiState.walkingPhase
    val route = uiState.walkingRoute

    Column(modifier = Modifier.fillMaxWidth()) {
        // Status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = walkingStatusText(phase, isPlaying = false, isFabClickable = true),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (route != null && phase != WalkingPhase.PLANNING) {
                Text(
                    text = walkingRouteSummary(route, uiState.walkingSpeedPreset),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (phase) {
            WalkingPhase.PLANNING -> {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            WalkingPhase.READY -> {
                Spacer(modifier = Modifier.height(10.dp))
                WalkingSpeedChipRow(uiState.walkingSpeedPreset, onSpeedChange)
                Spacer(modifier = Modifier.height(10.dp))
                MiuixPillButton(
                    text = stringResource(R.string.walk_start),
                    icon = Icons.Default.PlayArrow,
                    onClick = onStart,
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            WalkingPhase.WALKING, WalkingPhase.PAUSED, WalkingPhase.ARRIVED -> {
                val total = route?.totalDistanceMeters ?: 0.0
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (total > 0) (uiState.walkingDistanceTravelled / total).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.walk_progress_format,
                        formatWalkingDistance(uiState.walkingDistanceTravelled),
                        formatWalkingDistance(total),
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (phase != WalkingPhase.ARRIVED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    WalkingSpeedChipRow(uiState.walkingSpeedPreset, onSpeedChange)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (phase == WalkingPhase.PAUSED) {
                        MiuixPillButton(
                            text = stringResource(R.string.walk_resume),
                            icon = Icons.Default.PlayArrow,
                            onClick = onResume,
                            isPrimary = true,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (phase == WalkingPhase.WALKING) {
                        MiuixPillButton(
                            text = stringResource(R.string.walk_pause),
                            icon = Icons.Default.Pause,
                            onClick = onPause,
                            isPrimary = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    MiuixPillButton(
                        text = stringResource(R.string.walk_stop),
                        icon = Icons.Default.Stop,
                        onClick = onStop,
                        isDestructive = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            WalkingPhase.FAILED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.walkingErrorCode?.let { walkingErrorText(it) } ?: stringResource(R.string.walk_status_failed),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                MiuixPillButton(
                    text = stringResource(R.string.walk_replan),
                    icon = Icons.Default.Refresh,
                    onClick = onPlanRoute,
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            WalkingPhase.IDLE, WalkingPhase.STOPPING -> Unit
        }
    }
}

/** The three walking-pace presets as selectable chips. */
@Composable
private fun WalkingSpeedChipRow(
    selected: WalkingSpeedPreset,
    onSpeedChange: (WalkingSpeedPreset) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WalkingSpeedPreset.entries.forEach { preset ->
            MiuixChip(
                text = stringResource(preset.labelRes),
                isActive = preset == selected,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSpeedChange(preset) }
            )
        }
    }
}

/** "1.24 km · 约 15 分钟" style summary; the ETA prefers the API-provided duration. */
@Composable
private fun walkingRouteSummary(route: WalkingRoute, preset: WalkingSpeedPreset): String {
    val durationSeconds = route.expectedDurationSeconds
        ?: route.totalDistanceMeters.div(preset.metersPerSecond).toInt()
    val minutes = (durationSeconds / 60).coerceAtLeast(1)
    return "${formatWalkingDistance(route.totalDistanceMeters)} · ${stringResource(R.string.walk_eta_format, minutes)}"
}

/** Human-readable distance: metres below 1 km, kilometres above. */
private fun formatWalkingDistance(meters: Double): String = when {
    meters >= 1000.0 -> String.format(Locale.US, "%.2f km", meters / 1000.0)
    else -> String.format(Locale.US, "%.0f m", meters)
}
