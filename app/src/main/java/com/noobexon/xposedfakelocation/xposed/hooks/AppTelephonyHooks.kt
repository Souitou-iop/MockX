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
            hookPhoneStateListener(telephonyManagerClass)
            hookTelephonyCallback(telephonyManagerClass)
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

    /**
     * Intercepts [TelephonyManager.listen] to strip cell location and cell info event flags
     * when fake location is active, preventing the system from pushing real cell tower IDs.
     */
    private fun hookPhoneStateListener(clazz: Class<*>) {
        hookAll(clazz, "listen") { chain ->
            val args = chain.args
            val events = args.getOrNull(1) as? Int
            if (events != null && PreferencesUtil.getIsPlaying() == true) {
                // LISTEN_CELL_LOCATION = 0x10, LISTEN_CELL_INFO = 0x400
                val mask = (0x10 or 0x400).inv()
                val filtered = events and mask
                val newArgs = args.toTypedArray()
                newArgs[1] = filtered
                module.log(Log.INFO, tag, "Masked cell events in TelephonyManager#listen (was $events, now $filtered)")
                chain.proceed(newArgs)
            } else {
                chain.proceed()
            }
        }
    }

    /**
     * Intercepts [TelephonyManager.registerTelephonyCallback] (API 31+) to ensure
     * cell info and cell location callbacks are silenced while spoofing.
     */
    private fun hookTelephonyCallback(clazz: Class<*>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        hookAll(clazz, "registerTelephonyCallback") { chain ->
            if (PreferencesUtil.getIsPlaying() == true) {
                val callback = chain.args.getOrNull(1)
                if (callback != null) {
                    val className = callback.javaClass.name
                    if (className.contains("Cell", ignoreCase = true) ||
                        callback.javaClass.interfaces.any { it.name.contains("Cell", ignoreCase = true) }) {
                        module.log(Log.INFO, tag, "Suppressed cell TelephonyCallback registration ($className).")
                        return@hookAll null
                    }
                }
            }
            chain.proceed()
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
