package com.noobexon.xposedfakelocation.manager.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.noobexon.xposedfakelocation.data.KEY_MONET_COLOR
import com.noobexon.xposedfakelocation.data.KEY_THEME_MODE
import com.noobexon.xposedfakelocation.data.SHARED_PREFS_FILE
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * App theme root, following the same pattern as HyperLyrics-Enhanced: the wrapper observes the
 * SharedPreferences keys directly so a settings change recomposes the tree immediately, without
 * an Activity recreation and without routing through any ViewModel.
 *
 * The [ThemeController] uses miuix's stock HyperOS palette for the plain modes and generates
 * Monet schemes for the dynamic modes, optionally seeded by [MonetColor].
 */
@Composable
fun MockXTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
    }

    var themeModeId by remember {
        mutableIntStateOf(ThemeMode.readFromPrefs(prefs, KEY_THEME_MODE, ThemeMode.DEFAULT_ID))
    }
    var monetColorId by remember {
        mutableIntStateOf(prefs.getInt(KEY_MONET_COLOR, MonetColor.DEFAULT_ID))
    }

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                KEY_THEME_MODE -> themeModeId = ThemeMode.readFromPrefs(p, KEY_THEME_MODE, ThemeMode.DEFAULT_ID)
                KEY_MONET_COLOR -> monetColorId = p.getInt(KEY_MONET_COLOR, MonetColor.DEFAULT_ID)
            }
        }
    }

    DisposableEffect(prefs) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val themeController = remember(themeModeId, monetColorId) {
        ThemeController(
            colorSchemeMode = ThemeMode.fromId(themeModeId).colorSchemeMode,
            keyColor = MonetColor.fromId(monetColorId).seed,
        )
    }

    val view = LocalView.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (ThemeMode.fromId(themeModeId)) {
        ThemeMode.LIGHT, ThemeMode.MONET_LIGHT -> false
        ThemeMode.DARK, ThemeMode.MONET_DARK -> true
        ThemeMode.SYSTEM, ThemeMode.MONET_SYSTEM -> isSystemDark
    }

    LaunchedEffect(isDark, view) {
        if (!view.isInEditMode) {
            var currentContext = context
            while (currentContext is ContextWrapper) {
                if (currentContext is Activity) break
                currentContext = currentContext.baseContext
            }
            val window = (currentContext as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MiuixTheme(controller = themeController) {
        content()
    }
}
