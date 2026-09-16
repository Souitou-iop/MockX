package com.noobexon.xposedfakelocation.manager.ui.targetapps

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
import com.noobexon.xposedfakelocation.manager.ui.components.BlurredBar
import com.noobexon.xposedfakelocation.manager.ui.components.BlurBackdropBox
import com.noobexon.xposedfakelocation.manager.ui.components.StatusChip
import com.noobexon.xposedfakelocation.manager.ui.components.LoadingIndicator
import com.noobexon.xposedfakelocation.manager.ui.components.pageScrollModifiers
import com.noobexon.xposedfakelocation.manager.ui.components.rememberBlurBackdrop
import com.noobexon.xposedfakelocation.manager.ui.components.SearchField
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Target app picker with large title, instant search, filter menu and pull-to-refresh —
 * rendered entirely with miuix components.
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
    val colorScheme = MiuixTheme.colorScheme
    val context = LocalContext.current

    val filterMenuItems = remember(uiState.showUserApps, uiState.showSystemApps, context) {
        listOf(
            DropdownItem(
                text = context.getString(R.string.target_apps_filter_user_only),
                selected = uiState.showUserApps,
                onClick = { onSetShowUserApps(!uiState.showUserApps) }
            ),
            DropdownItem(
                text = context.getString(R.string.target_apps_include_system),
                selected = uiState.showSystemApps,
                onClick = { onSetShowSystemApps(!uiState.showSystemApps) }
            )
        )
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.imePadding()
            )
        },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.screen_target_apps),
                    subtitle = stringResource(R.string.screen_target_apps_subtitle),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.cd_navigate_back)
                            )
                        }
                    },
                    actions = {
                        WindowIconDropdownMenu(
                            entry = DropdownEntry(items = filterMenuItems),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Filter,
                                contentDescription = stringResource(R.string.cd_filter_apps),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(top = top, start = 0.dp, end = 0.dp, bottom = bottom + 16.dp)
        }
        val pullToRefreshState = rememberPullToRefreshState()
        val refreshTexts = listOf(
            stringResource(R.string.pull_to_refresh_pulling),
            stringResource(R.string.pull_to_refresh_release),
            stringResource(R.string.pull_to_refresh_refreshing),
            stringResource(R.string.pull_to_refresh_refreshed)
        )
        BlurBackdropBox(backdrop) {
            PullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                pullToRefreshState = pullToRefreshState,
                contentPadding = contentPadding,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                refreshTexts = refreshTexts,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pageScrollModifiers(
                            enableScrollEndHaptic = true,
                            showTopAppBar = true,
                            topAppBarScrollBehavior = topAppBarScrollBehavior
                        ),
                    contentPadding = contentPadding
                ) {
                    item(key = "search") {
                        SearchField(
                            query = uiState.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            label = stringResource(R.string.target_apps_search_label)
                        )
                    }
                    item(key = "status") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(
                                text = stringResource(R.string.target_apps_enabled_count, uiState.selectedPackages.size),
                                isActive = uiState.selectedPackages.isNotEmpty()
                            )

                            if (!uiState.isModuleActive) {
                                Text(
                                    text = stringResource(R.string.target_apps_module_inactive),
                                    fontSize = 12.sp,
                                    color = colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (uiState.isLoading) {
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator()
                            }
                        }
                    } else if (uiState.filteredApps.isEmpty()) {
                        item(key = "empty") {
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (uiState.searchQuery.isNotBlank()) {
                                            stringResource(R.string.target_apps_no_results, uiState.searchQuery)
                                        } else {
                                            stringResource(R.string.target_apps_filter_no_results)
                                        },
                                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurfaceVariantActions
                                    )
                                }
                            }
                        }
                    } else {
                        // Rows are independent lazy items, but the section background is painted
                        // continuously so the whole list reads as one card (hle whitelist style).
                        itemsIndexed(
                            items = uiState.filteredApps,
                            key = { _, app -> app.packageName },
                            contentType = { _, _ -> "target_app" }
                        ) { index, app ->
                            val listSize = uiState.filteredApps.size
                            val rowShape = when {
                                listSize == 1 -> RoundedCornerShape(16.dp)
                                index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                index == listSize - 1 -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = if (index == listSize - 1) 12.dp else 0.dp)
                                    .fillMaxWidth()
                                    .background(color = colorScheme.surfaceContainer, shape = rowShape)
                                    .then(
                                        if (app.isSelected) {
                                            Modifier.background(
                                                color = colorScheme.primary.copy(alpha = 0.08f),
                                                shape = rowShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                TargetAppRow(
                                    app = app,
                                    onToggle = { onToggle(app.packageName) },
                                    onRelaunch = { onRelaunch(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Single app row inside the shared card surface; the card background and corner radii are
 * painted by the item container, not here.
 */
@Composable
private fun TargetAppRow(
    app: TargetAppItem,
    onToggle: () -> Unit,
    onRelaunch: () -> Unit
) {
    val colorScheme = MiuixTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !app.isPending, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                fontSize = 15.sp,
                fontWeight = if (app.isSelected) FontWeight.Bold else FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = app.packageName,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (app.isSelected) {
            if (app.isRelaunching) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(size = 22.dp, strokeWidth = 3.dp)
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    contentColor = colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(onClick = onRelaunch) {
                        Icon(
                            MiuixIcons.Reset,
                            contentDescription = stringResource(R.string.target_apps_relaunch_cd, app.label),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        if (app.isPending) {
            Box(
                modifier = Modifier.size(26.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(size = 22.dp, strokeWidth = 3.dp)
            }
        } else {
            Checkbox(
                state = androidx.compose.ui.state.ToggleableState(app.isSelected),
                onClick = { onToggle() },
            )
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
            color = MiuixTheme.colorScheme.surfaceVariant,
            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
