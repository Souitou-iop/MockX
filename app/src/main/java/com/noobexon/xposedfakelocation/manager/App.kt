package com.noobexon.xposedfakelocation.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import com.noobexon.xposedfakelocation.data.DEFAULT_PREDICTIVE_BACK_ENABLED
import com.noobexon.xposedfakelocation.data.KEY_PREDICTIVE_BACK_ENABLED
import com.noobexon.xposedfakelocation.data.SHARED_PREFS_FILE
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private val _serviceState = MutableStateFlow<XposedService?>(null)
        val serviceState: StateFlow<XposedService?> = _serviceState.asStateFlow()
        val service: XposedService? get() = _serviceState.value   // keep existing callers working
    }

    override fun onCreate() {
        super.onCreate()
        applyPredictiveBackGesture()
        XposedServiceHelper.registerListener(this)   // exactly once

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                RefreshRateHelper.applyHighRefreshRate(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                RefreshRateHelper.applyHighRefreshRate(activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Opts the app into (or out of) the Android predictive back gesture by flipping the hidden
     * `ApplicationInfo.setEnableOnBackInvokedCallback` flag before any activity attaches. Read
     * from the app-local preferences; a settings change applies on the next process start.
     */
    private fun applyPredictiveBackGesture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val prefs = getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_PREDICTIVE_BACK_ENABLED, DEFAULT_PREDICTIVE_BACK_ENABLED)
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"
            )
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(applicationInfo, enabled)
        }
    }

    override fun onServiceBind(service: XposedService) {
        _serviceState.value = service
    }

    override fun onServiceDied(service: XposedService) {
        _serviceState.value = null
    }
}
