package com.noobexon.xposedfakelocation.manager.ui.targetapps

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.RefreshRateHelper
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixCard
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixCardDivider
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixChip
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixLargeTitleHeader
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixRoundCheckbox
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixSearchBox
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HyperOS / Miuix Redesigned Target Apps Screen.
 *
 * Implements:
 * - HyperOS Large Title Header ("目标应用" / "Target Apps").
 * - Instant Pill Search Bar and Filter Chip Bar (selected count, user apps vs system apps).
 * - Grouped Squircle App Cards with smooth 18.dp rounded corners.
 * - MIUI iconic round blue ripple checkboxes and pill restart buttons.
 */
@Composable
fun TargetAppsScreen(
    navController: NavController,
    viewModel: TargetAppsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) {
        RefreshRateHelper.applyHighRefreshRate(context)
        RefreshRateHelper.applyFrameRateToView(view)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                val message = when (event) {
                    is TargetAppsEvent.ModuleNotActive ->
                        context.getString(R.string.target_apps_module_inactive)
                    is TargetAppsEvent.ScopeRequestFailed ->
                        context.getString(R.string.target_apps_scope_request_failed, event.message)
                    is TargetAppsEvent.Relaunched ->
                        context.getString(R.string.target_apps_relaunching, event.appLabel)
                    is TargetAppsEvent.RelaunchFailed ->
                        context.getString(R.string.target_apps_relaunch_failed, event.appLabel)
                    is TargetAppsEvent.RootRequired ->
                        context.getString(R.string.target_apps_root_required)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    TargetAppsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = { navController.navigateUp() },
        onSearchQueryChange = viewModel::updateSearchQuery,
        onToggle = viewModel::toggleApp,
        onRelaunch = viewModel::relaunchApp,
        onRefresh = viewModel::refresh,
        onSetShowUserApps = viewModel::setShowUserApps,
        onSetShowSystemApps = viewModel::setShowSystemApps,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetAppsContent(
    uiState: TargetAppsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onRelaunch: (String) -> Unit,
    onRefresh: () -> Unit,
    onSetShowUserApps: (Boolean) -> Unit,
    onSetShowSystemApps: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()

    val filterMenuItems = remember(uiState.showUserApps, uiState.showSystemApps) {
        listOf(
            DropdownItem(
                text = "仅显示用户应用",
                selected = uiState.showUserApps,
                onClick = { onSetShowUserApps(!uiState.showUserApps) }
            ),
            DropdownItem(
                text = "包含系统应用",
                selected = uiState.showSystemApps,
                onClick = { onSetShowSystemApps(!uiState.showSystemApps) }
            )
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.imePadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // HyperOS Large Title Header
            MiuixLargeTitleHeader(
                title = stringResource(R.string.screen_target_apps),
                subtitle = "仅针对选中的应用启用底层定位与基站拦截",
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    WindowIconDropdownMenu(
                        entry = DropdownEntry(items = filterMenuItems),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.cd_filter_apps),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // Capsule Search Box
            MiuixSearchBox(
                query = uiState.searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = stringResource(R.string.target_apps_search_label),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            // Status & Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiuixChip(
                    text = "已启用 ${uiState.selectedPackages.size} 个应用",
                    isActive = uiState.selectedPackages.isNotEmpty()
                )

                if (!uiState.isModuleActive) {
                    Text(
                        text = stringResource(R.string.target_apps_module_inactive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main List or Empty State
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.filteredApps.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.target_apps_no_results, uiState.searchQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.filteredApps.isEmpty() && (!uiState.showUserApps || !uiState.showSystemApps) -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.target_apps_filter_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(uiState.filteredApps, key = { it.packageName }) { app ->
                                TargetAppCard(
                                    app = app,
                                    onToggle = { onToggle(app.packageName) },
                                    onRelaunch = { onRelaunch(app.packageName) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * HyperOS Grouped App Card with continuous smooth squircle corners.
 */
@Composable
private fun TargetAppCard(
    app: TargetAppItem,
    onToggle: () -> Unit,
    onRelaunch: () -> Unit
) {
    val cardBg = if (app.isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !app.isPending, onClick = onToggle),
        containerColor = cardBg,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = app.packageName,
                label = app.label
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = if (app.isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (app.isSelected) {
                if (app.isRelaunching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        IconButton(onClick = onRelaunch) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = stringResource(R.string.target_apps_relaunch_cd, app.label),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            if (app.isPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                MiuixRoundCheckbox(
                    checked = app.isSelected,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    label: String
) {
    val context = LocalContext.current
    val iconBitmap by produceState<Bitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap()
            }.getOrNull()
        }
    }

    val bitmap = iconBitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_app_icon, label),
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
