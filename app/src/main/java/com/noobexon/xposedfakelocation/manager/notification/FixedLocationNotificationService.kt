package com.noobexon.xposedfakelocation.manager.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.MainActivity
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps fixed virtual-location mode visible as the same compact HyperOS/notification surface. */
class FixedLocationNotificationService : Service() {
    private lateinit var repository: PreferencesRepository
    private var refreshThread: Thread? = null
    @Volatile private var refreshRunning = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = PreferencesRepository(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Zero the session start so the next session counts from zero (ControlReceiver's
            // own stop path and the island stop button both funnel through ACTION_STOP).
            serviceScope.launch { repository.saveFixedLocationStartedAt(0L) }
            stopRefresh()
            // Remove the notification (and thus the Super Island) from every surface,
            // not only the foreground slot.
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START) {
            // An explicit START always begins a fresh session: reset the timer so a stale
            // timestamp left over from a previous killed session can never leak into the new one.
            serviceScope.launch { repository.saveFixedLocationStartedAt(System.currentTimeMillis()) }
        } else if (!repository.getIsPlaying()) {
            // START_STICKY re-creation (intent == null) while the session is off: do not revive
            // the island after the user closed it or force-stopped the app.
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startRefresh()
        // NOT_STICKY: for a UI-thread notification surface a system re-creation after the
        // process dies should not silently resurrect the island; the state check above already
        // refused stale restarts, and nothing benefits from an auto-restart here.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRefresh()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    /**
     * Ticks the notification once per second so the elapsed-time line and the collapsed island's
     * right side keep advancing while the (persisted) start time stays fixed. A fresh [Thread]
     * (wake-lock is optional: location spoofing runs in the Xposed process, not here), so a lost
     * tick only delays the display by ≤1 s.
     */
    private fun startRefresh() {
        if (refreshRunning) return
        refreshRunning = true
        refreshThread = Thread {
            while (refreshRunning) {
                SystemClock.sleep(REFRESH_INTERVAL_MS)
                // A STOP may have run (and cancelled the notification) while this thread slept;
                // re-checking the volatile flag here prevents the final notify() from resurrecting
                // the notification — and thus the Super Island — after the user stopped the session.
                if (!refreshRunning) break
                try {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification())
                } catch (_: Exception) {
                    // Notification refresh failures must never stop the foreground service.
                }
            }
        }.apply { isDaemon = true }.also { it.start() }
    }

    /**
     * Surfaces the stop flag and waits (bounded) for the refresh thread to finish its current
     * iteration, so a notify() already in flight when the notification was cancelled can never
     * land after [NotificationManager.cancel]. The thread sleeps at most [REFRESH_INTERVAL_MS]
     * between iterations, so joining that long guarantees no further posts.
     */
    private fun stopRefresh() {
        refreshRunning = false
        refreshThread?.join(REFRESH_INTERVAL_MS + 500L)
        refreshThread = null
    }

    private fun buildNotification(): Notification {
        val coordinates = repository.getLastClickedLocation()?.let {
            "%.5f, %.5f".format(it.latitude, it.longitude)
        }
        val elapsed = formatElapsed(repository.getFixedLocationStartedAt())
        val elapsedLabel = getString(R.string.virtual_location_elapsed, elapsed)
        val contentText = listOfNotNull(coordinates, elapsedLabel).joinToString("\n")
        return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(getString(R.string.virtual_location_mode))
        .setContentText(contentText)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        ))
        .also { builder ->
            // Super Island only renders where the user picked it explicitly (SUPER_ISLAND) or where
            // AUTO falls back to it. GOOGLE_LIVE_UPDATE forces a plain notification here — a fixed
            // location has no progress to show, so the Live Update style is only applied to walking.
            val islandStyle = IslandStyleOption.fromTag(repository.getIslandStyle())
            val useIsland = when (islandStyle) {
                IslandStyleOption.AUTO -> XiaomiIslandAdapter.isEligible(this)
                IslandStyleOption.SUPER_ISLAND -> XiaomiIslandAdapter.isEligible(this)
                IslandStyleOption.GOOGLE_LIVE_UPDATE -> false
            }
            if (useIsland) {
                XiaomiIslandAdapter.apply(
                    builder = builder,
                    context = this,
                    state = WalkingNotificationState(WalkingPhase.PAUSED, 0, 0.0, 0.0, null, 1),
                    title = getString(R.string.virtual_location_mode),
                    status = getString(R.string.virtual_location_mode),
                    distanceSummary = getString(R.string.virtual_location_active),
                    eta = null,
                    islandContent = elapsed,
                    expandedLines = listOfNotNull(coordinates, elapsedLabel),
                    hideProgress = true,
                    timerStartedAt = repository.getFixedLocationStartedAt(),
                    stopActionTitle = getString(R.string.virtual_location_stop),
                )
            }
        }
        .build()
    }

    private fun formatElapsed(startedAtEpochMillis: Long): String {
        if (startedAtEpochMillis <= 0L) return ""
        val totalSeconds = ((System.currentTimeMillis() - startedAtEpochMillis) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.virtual_location_mode), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_START = "io.github.souitou.mockx.action.START_FIXED_NOTIFICATION"
        const val ACTION_STOP = "io.github.souitou.mockx.action.STOP_FIXED_NOTIFICATION"
        const val CHANNEL_ID = "fixed_location"
        const val NOTIFICATION_ID = 4202
        private const val REFRESH_INTERVAL_MS = 1_000L
    }
}
