package com.noobexon.xposedfakelocation.manager.notification

import android.content.Context
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import java.util.Locale

/** The localized strings the notification copy is assembled from; JVM tests construct this directly. */
data class WalkingNotificationTexts(
    /** Template with two `%s` placeholders: travelled and total distance. */
    val walkingTemplate: String,
    val paused: String,
    val arrived: String,
    val statusWalking: String,
    val statusPaused: String,
    val statusArrived: String,
)

/**
 * Resolves notification copy from the shared [WalkingNotificationState]. The resolution rules
 * live in the pure [NotificationTextResolver] so they stay unit-testable; this class only reads
 * resources. It never touches preferences or raw coordinates (通知体验升级规划.md §6.2).
 */
class NotificationContentFormatter(private val context: Context) {

    private val texts = WalkingNotificationTexts(
        walkingTemplate = context.getString(R.string.walk_notification_text),
        paused = context.getString(R.string.walk_notification_paused_text),
        arrived = context.getString(R.string.walk_notification_arrived_text),
        statusWalking = context.getString(R.string.walk_status_walking),
        statusPaused = context.getString(R.string.walk_status_paused),
        statusArrived = context.getString(R.string.walk_status_arrived),
    )

    fun contentTitle(): String = context.getString(R.string.walk_notification_title)

    fun contentText(state: WalkingNotificationState): String =
        NotificationTextResolver.contentText(state, texts)

    /** Short status label shared by the HyperOS ticker, AOD line and island summary. */
    fun statusLabel(state: WalkingNotificationState): String =
        NotificationTextResolver.statusLabel(state, texts)

    /** Localized ETA line, or null when the estimate is missing or under a minute. */
    fun etaText(state: WalkingNotificationState): String? {
        val minutes = state.remainingSeconds?.takeIf { it >= 60 }?.let { it / 60 } ?: return null
        return context.getString(R.string.walk_eta_format, minutes.toInt())
    }

    /** "Travelled / total" without any lead-in words, for the compact island surfaces. */
    fun distanceSummary(state: WalkingNotificationState): String =
        NotificationTextResolver.distanceSummary(state.travelledMeters, state.totalMeters)

    companion object {
        /** Mirrors the map screen's distance formatting: "850 m" below 1 km, "1.24 km" above. */
        fun formatDistance(meters: Double): String = when {
            !meters.isFinite() || meters < 0.0 -> String.format(Locale.US, "%.0f m", 0.0)
            meters >= 1000.0 -> String.format(Locale.US, "%.2f km", meters / 1000.0)
            else -> String.format(Locale.US, "%.0f m", meters)
        }
    }
}

/** Pure text rules; no Android types so JVM tests can exercise every phase. */
object NotificationTextResolver {

    fun contentText(state: WalkingNotificationState, texts: WalkingNotificationTexts): String =
        when (state.phase) {
            WalkingPhase.PAUSED -> texts.paused
            WalkingPhase.ARRIVED -> texts.arrived
            else -> String.format(
                Locale.US,
                texts.walkingTemplate,
                NotificationContentFormatter.formatDistance(state.travelledMeters),
                NotificationContentFormatter.formatDistance(state.totalMeters),
            )
        }

    fun statusLabel(state: WalkingNotificationState, texts: WalkingNotificationTexts): String =
        when (state.phase) {
            WalkingPhase.PAUSED -> texts.statusPaused
            WalkingPhase.ARRIVED -> texts.statusArrived
            else -> texts.statusWalking
        }

    fun distanceSummary(travelledMeters: Double, totalMeters: Double): String =
        "${NotificationContentFormatter.formatDistance(travelledMeters)} / " +
            NotificationContentFormatter.formatDistance(totalMeters)
}
