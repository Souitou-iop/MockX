package com.noobexon.xposedfakelocation.manager.ui.map

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixBottomPanel
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixChip
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
                                    if (isPlaying) Color(0xFF34C759)
                                    else if (isFabClickable) MaterialTheme.colorScheme.primary
                                    else Color(0xFF8E8E93)
                                )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = if (isPlaying) "伪装运行中" else if (isFabClickable) "位置已选定" else stringResource(R.string.app_name),
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

                    // Main Action Pill Button
                    MiuixPillButton(
                        text = if (isPlaying) "停止虚拟定位" else if (isFabClickable) "开启虚拟定位" else "请先选定地图位置",
                        icon = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
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
