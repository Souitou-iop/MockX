package com.noobexon.xposedfakelocation.manager.ui.map

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.components.BottomControlPanel
import com.noobexon.xposedfakelocation.manager.ui.components.BlurBackdropBox
import com.noobexon.xposedfakelocation.manager.ui.components.FloatingPanel
import com.noobexon.xposedfakelocation.manager.ui.components.PillActionButton
import com.noobexon.xposedfakelocation.manager.ui.components.StatusChip
import com.noobexon.xposedfakelocation.manager.ui.components.rememberBlurBackdrop
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import com.noobexon.xposedfakelocation.manager.ui.theme.StatusSuccess
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.MapAlbum
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Full-screen immersive map with HyperOS floating chrome: a top status island (drawer trigger,
 * live spoof status, layer switcher, center) and a bottom dashboard panel (primary action,
 * location info, favorites, quick clear). The modal drawer is a Material 3
 * [ModalNavigationDrawer], which handles dragging and predictive back natively.
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val colorScheme = MiuixTheme.colorScheme
    val backdrop = rememberBlurBackdrop()
    val blurEnabled = backdrop != null
    val fakeLocationSet = stringResource(R.string.toast_fake_location_set)
    val fakeLocationUnset = stringResource(R.string.toast_unset_fake_location)

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
        drawerState = drawerState,
        // The M3 drawer arms its drag detector on the whole screen; while closed that hijacks
        // every horizontal pan of the map. Enable the gestures only once the drawer is open —
        // drag-to-close keeps working, and the map pans freely while closed.
        gesturesEnabled = !drawerState.isClosed,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        drawerContent = {
            ModalDrawerSheet(
                // The drawerState overload owns back handling: it closes on back on every
                // Android version and animates the predictive-back preview on Android 14+.
                drawerState = drawerState,
                modifier = Modifier.width(300.dp),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = colorScheme.surface,
            ) {
                DrawerContent(
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                    onNavigate = { mapViewModel.requestReopenDrawer() },
                    navController = navController
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-screen immersive map canvas, captured as the blur backdrop for the panels
            BlurBackdropBox(backdrop = backdrop) {
                MapViewContainer(
                    isLoading = uiState.isLoading,
                    lastClickedLocation = uiState.lastClickedLocation,
                    userLocation = uiState.userLocation,
                    isPlaying = uiState.isPlaying,
                    mapZoom = uiState.mapZoom,
                    mapSource = uiState.mapSource,
                    tiandituToken = uiState.tiandituToken,
                    hasResolvedInitialLocation = uiState.hasResolvedInitialLocation,
                    goToPointEvent = mapViewModel.goToPointEvent,
                    centerMapEvent = mapViewModel.centerMapEvent,
                    onClickedLocationChange = mapViewModel::updateClickedLocation,
                    onUserLocationChange = mapViewModel::updateUserLocation,
                    onMapZoomChange = mapViewModel::updateMapZoom,
                    onLoadingFinished = mapViewModel::setLoadingFinished,
                    onInitialLocationResolved = mapViewModel::markInitialLocationResolved,
                )
            }

            // 2. HyperOS floating top status island
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                FloatingPanel(
                    backdrop = backdrop,
                    blurEnabled = blurEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Drawer trigger button
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Sidebar,
                            contentDescription = stringResource(R.string.cd_menu),
                        )
                    }

                    // Middle status & location summary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        // Live status indicator dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isPlaying -> StatusSuccess
                                        isFabClickable -> colorScheme.primary
                                        else -> colorScheme.onSurfaceVariantSummary
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = when {
                                    isPlaying -> stringResource(R.string.map_status_spoofing)
                                    isFabClickable -> stringResource(R.string.map_status_selected)
                                    else -> stringResource(R.string.app_name)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (clickedLocation != null) {
                                Text(
                                    text = String.format(Locale.US, "%.4f, %.4f", clickedLocation.latitude, clickedLocation.longitude),
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Action icons: center, switch source, more
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { mapViewModel.triggerCenterMapEvent() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Location,
                                contentDescription = stringResource(R.string.cd_center),
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { mapViewModel.showMapSourceDialog() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MapAlbum,
                                contentDescription = stringResource(R.string.map_switch_source),
                                tint = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        val menuItems = remember(isFabClickable, colorScheme) {
                            listOf(
                                DropdownItem(
                                    text = context.getString(R.string.map_go_to_point),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = MiuixIcons.Location,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = modifier.size(20.dp)
                                        )
                                    },
                                    onClick = { mapViewModel.showGoToPointDialog() }
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.map_add_to_favorites),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = MiuixIcons.Favorites,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = modifier.size(20.dp)
                                        )
                                    },
                                    enabled = isFabClickable,
                                    onClick = { mapViewModel.showAddToFavoritesDialog() }
                                ),
                                DropdownItem(
                                    text = context.getString(R.string.map_clear_location),
                                    icon = { modifier ->
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = null,
                                            tint = colorScheme.error,
                                            modifier = modifier.size(20.dp)
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
                                tint = colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 3. HyperOS bottom floating dashboard panel
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                BottomControlPanel(
                    backdrop = backdrop,
                    blurEnabled = blurEnabled
                ) {
                    if (clickedLocation != null) {
                        // Location info & actions row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.map_spoof_target),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.US, "%.6f°, %.6f°", clickedLocation.latitude, clickedLocation.longitude),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusChip(
                                    text = stringResource(R.string.map_chip_favorite),
                                    isActive = true,
                                    modifier = Modifier.clickable { mapViewModel.showAddToFavoritesDialog() }
                                )
                                StatusChip(
                                    text = stringResource(R.string.map_chip_clear),
                                    isActive = false,
                                    modifier = Modifier.clickable { mapViewModel.updateClickedLocation(null) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        // Helpful prompt when no pin is placed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Location,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.map_hint_place_pin),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Main action pill button
                    PillActionButton(
                        text = when {
                            isPlaying -> stringResource(R.string.map_stop_spoof)
                            isFabClickable -> stringResource(R.string.map_start_spoof)
                            else -> stringResource(R.string.map_select_location_first)
                        },
                        icon = if (isPlaying) MiuixIcons.Close else MiuixIcons.Play,
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
                        enabled = isFabClickable || isPlaying,
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
    }
}
