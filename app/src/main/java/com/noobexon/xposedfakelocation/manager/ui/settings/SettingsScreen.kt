package com.noobexon.xposedfakelocation.manager.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.RefreshRateHelper
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.notification.IslandStyleOption
import com.noobexon.xposedfakelocation.manager.ui.components.AppDialog
import com.noobexon.xposedfakelocation.manager.ui.components.BlurredBar
import com.noobexon.xposedfakelocation.manager.ui.components.BlurBackdropBox
import com.noobexon.xposedfakelocation.manager.ui.components.SearchField
import com.noobexon.xposedfakelocation.manager.ui.components.TextInputDialog
import com.noobexon.xposedfakelocation.manager.ui.components.pageScrollModifiers
import com.noobexon.xposedfakelocation.manager.ui.components.rememberBlurBackdrop
import com.noobexon.xposedfakelocation.manager.ui.map.MapSourceOption
import com.noobexon.xposedfakelocation.manager.ui.map.MapSourceSelectionDialog
import com.noobexon.xposedfakelocation.manager.ui.theme.MonetColor
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) {
        RefreshRateHelper.applyHighRefreshRate(context)
        RefreshRateHelper.applyFrameRateToView(view)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var restartDialogEnabled by remember { mutableStateOf<Boolean?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, settingsViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            settingsViewModel.systemHooksEvents.collect { event ->
                when (event) {
                    is SystemHooksEvent.RestartRequired -> restartDialogEnabled = event.enabled
                    is SystemHooksEvent.ModuleNotActive ->
                        snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                    is SystemHooksEvent.ScopeRequestFailed ->
                        snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_scope_failed, event.message))
                }
            }
        }
    }

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val selectedLanguage = LanguageOption.fromTag(uiState.languageTag)

    val categories: List<Pair<SettingsCategory, List<SettingEntry>>> = listOf(
        SettingsCategory.LOCATION to listOf(
            SettingEntry.Numeric(NumericSetting.RANDOMIZE_RADIUS, uiState.useRandomize, settingsViewModel::setUseRandomize, uiState.randomizeRadius) { settingsViewModel.setRandomizeRadius(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.HORIZONTAL_ACCURACY, uiState.useAccuracy, settingsViewModel::setUseAccuracy, uiState.accuracy) { settingsViewModel.setAccuracy(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.VERTICAL_ACCURACY, uiState.useVerticalAccuracy, settingsViewModel::setUseVerticalAccuracy, uiState.verticalAccuracy, settingsViewModel::setVerticalAccuracy)
        ),
        SettingsCategory.MAP to buildList {
            add(SettingEntry.MapSource(uiState.mapSource, settingsViewModel::setMapSource))
            if (uiState.mapSource == MapSourceOption.TIANDITU_VECTOR) {
                add(
                    SettingEntry.Text(
                        SettingKeys.TIANDITU_TOKEN,
                        R.string.setting_tianditu_token_title,
                        R.string.setting_tianditu_token_description,
                        R.string.setting_tianditu_token_label,
                        uiState.tiandituToken,
                        onValueChange = settingsViewModel::setTianDiTuToken
                    )
                )
            }
            add(
                SettingEntry.SecretText(
                    SettingKeys.AMAP_KEY,
                    R.string.setting_amap_key_title,
                    R.string.setting_amap_key_description,
                    R.string.setting_amap_key_label,
                    isConfigured = uiState.amapKeyConfigured,
                    onValueChange = settingsViewModel::setAmapWebServiceKey,
                    onClear = settingsViewModel::clearAmapWebServiceKey,
                )
            )
        },
        SettingsCategory.ALTITUDE to listOf(
            SettingEntry.Numeric(NumericSetting.ALTITUDE, uiState.useAltitude, settingsViewModel::setUseAltitude, uiState.altitude) { settingsViewModel.setAltitude(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.MEAN_SEA_LEVEL, uiState.useMeanSeaLevel, settingsViewModel::setUseMeanSeaLevel, uiState.meanSeaLevel) { settingsViewModel.setMeanSeaLevel(it.toDouble()) },
            SettingEntry.Numeric(NumericSetting.MEAN_SEA_LEVEL_ACCURACY, uiState.useMeanSeaLevelAccuracy, settingsViewModel::setUseMeanSeaLevelAccuracy, uiState.meanSeaLevelAccuracy, settingsViewModel::setMeanSeaLevelAccuracy)
        ),
        SettingsCategory.MOVEMENT to listOf(
            SettingEntry.Numeric(NumericSetting.SPEED, uiState.useSpeed, settingsViewModel::setUseSpeed, uiState.speed, settingsViewModel::setSpeed),
            SettingEntry.Numeric(NumericSetting.SPEED_ACCURACY, uiState.useSpeedAccuracy, settingsViewModel::setUseSpeedAccuracy, uiState.speedAccuracy, settingsViewModel::setSpeedAccuracy)
        ),
        SettingsCategory.NOTIFICATIONS to listOf(
            SettingEntry.IslandStyle(uiState.islandStyle, settingsViewModel::setIslandStyle),
            SettingEntry.Switch(SettingKeys.HIDE_TOAST, R.string.setting_hide_toast_title, R.string.setting_hide_toast_description, uiState.hideFakeLocationToast, settingsViewModel::setHideFakeLocationToast)
        ),
        SettingsCategory.SYSTEM_HOOKS to listOf(
            SettingEntry.Switch(SettingKeys.SYSTEM_HOOKS, R.string.setting_system_hooks_title, R.string.setting_system_hooks_description, uiState.systemHooksEnabled, settingsViewModel::setEnableSystemHooks)
        ),
        SettingsCategory.WIFI_IDENTITY to buildList {
            add(SettingEntry.Switch(SettingKeys.WIFI_IDENTITY, R.string.setting_wifi_identity_title, R.string.setting_wifi_identity_description, uiState.wifiIdentityEnabled, settingsViewModel::setEnableWifiIdentity))
            if (uiState.wifiIdentityEnabled) {
                add(SettingEntry.Text(SettingKeys.WIFI_SSID, R.string.setting_wifi_ssid_title, R.string.setting_wifi_ssid_description, R.string.setting_wifi_ssid_label, uiState.wifiSsid, onValueChange = settingsViewModel::setWifiSsid))
                add(SettingEntry.Text(SettingKeys.WIFI_BSSID, R.string.setting_wifi_bssid_title, R.string.setting_wifi_bssid_description, R.string.setting_wifi_bssid_label, uiState.wifiBssid, onValueChange = settingsViewModel::setWifiBssid))
                add(SettingEntry.Text(SettingKeys.WIFI_RSSI, R.string.setting_wifi_rssi_title, R.string.setting_wifi_rssi_description, R.string.setting_wifi_rssi_label, uiState.wifiRssi.toString(), TextInputKind.SIGNED_NUMBER, settingsViewModel::setWifiRssi))
            }
        },
        SettingsCategory.EXTERNAL_CONTROL to listOf(
            SettingEntry.Switch(SettingKeys.BROADCAST, R.string.setting_external_broadcast_title, R.string.setting_external_broadcast_description, uiState.enableBroadcastControl, settingsViewModel::setEnableBroadcastControl)
        ),
        SettingsCategory.APPEARANCE to buildList {
            add(SettingEntry.Theme(uiState.themeModeId, settingsViewModel::setThemeMode))
            add(SettingEntry.Switch(SettingKeys.PREDICTIVE_BACK, R.string.setting_predictive_back_title, R.string.setting_predictive_back_description, uiState.predictiveBack, settingsViewModel::setPredictiveBack))
            if (ThemeMode.fromId(uiState.themeModeId).isMonet) {
                add(SettingEntry.MonetColor(uiState.monetColorId, settingsViewModel::setMonetColor))
            }
            add(SettingEntry.Language(selectedLanguage) { option ->
                settingsViewModel.setLanguage(option.tag)
                context.findActivity()?.recreate()
            })
        }
    )

    SettingsContent(
        categories = categories,
        snackbarHostState = snackbarHostState,
        restartDialogEnabled = restartDialogEnabled,
        onRestartDialogDismiss = { restartDialogEnabled = null },
        onBack = { navController.navigateUp() },
        onResetConfirmed = {
            settingsViewModel.resetToDefaults()
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsContent(
    categories: List<Pair<SettingsCategory, List<SettingEntry>>>,
    snackbarHostState: SnackbarHostState,
    restartDialogEnabled: Boolean?,
    onRestartDialogDismiss: () -> Unit,
    onBack: () -> Unit,
    onResetConfirmed: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val resetMenuItems = remember {
        listOf(
            DropdownItem(
                text = context.getString(R.string.settings_reset_all),
                icon = { modifier ->
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                        modifier = modifier.size(20.dp)
                    )
                },
                onClick = { showResetDialog = true }
            )
        )
    }

    val filtered = if (searchQuery.isBlank()) {
        categories
    } else {
        categories
            .map { (category, entries) -> category to entries.filter { entryMatches(searchQuery, searchTextOf(it, context)) } }
            .filter { it.second.isNotEmpty() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.screen_settings),
                    subtitle = stringResource(R.string.screen_settings_subtitle),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.cd_navigate_back)
                            )
                        }
                    },
                    actions = {
                        WindowIconDropdownMenu(
                            entry = DropdownEntry(items = resetMenuItems),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.cd_options),
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
        BlurBackdropBox(backdrop) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        enableScrollEndHaptic = true,
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior
                    ),
                contentPadding = contentPadding,
            ) {
                item(key = "search_box") {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        label = stringResource(R.string.settings_search_hint)
                    )
                }

                val display = if (searchQuery.isBlank()) categories else filtered
                display.forEach { (category, entries) ->
                    item(key = "${category.name}_title") {
                        SmallTitle(text = stringResource(category.titleRes))
                    }
                    item(key = "${category.name}_card") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            // animateContentSize gives hle-style expand/collapse when a switch
                            // reveals or hides its dependent rows (sliders, text inputs, …).
                            Column(
                                modifier = Modifier.animateContentSize()
                            ) {
                                entries.forEach { entry ->
                                    SettingEntryRow(entry)
                                }
                            }
                        }
                    }
                }

                if (searchQuery.isNotBlank() && filtered.isEmpty()) {
                    item(key = "search_empty") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_search_empty),
                                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AppDialog(
            title = stringResource(R.string.settings_reset_title),
            onDismissRequest = { showResetDialog = false },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                showResetDialog = false
                onResetConfirmed()
            },
            confirmDestructive = true,
            dismissText = stringResource(R.string.action_cancel)
        ) {
            Text(
                text = stringResource(R.string.settings_reset_message),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }

    restartDialogEnabled?.let { enabled ->
        AppDialog(
            title = stringResource(R.string.dialog_restart_required_title),
            onDismissRequest = onRestartDialogDismiss,
            confirmText = stringResource(R.string.action_ok),
            onConfirm = onRestartDialogDismiss
        ) {
            Text(
                text = stringResource(
                    if (enabled) R.string.dialog_restart_required_enable_message
                    else R.string.dialog_restart_required_disable_message
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
private fun SettingEntryRow(entry: SettingEntry) {
    when (entry) {
        is SettingEntry.Switch -> SwitchRow(
            title = stringResource(entry.titleRes),
            description = stringResource(entry.descriptionRes),
            checked = entry.checked,
            onCheckedChange = entry.onCheckedChange,
        )

        is SettingEntry.Numeric -> NumericRow(
            title = stringResource(entry.setting.titleRes),
            description = stringResource(entry.setting.descriptionRes),
            checked = entry.enabled,
            onCheckedChange = entry.onEnabledChange,
            value = entry.value,
            onValueChange = entry.onValueChange,
            range = entry.setting.min..entry.setting.max,
            unit = stringResource(entry.setting.unitRes),
            fractionDigits = entry.setting.decimals,
            stepSize = entry.setting.precision,
        )

        is SettingEntry.Text -> TextRow(
            title = stringResource(entry.titleRes),
            value = entry.value,
            label = stringResource(entry.labelRes),
            kind = entry.inputKind,
            onValueChange = entry.onValueChange,
        )

        is SettingEntry.SecretText -> SecretTextRow(
            title = stringResource(entry.titleRes),
            description = stringResource(entry.descriptionRes),
            label = stringResource(entry.labelRes),
            isConfigured = entry.isConfigured,
            onValueChange = entry.onValueChange,
            onClear = entry.onClear,
        )

        is SettingEntry.Theme -> ThemeRow(
            selectedModeId = entry.selectedModeId,
            onSelected = entry.onSelected,
        )

        is SettingEntry.MonetColor -> MonetColorRow(
            selectedColorId = entry.selectedColorId,
            onSelected = entry.onSelected,
        )

        is SettingEntry.Language -> LanguageRow(
            selectedTag = entry.selected.tag,
            onSelected = entry.onSelected,
        )

        is SettingEntry.MapSource -> MapSourceRow(
            selectedSource = entry.selected,
            onSourceSelected = entry.onSelected
        )

        is SettingEntry.IslandStyle -> IslandStyleRow(
            selected = entry.selected,
            onSelected = entry.onSelected,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = description,
    )
}

@Composable
private fun NumericRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    fractionDigits: Int,
    stepSize: Float = 0f,
) {
    val hapticFeedback = LocalHapticFeedback.current

    Column {
        SwitchPreference(
            checked = checked,
            onCheckedChange = onCheckedChange,
            title = title,
            summary = description,
        )

        if (checked) {
            SliderPreference(
                value = value,
                onValueChange = { newValue ->
                    val snapped = if (stepSize > 0f) {
                        val stepsFromMin = Math.round((newValue - range.start) / stepSize)
                        (range.start + stepsFromMin * stepSize).coerceIn(range.start, range.endInclusive)
                    } else {
                        newValue
                    }
                    // Soft linear-motor tick per step (CLOCK_TICK), never the harsh long-press buzz.
                    if (snapped != value) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onValueChange(snapped)
                },
                valueText = String.format(Locale.US, "%.${fractionDigits}f %s", value, unit),
                valueRange = range,
            )
        }
    }
}

@Composable
private fun TextRow(
    title: String,
    value: String,
    label: String,
    kind: TextInputKind = TextInputKind.TEXT,
    onValueChange: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    ArrowPreference(
        title = title,
        summary = value.ifEmpty { stringResource(R.string.not_set) },
        onClick = { dialogOpen = true },
    )

    if (dialogOpen) {
        TextInputDialog(
            title = title,
            label = label,
            initialValue = value,
            confirmText = stringResource(R.string.action_ok),
            dismissText = stringResource(R.string.action_cancel),
            onDismissRequest = { dialogOpen = false },
            onConfirm = onValueChange,
        )
    }
}

/**
 * Credential row (e.g. Amap Web-Service key). The collapsed value only ever shows whether the
 * credential is configured — the stored value is never echoed back. The dialog hides input as
 * it is typed and offers an explicit clear action; an empty OK keeps the existing value.
 */
@Composable
private fun SecretTextRow(
    title: String,
    description: String,
    label: String,
    isConfigured: Boolean,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Text(
            text = stringResource(if (isConfigured) R.string.setting_secret_configured else R.string.setting_secret_not_configured),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (dialogOpen) {
        var textValue by remember { mutableStateOf("") }
        AppDialog(
            title = title,
            onDismissRequest = { dialogOpen = false },
            confirmText = stringResource(R.string.action_ok),
            onConfirm = {
                onValueChange(textValue)
                dialogOpen = false
            },
            dismissText = stringResource(R.string.action_cancel),
        ) {
            TextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = label,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth()
            )
                if (isConfigured) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setting_secret_clear),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                onClear()
                                dialogOpen = false
                            }
                            .padding(vertical = 6.dp)
                    )
                }
        }
    }
}

@Composable
private fun ThemeRow(
    selectedModeId: Int,
    onSelected: (Int) -> Unit,
) {
    val modeOptions = listOf(
        stringResource(R.string.theme_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark),
        stringResource(R.string.theme_system_monet),
        stringResource(R.string.theme_light_monet),
        stringResource(R.string.theme_dark_monet),
    )
    WindowDropdownPreference(
        title = stringResource(R.string.setting_theme_title),
        summary = stringResource(R.string.setting_theme_description),
        items = modeOptions,
        selectedIndex = selectedModeId,
        onSelectedIndexChange = onSelected,
    )
}

@Composable
private fun IslandStyleRow(
    selected: IslandStyleOption,
    onSelected: (IslandStyleOption) -> Unit,
) {
    val styleOptions = IslandStyleOption.entries.map { stringResource(it.labelRes) }
    WindowDropdownPreference(
        title = stringResource(R.string.setting_island_style_title),
        summary = stringResource(R.string.setting_island_style_description),
        items = styleOptions,
        selectedIndex = IslandStyleOption.entries.indexOf(selected),
        onSelectedIndexChange = { index ->
            IslandStyleOption.entries.getOrNull(index)?.let(onSelected)
        },
    )
}

@Composable
private fun MonetColorRow(
    selectedColorId: Int,
    onSelected: (Int) -> Unit,
) {
    val colorOptions = listOf(
        stringResource(R.string.monet_default),
        stringResource(R.string.monet_blue),
        stringResource(R.string.monet_green),
        stringResource(R.string.monet_red),
        stringResource(R.string.monet_yellow),
        stringResource(R.string.monet_orange),
        stringResource(R.string.monet_purple),
        stringResource(R.string.monet_pink),
    )
    WindowDropdownPreference(
        title = stringResource(R.string.setting_monet_color_title),
        items = colorOptions,
        selectedIndex = MonetColor.fromId(selectedColorId).id,
        onSelectedIndexChange = onSelected,
    )
}

@Composable
private fun LanguageRow(
    selectedTag: String,
    onSelected: (LanguageOption) -> Unit,
) {
    val selected = LanguageOption.fromTag(selectedTag)
    val languageOptions = LanguageOption.entries.map { stringResource(it.labelRes) }
    WindowDropdownPreference(
        title = stringResource(R.string.setting_language_title),
        summary = stringResource(R.string.setting_language_description),
        items = languageOptions,
        selectedIndex = LanguageOption.entries.indexOf(selected),
        onSelectedIndexChange = { index ->
            LanguageOption.entries.getOrNull(index)?.let(onSelected)
        },
    )
}

@Composable
private fun MapSourceRow(
    selectedSource: MapSourceOption,
    onSourceSelected: (MapSourceOption) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ArrowPreference(
        title = stringResource(R.string.setting_map_source_title),
        summary = stringResource(selectedSource.labelRes),
        onClick = { showDialog = true },
    )

    if (showDialog) {
        MapSourceSelectionDialog(
            selectedSource = selectedSource,
            onSourceSelected = {
                onSourceSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

private fun entryMatches(query: String, text: String): Boolean =
    text.contains(query.trim(), ignoreCase = true)

private fun searchTextOf(entry: SettingEntry, context: Context): String = when (entry) {
    is SettingEntry.Switch -> "${context.getString(entry.titleRes)} ${context.getString(entry.descriptionRes)}"
    is SettingEntry.Numeric -> "${context.getString(entry.setting.titleRes)} ${context.getString(entry.setting.descriptionRes)}"
    is SettingEntry.Text -> "${context.getString(entry.titleRes)} ${context.getString(entry.descriptionRes)}"
    is SettingEntry.SecretText -> "${context.getString(entry.titleRes)} ${context.getString(entry.descriptionRes)}"
    is SettingEntry.Theme -> "${context.getString(R.string.setting_theme_title)} ${context.getString(R.string.setting_theme_description)}"
    is SettingEntry.MonetColor -> "${context.getString(R.string.setting_monet_color_title)} ${context.getString(R.string.setting_monet_color_description)}"
    is SettingEntry.Language -> "${context.getString(R.string.setting_language_title)} ${context.getString(R.string.setting_language_description)}"
    is SettingEntry.MapSource -> "${context.getString(R.string.setting_map_source_title)} ${context.getString(R.string.setting_map_source_description)}"
    is SettingEntry.IslandStyle -> "${context.getString(R.string.setting_island_style_title)} ${context.getString(R.string.setting_island_style_description)}"
}
