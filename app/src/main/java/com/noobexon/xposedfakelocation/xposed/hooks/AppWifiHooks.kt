package com.noobexon.xposedfakelocation.xposed.hooks

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

/**
 * App-side Wi-Fi identity & location leak prevention hooks.
 *
 * Domestic map SDKs (AMap, Baidu, Tencent) primarily determine device location in indoor and
 * urban scenarios through Wi-Fi AP scanning (BSSID + RSSI) sent to their positioning servers.
 *
 * This hook guarantees that whenever fake location is active ([PreferencesUtil.getIsPlaying]):
 * 1. Surrounding Wi-Fi scan results ([WifiManager.getScanResults]) are always cleared so nearby APs
 *    cannot be uploaded to locate the device.
 * 2. Background Wi-Fi scans ([WifiManager.startScan]) are suppressed.
 * 3. Connected Wi-Fi info ([WifiManager.getConnectionInfo]) returns either the user-configured fake
 *    Wi-Fi identity or a sanitized WifiInfo (with masked BSSID `02:00:00:00:00:00`) to prevent the
 *    connected router's MAC from exposing the physical location.
 */
class AppWifiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader,
    private val packageName: String
) {
    private val tag = "[AppWifiHooks]"

    fun init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            module.log(Log.WARN, tag, "App-side Wi-Fi hooks require Android 11 or newer.")
            return
        }
        hookConnectionInfo()
        hookScanResults()
        hookStartScan()
        module.log(Log.INFO, tag, "Instantiated app-side Wi-Fi hooks successfully")
    }

    /**
     * Hooks `WifiManager.getConnectionInfo()` to return a fake or sanitized [WifiInfo]
     * when spoofing is enabled, preventing the real router MAC from leaking.
     */
    private fun hookConnectionInfo() {
        runCatching {
            val wifiManagerClass = Class.forName("android.net.wifi.WifiManager", false, classLoader)
            val method = wifiManagerClass.getDeclaredMethod("getConnectionInfo")
            module.hook(method).intercept { chain ->
                val isPlaying = PreferencesUtil.getIsPlaying() == true
                if (!isPlaying) {
                    return@intercept chain.proceed()
                }

                val identity = WifiIdentityHookPolicy.readActiveIdentity(module)
                if (identity != null && identity.targets(packageName)) {
                    module.log(Log.INFO, tag, "Replaced Wi-Fi connection info with configured identity.")
                    createFakeWifiInfo(identity)
                } else {
                    // Even without a specific identity, mask the real BSSID when fake location is playing
                    // to prevent map SDKs from looking up the user's real AP in their database.
                    module.log(Log.INFO, tag, "Sanitized Wi-Fi connection info to prevent location leak.")
                    createSanitizedWifiInfo()
                }
            }
            module.log(Log.INFO, tag, "Hooked WifiManager#getConnectionInfo.")
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed hooking WifiManager#getConnectionInfo: ${it.message}")
        }
    }

    /**
     * Hooks `WifiManager.getScanResults()` to always return an empty list while spoofing is
     * enabled, ensuring map SDKs cannot collect surrounding APs to compute real coordinates.
     */
    private fun hookScanResults() {
        runCatching {
            val wifiManagerClass = Class.forName("android.net.wifi.WifiManager", false, classLoader)
            val method = wifiManagerClass.getDeclaredMethod("getScanResults")
            module.hook(method).intercept { chain ->
                if (PreferencesUtil.getIsPlaying() == true) {
                    module.log(Log.INFO, tag, "Cleared Wi-Fi scan results (app-side) to prevent location leak.")
                    emptyList<ScanResult>()
                } else {
                    chain.proceed()
                }
            }
            module.log(Log.INFO, tag, "Hooked WifiManager#getScanResults.")
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed hooking WifiManager#getScanResults: ${it.message}")
        }
    }

    /**
     * Hooks `WifiManager.startScan()` to suppress active Wi-Fi scans while spoofing is active.
     */
    @Suppress("DEPRECATION")
    private fun hookStartScan() {
        runCatching {
            val wifiManagerClass = Class.forName("android.net.wifi.WifiManager", false, classLoader)
            val method = wifiManagerClass.getDeclaredMethod("startScan")
            module.hook(method).intercept { chain ->
                if (PreferencesUtil.getIsPlaying() == true) {
                    module.log(Log.INFO, tag, "Suppressed WifiManager#startScan while spoofing.")
                    true
                } else {
                    chain.proceed()
                }
            }
            module.log(Log.INFO, tag, "Hooked WifiManager#startScan.")
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed hooking WifiManager#startScan: ${it.message}")
        }
    }

    /**
     * Builds a fake [WifiInfo] from the configured SSID/BSSID/RSSI preferences.
     */
    @Suppress("DEPRECATION")
    private fun createFakeWifiInfo(identity: WifiIdentity): WifiInfo {
        return WifiInfo.Builder()
            .setBssid(identity.bssid)
            .setSsid(identity.ssid.toByteArray())
            .setRssi(identity.rssi)
            .setNetworkId(0)
            .build()
    }

    /**
     * Builds a sanitized [WifiInfo] with a masked generic BSSID to prevent cloud location lookups.
     */
    @Suppress("DEPRECATION")
    private fun createSanitizedWifiInfo(): WifiInfo {
        return WifiInfo.Builder()
            .setBssid("02:00:00:00:00:00")
            .setSsid("AndroidWifi".toByteArray())
            .setRssi(-60)
            .setNetworkId(0)
            .build()
    }
}
