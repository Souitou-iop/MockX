package com.noobexon.xposedfakelocation.manager.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.util.Log
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.notification.FixedLocationNotificationService
import com.noobexon.xposedfakelocation.manager.notification.XiaomiIslandPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exported entry point for the 模板13 island stop button (按钮组件1, 自定义构建 Action).
 *
 * The HyperOS island system resolves the `actionIntent` URI in [XiaomiIslandPayload] against
 * this receiver, so it MUST be declared `android:exported="true"` in the manifest (模板库
 * §关于 actionInfo). It only services the narrow [ACTION_ISLAND_STOP_FIXED] broadcast and
 * otherwise ignores everything, mirroring the fixed-location stop flow of [ControlReceiver]:
 * persist `is_playing=false` and tell [FixedLocationNotificationService] to drop its
 * foreground surface.
 */
class IslandStopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != XiaomiIslandPayload.ACTION_ISLAND_STOP_FIXED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val repository = PreferencesRepository(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.saveIsPlaying(false)
                // Tear the island surface down directly here too, in case the service chain
                // (ACTION_STOP) fails to reach the running service; cancelling the notification
                // removes every island surface immediately.
                appContext.getSystemService(NotificationManager::class.java)
                    .cancel(FixedLocationNotificationService.NOTIFICATION_ID)
                appContext.startService(
                    Intent(appContext, FixedLocationNotificationService::class.java)
                        .setAction(FixedLocationNotificationService.ACTION_STOP),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Island stop failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "IslandStopReceiver"
    }
}