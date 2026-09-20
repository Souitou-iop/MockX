package com.noobexon.xposedfakelocation.manager.notification

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import com.noobexon.xposedfakelocation.R

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
     *
     * @param timerStartedAt epoch-millis when the virtual-location session started; passed through
     * to [XiaomiIslandPayload.build] so 模板13's `highlightInfo.timerInfo` can drive the native
     * counting-up timer. Zero/omitted for walking mode (模板4 has no timer).
     * @param stopActionTitle localized title for the 模板13 stop button; null omits the button.
     */
    fun apply(
        builder: NotificationCompat.Builder,
        context: Context,
        state: WalkingNotificationState,
        title: String,
        status: String,
        distanceSummary: String,
        eta: String?,
        islandContent: String? = null,
        expandedLines: List<String>? = null,
        hideProgress: Boolean = false,
        timerStartedAt: Long = 0L,
        stopActionTitle: String? = null,
    ): Boolean {
        val payload = XiaomiIslandPayload.build(
            title = title,
            status = status,
            distanceSummary = distanceSummary,
            eta = eta,
            progressPercent = if (hideProgress) -1 else state.progressPercent,
            islandContent = islandContent,
            expandedLines = expandedLines,
            timerStartedAt = timerStartedAt,
            stopActionTitle = stopActionTitle,
        ) ?: return false
        return try {
            val extras = Bundle().apply {
                putString(XiaomiIslandPayload.EXTRA_FOCUS_PARAM, payload.json)
                if (payload.pictureNeeded) {
                    putBundle(XiaomiIslandPayload.EXTRA_FOCUS_PICS, buildPicsBundle(context))
                }
            }
            builder.addExtras(extras)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Island extras skipped: ${e.message}")
            false
        }
    }

    /**
     * Assembles the `miui.focus.pics` Bundle with every Icon the payload JSON may reference.
     * Keys that fail to resolve are simply skipped — the OS degrades gracefully (e.g. 进度组件1
     * without forward/middle/end Icons renders as 进度组件2).
     */
    private fun buildPicsBundle(context: Context): Bundle = Bundle().apply {
        // App icon (摘要态 图文组件1 + smallIslandArea).
        val appIconRes = context.applicationInfo.icon
        if (appIconRes != 0) {
            putParcelable(
                XiaomiIslandPayload.KEY_ISLAND_PICTURE,
                Icon.createWithResource(context, appIconRes),
            )
        }
        // 模板4/13 picFunction (功能图标).
        putIconIfValid(context, R.drawable.ic_island_function, XiaomiIslandPayload.KEY_PIC_FUNCTION)
        // 进度组件1 picture set (模板4 walking only; harmless when unused).
        putIconIfValid(context, R.drawable.ic_island_forward, XiaomiIslandPayload.KEY_PIC_FORWARD)
        putIconIfValid(context, R.drawable.ic_island_end, XiaomiIslandPayload.KEY_PIC_END)
        putIconIfValid(context, R.drawable.ic_island_end_unselected, XiaomiIslandPayload.KEY_PIC_END_UNSELECTED)
    }

    private fun Bundle.putIconIfValid(context: Context, resId: Int, key: String) {
        runCatching { Icon.createWithResource(context, resId) }
            .onSuccess { putParcelable(key, it) }
            .onFailure { Log.w(TAG, "Island icon '$key' skipped: ${it.message}") }
    }
}
