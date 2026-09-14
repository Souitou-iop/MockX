package com.noobexon.xposedfakelocation.manager

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager

/**
 * Utility to request the highest available hardware refresh rate for app windows and view trees.
 *
 * Many Android OEM distributions (HyperOS, ColorOS, OriginOS, One UI) default third-party apps
 * or non-video Compose screens to 60Hz under "smart/adaptive" refresh rate settings.
 *
 * To guarantee 120Hz/144Hz smoothness across secondary screens and scrolling lists:
 * 1. Resolves the active display resolution and selects the highest refresh rate mode matching
 *    the exact physical dimensions, preventing WindowManager mode-rejection bugs.
 * 2. Sets [WindowManager.LayoutParams.preferredRefreshRate] (API 30+).
 * 3. Applies [View.setFrameRate] (API 30+) to the window decor and root view trees.
 * 4. Applies [View.setFrameRateCategory] (API 34/35+) with `FRAME_RATE_CATEGORY_HIGH`.
 * 5. Uses a post-attach callback to guarantee properties survive Window attachment and lifecycle passes.
 */
object RefreshRateHelper {

    fun applyHighRefreshRate(context: Context) {
        val activity = findActivity(context) ?: return
        applyHighRefreshRate(activity)
    }

    fun applyHighRefreshRate(activity: Activity) {
        val window = activity.window ?: return
        applyHighRefreshRate(window, activity)
    }

    fun applyHighRefreshRate(window: Window, activity: Activity) {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        } ?: return

        val modes = display.supportedModes ?: return
        val currentMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            display.mode
        } else null

        // 1. Filter modes that match the current physical screen dimensions.
        // Selecting a mode with mismatched dimensions causes WindowManager to reject the mode switch
        // and fall back to 60Hz.
        val matchingModes = if (currentMode != null) {
            modes.filter {
                it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight
            }
        } else {
            modes.toList()
        }

        val targetModes = if (matchingModes.isNotEmpty()) matchingModes else modes.toList()
        val bestMode = targetModes.maxByOrNull { it.refreshRate } ?: return
        val maxRefreshRate = bestMode.refreshRate

        // 2. Set window layout params
        val params = window.attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.preferredRefreshRate = maxRefreshRate
        }
        if (matchingModes.isNotEmpty()) {
            params.preferredDisplayModeId = bestMode.modeId
        }
        window.attributes = params

        // 3. Inject frame rate into DecorView once attached
        val decorView = window.decorView
        applyFrameRateToView(decorView, maxRefreshRate)
        decorView.post {
            applyFrameRateToView(decorView, maxRefreshRate)
        }
    }

    /**
     * Injects high frame rate hints into a specific [View] tree.
     */
    fun applyFrameRateToView(view: View?, refreshRate: Float? = null) {
        if (view == null) return

        val rate: Float = refreshRate ?: run {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.display
            } else {
                null
            }
            display?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate ?: 120.0f
        }

        // View.setFrameRate (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val method = View::class.java.getMethod("setFrameRate", java.lang.Float.TYPE, Integer.TYPE)
                method.invoke(view, rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        }

        // View.setFrameRateCategory (API 34/35+)
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                val method = View::class.java.getMethod("setFrameRateCategory", Integer.TYPE)
                // View.FRAME_RATE_CATEGORY_HIGH = 2
                method.invoke(view, 2)
            }
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
