package com.noobexon.xposedfakelocation.manager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class App : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private val _serviceState = MutableStateFlow<XposedService?>(null)
        val serviceState: StateFlow<XposedService?> = _serviceState.asStateFlow()
        val service: XposedService? get() = _serviceState.value   // keep existing callers working
    }

    override fun onCreate() {
        super.onCreate()
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

    override fun onServiceBind(service: XposedService) {
        _serviceState.value = service
    }

    override fun onServiceDied(service: XposedService) {
        _serviceState.value = null
    }
}