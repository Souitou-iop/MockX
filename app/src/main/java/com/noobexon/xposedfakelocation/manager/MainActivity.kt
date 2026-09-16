package com.noobexon.xposedfakelocation.manager

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.noobexon.xposedfakelocation.data.SHARED_PREFS_FILE
import com.noobexon.xposedfakelocation.data.KEY_THEME_MODE
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.ui.navigation.AppNavGraph
import com.noobexon.xposedfakelocation.manager.ui.theme.MockXTheme
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeMode
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.attachBaseContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))

        RefreshRateHelper.applyHighRefreshRate(this)

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        // Opaque window background matching the active mode so edge-to-edge gaps never flash.
        val prefs = getSharedPreferences(SHARED_PREFS_FILE, MODE_PRIVATE)
        val modeId = ThemeMode.readFromPrefs(prefs, KEY_THEME_MODE, ThemeMode.DEFAULT_ID)
        val uiMode = resources.configuration.uiMode
        val systemDark = uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = when (ThemeMode.fromId(modeId)) {
            ThemeMode.LIGHT, ThemeMode.MONET_LIGHT -> false
            ThemeMode.DARK, ThemeMode.MONET_DARK -> true
            ThemeMode.SYSTEM, ThemeMode.MONET_SYSTEM -> systemDark
        }
        window.setBackgroundDrawable(
            ColorDrawable(if (isDark) android.graphics.Color.BLACK else 0xFFF7F7F7.toInt())
        )

        setContent {
            MockXTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RefreshRateHelper.applyHighRefreshRate(this)
    }
}
