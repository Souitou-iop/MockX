package com.noobexon.xposedfakelocation.xposed.hooks

import android.os.Build
import android.telephony.CellInfo
import android.telephony.NeighboringCellInfo
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Hooker
import java.lang.reflect.Method

/**
 * Hooks [android.telephony.TelephonyManager] inside the target application process.
 *
 * Domestic map SDKs (AMap, Baidu, Tencent) aggressively collect cell tower IDs
 * (MCC, MNC, LAC, CID, TAC, ECI) via [TelephonyManager] in their own process,
 * packaging and uploading them to their cloud location servers for network positioning.
 * When spoofing is active, this hook clears all cell information so map SDKs cannot
 * resolve the device's physical location through cell towers.
 */
class AppTelephonyHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[AppTelephonyHooks]"

    fun init() {
        runCatching {
            val telephonyManagerClass = Class.forName("android.telephony.TelephonyManager", false, classLoader)
            hookCellInfo(telephonyManagerClass)
            hookCellLocation(telephonyManagerClass)
            hookNeighboringCellInfo(telephonyManagerClass)
            hookAsyncCellInfoUpdate(telephonyManagerClass)
            module.log(Log.INFO, tag, "Instantiated app-side TelephonyManager hooks successfully")
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed to initialize AppTelephonyHooks: ${it.message}")
        }
    }

    private fun hookCellInfo(clazz: Class<*>) {
        hookAll(clazz, "getAllCellInfo") { chain ->
            if (PreferencesUtil.getIsPlaying() == true) {
                module.log(Log.INFO, tag, "Cleared getAllCellInfo (app-side) while spoofing.")
                emptyList<CellInfo>()
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookCellLocation(clazz: Class<*>) {
        hookAll(clazz, "getCellLocation") { chain ->
            if (PreferencesUtil.getIsPlaying() == true) {
                module.log(Log.INFO, tag, "Cleared getCellLocation (app-side) while spoofing.")
                null
            } else {
                chain.proceed()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hookNeighboringCellInfo(clazz: Class<*>) {
        hookAll(clazz, "getNeighboringCellInfo") { chain ->
            if (PreferencesUtil.getIsPlaying() == true) {
                module.log(Log.INFO, tag, "Cleared getNeighboringCellInfo (app-side) while spoofing.")
                emptyList<NeighboringCellInfo>()
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookAsyncCellInfoUpdate(clazz: Class<*>) {
        hookAll(clazz, "requestCellInfoUpdate") { chain ->
            if (PreferencesUtil.getIsPlaying() == true) {
                module.log(Log.INFO, tag, "Blocked requestCellInfoUpdate (app-side) while spoofing.")
                defaultReturnValue(chain.executable as? Method)
            } else {
                chain.proceed()
            }
        }
    }

    private fun hookAll(clazz: Class<*>, methodName: String, hooker: Hooker) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) {
            return
        }

        var hooked = 0
        methods.forEach { method ->
            try {
                module.hook(method).intercept(hooker)
                hooked++
            } catch (e: Throwable) {
                module.log(Log.ERROR, tag, "Failed hooking ${clazz.name}#$methodName: ${e.message}")
            }
        }

        if (hooked > 0) {
            module.log(Log.INFO, tag, "Hooked ${clazz.name}#$methodName ($hooked overloads).")
        }
    }

    private fun defaultReturnValue(method: Method?): Any? {
        return when (method?.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0F
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }
}
