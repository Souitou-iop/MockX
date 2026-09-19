package com.noobexon.xposedfakelocation.manager.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.RefreshRateHelper
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixCard
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixCardDivider
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixCategoryTitle
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixDialog
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixDialogButton
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixLargeTitleHeader
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixSearchBox
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixSlider
import com.noobexon.xposedfakelocation.manager.ui.theme.MiuixSwitch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.ui.map.MapSourceOption
import com.noobexon.xposedfakelocation.manager.ui.map.MapSourceSelectionDialog
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import kotlinx.coroutines.launch
import java.util.Locale

private object Dimensions {
    val SPACING_EXTRA_SMALL = 4.dp
    val SPACING_SMALL = 8.dp
    val SPACING_MEDIUM = 16.dp
    val SPACING_LARGE = 24.dp
    val CARD_CORNER_RADIUS = 18.dp
}

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

    val scope = rememberCoroutineScope()
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
        SettingsCategory.APPEARANCE to listOf(
            SettingEntry.Theme(uiState.themeOption) { option ->
                settingsViewModel.setTheme(option)
            }
        ),
        SettingsCategory.LANGUAGE to listOf(
            SettingEntry.Language(selectedLanguage) { option ->
                settingsViewModel.setLanguage(option.tag)
                context.findActivity()?.recreate()
            }
        )
    )

    SettingsContent(
        categories = categories,
        snackbarHostState = snackbarHostState,
        restartDialogEnabled = restartDialogEnabled,
        onRestartDialogDismiss = { restartDialogEnabled = null },
        onBack = { navController.navigateUp() },
        onResetConfirmed = {
            settingsViewModel.resetToDefaults()
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_reset_done))
            }
        }
    )
}

/**
 * HyperOS / Miuix Redesigned Settings Screen Layout.
 *
 * Implements:
 * - Collapsible / Expandable Large Title Header ("设置").
 * - Always accessible Capsule Search Bar.
 * - Squircle Grouped Cards with smooth 18.dp rounded corners and inset dividers.
 * - Reset All Options dropdown dialog.
 */
