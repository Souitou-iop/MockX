package com.noobexon.xposedfakelocation.manager.notification

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * HyperOS Super Island enhancer (通知体验升级规划.md §9): injects the `miui.focus.param` JSON and
 * the island picture bundle into the ONE shared walking notification. It never posts its own
 * notification, never runs network code and never blocks the standard notification: unsupported
 * devices skip injection entirely and any payload/extras failure degrades silently to the plain
 * notification. Real rendering is decided by the OS, not by this adapter (§9.2).
 */
object XiaomiIslandAdapter {

    private const val TAG = "XiaomiIsland"

    /**
     * Low-cost gate (§9.2): `Build.MANUFACTURER` is "Xiaomi" on Xiaomi/Redmi/POCO HyperOS
     * hardware. This is a hint, not a capability proof — a device passing the gate may still
     * render the notification as a standard one, which is the designed fallback.
     */
    fun isEligible(context: Context): Boolean =
        Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    /**
     * Adds the island extras to [builder]. Returns false when the payload was unusable or the
     * extras could not be attached — the caller keeps the standard notification either way.
     */
    fun apply(
        builder: NotificationCompat.Builder,
        context: Context,
        state: WalkingNotificationState,
        title: String,
        status: String,
        distanceSummary: String,
        eta: String?,
    ): Boolean {
        val payload = XiaomiIslandPayload.build(
            title = title,
            status = status,
            distanceSummary = distanceSummary,
            eta = eta,
            progressPercent = state.progressPercent,
        ) ?: return false
        return try {
            val extras = Bundle().apply {
                putString(XiaomiIslandPayload.EXTRA_FOCUS_PARAM, payload.json)
                if (payload.pictureNeeded) {
                    val appIconRes = context.applicationInfo.icon
                    if (appIconRes != 0) {
                        putBundle(
                            XiaomiIslandPayload.EXTRA_FOCUS_PICS,
                            Bundle().apply {
                                putParcelable(
                                    XiaomiIslandPayload.KEY_ISLAND_PICTURE,
                                    Icon.createWithResource(context, appIconRes),
                                )
                            },
                        )
                    }
                }
            }
            builder.addExtras(extras)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Island extras skipped: ${e.message}")
            false
        }
    }
}