@Composable
private fun SettingsContent(
    categories: List<Pair<SettingsCategory, List<SettingEntry>>>,
    snackbarHostState: SnackbarHostState,
    restartDialogEnabled: Boolean?,
    onRestartDialogDismiss: () -> Unit,
    onBack: () -> Unit,
    onResetConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var query by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val resetMenuItems = remember {
        listOf(
            DropdownItem(
                text = context.getString(R.string.settings_reset_all),
                icon = {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { showResetDialog = true }
            )
        )
    }

    val filtered = if (query.isBlank()) {
        categories
    } else {
        categories
            .map { (category, entries) -> category to entries.filter { entryMatches(query, searchTextOf(it, context)) } }
            .filter { it.second.isNotEmpty() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            // HyperOS Large Title Header with back button and menu
            MiuixLargeTitleHeader(
                title = stringResource(R.string.screen_settings),
                subtitle = "自定义定位算法与系统防检测策略",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = MaterialTheme.colorScheme.onBackground
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
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // HyperOS Capsule Search Box
            MiuixSearchBox(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.settings_search_hint),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Settings Grouped Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filtered.forEach { (category, entries) ->
                        MiuixCategoryTitle(stringResource(category.titleRes))
                        MiuixCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            entries.forEachIndexed { index, entry ->
                                key(entry.key) {
                                    SettingEntryRow(entry)
                                    if (index != entries.lastIndex) {
                                        MiuixCardDivider(startPadding = 16.dp, endPadding = 16.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showResetDialog) {
            MiuixDialog(
                onDismissRequest = { showResetDialog = false },
                title = stringResource(R.string.settings_reset_title),
                confirmButton = {
                    MiuixDialogButton(
                        text = stringResource(R.string.action_ok),
                        isDestructive = true,
                        onClick = {
                            showResetDialog = false
                            onResetConfirmed()
                        }
                    )
                },
                dismissButton = {
                    MiuixDialogButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showResetDialog = false }
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.settings_reset_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        restartDialogEnabled?.let { enabled ->
            MiuixDialog(
                onDismissRequest = onRestartDialogDismiss,
                title = stringResource(R.string.dialog_restart_required_title),
                confirmButton = {
                    MiuixDialogButton(
                        text = stringResource(R.string.action_ok),
                        isPrimary = true,
                        onClick = onRestartDialogDismiss
                    )
                }
            ) {
                Text(
                    text = stringResource(
                        if (enabled) R.string.dialog_restart_required_enable_message
                        else R.string.dialog_restart_required_disable_message
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
            steps = 0,
            fractionDigits = entry.setting.decimals,
            stepSize = entry.setting.precision,
        )

        is SettingEntry.Text -> TextRow(
            title = stringResource(entry.titleRes),
            description = stringResource(entry.descriptionRes),
            label = stringResource(entry.labelRes),
            value = entry.value,
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
            current = entry.selected,
            onSelect = entry.onSelected,
        )

        is SettingEntry.Language -> LanguageRow(
            current = entry.selected,
            onSelect = entry.onSelected,
        )

        is SettingEntry.MapSource -> MapSourceRow(
            selectedSource = entry.selected,
            onSourceSelected = entry.onSelected
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Dimensions.SPACING_MEDIUM)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
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
    steps: Int,
    fractionDigits: Int,
    stepSize: Float = 0f,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = Dimensions.SPACING_MEDIUM)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MiuixSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }

        if (checked) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = String.format(Locale.US, "%.${fractionDigits}f %s", value, unit),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            MiuixSlider(
                value = value,
                onValueChange = { newValue ->
                    val snapped = if (stepSize > 0f) {
                        val stepsFromMin = Math.round((newValue - range.start) / stepSize)
                        (range.start + stepsFromMin * stepSize).coerceIn(range.start, range.endInclusive)
                    } else {
                        newValue
                    }
                    onValueChange(snapped)
                },
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TextRow(
    title: String,
    description: String,
    label: String,
    value: String,
    kind: TextInputKind = TextInputKind.TEXT,
    onValueChange: (String) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Dimensions.SPACING_MEDIUM)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value.ifEmpty { "未设置" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (dialogOpen) {
        var textValue by remember { mutableStateOf(value) }
        MiuixDialog(
            onDismissRequest = { dialogOpen = false },
            title = title,
            confirmButton = {
                MiuixDialogButton(
                    text = stringResource(R.string.action_ok),
                    isPrimary = true,
                    onClick = {
                        onValueChange(textValue)
                        dialogOpen = false
                    }
                )
            },
            dismissButton = {
                MiuixDialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { dialogOpen = false }
                )
            }
        ) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text(label) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (kind) {
                        TextInputKind.TEXT -> KeyboardType.Text
                        TextInputKind.SIGNED_NUMBER -> KeyboardType.Number
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
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
            .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Dimensions.SPACING_MEDIUM)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(if (isConfigured) R.string.setting_secret_configured else R.string.setting_secret_not_configured),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (dialogOpen) {
        var textValue by remember { mutableStateOf("") }
        MiuixDialog(
            onDismissRequest = { dialogOpen = false },
            title = title,
            confirmButton = {
                MiuixDialogButton(
                    text = stringResource(R.string.action_ok),
                    isPrimary = true,
                    onClick = {
                        onValueChange(textValue)
                        dialogOpen = false
                    }
                )
            },
            dismissButton = {
                MiuixDialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { dialogOpen = false }
                )
            }
        ) {
            Column {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text(label) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrect = false,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isConfigured) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setting_secret_clear),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
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
}

@Composable
private fun ThemeRow(
    current: ThemeOption,
    onSelect: (ThemeOption) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Dimensions.SPACING_MEDIUM)) {
            Text(
                text = stringResource(R.string.setting_theme_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.setting_theme_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(current.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (dialogOpen) {
        MiuixDialog(
            onDismissRequest = { dialogOpen = false },
            title = stringResource(R.string.setting_theme_title),
            dismissButton = {
                MiuixDialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { dialogOpen = false }
                )
            }
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = option == current,
                                onClick = {
                                    onSelect(option)
                                    dialogOpen = false
                                },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    current: LanguageOption,
    onSelect: (LanguageOption) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { dialogOpen = true }
            .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Dimensions.SPACING_MEDIUM)) {
            Text(
                text = stringResource(R.string.setting_language_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.setting_language_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(current.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (dialogOpen) {
        MiuixDialog(
            onDismissRequest = { dialogOpen = false },
            title = stringResource(R.string.setting_language_title),
            dismissButton = {
                MiuixDialogButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { dialogOpen = false }
                )
            }
        ) {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = option == current,
                                onClick = {
                                    onSelect(option)
                                    dialogOpen = false
                                },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSourceRow(
    selectedSource: MapSourceOption,
    onSourceSelected: (MapSourceOption) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = Dimensions.SPACING_MEDIUM, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Dimensions.SPACING_MEDIUM)) {
            Text(
                text = stringResource(R.string.setting_map_source_title),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.setting_map_source_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(selectedSource.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }

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
    is SettingEntry.Language -> "${context.getString(R.string.setting_language_title)} ${context.getString(R.string.setting_language_description)}"
    is SettingEntry.MapSource -> "${context.getString(R.string.setting_map_source_title)} ${context.getString(R.string.setting_map_source_description)}"
}
