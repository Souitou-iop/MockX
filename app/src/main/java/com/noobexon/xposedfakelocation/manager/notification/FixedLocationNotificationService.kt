package com.noobexon.xposedfakelocation.manager.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.MainActivity
import com.noobexon.xposedfakelocation.manager.route.WalkingPhase

/** Keeps fixed virtual-location mode visible as the same compact HyperOS/notification surface. */
class FixedLocationNotificationService : Service() {
    private lateinit var repository: PreferencesRepository

    override fun onCreate() {
        super.onCreate()
        repository = PreferencesRepository(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = null

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(getString(R.string.virtual_location_mode))
        .setContentText(repository.getLastClickedLocation()?.let {
            "%.5f, %.5f".format(it.latitude, it.longitude)
        } ?: getString(R.string.virtual_location_active))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        ))
        .also { builder ->
            if (XiaomiIslandAdapter.isEligible(this)) {
                XiaomiIslandAdapter.apply(
                    builder = builder,
                    context = this,
                    state = WalkingNotificationState(WalkingPhase.PAUSED, 0, 0.0, 0.0, null, 1),
                    title = getString(R.string.virtual_location_mode),
                    status = getString(R.string.virtual_location_mode),
                    distanceSummary = getString(R.string.virtual_location_active),
                    eta = null,
                )
            }
        }
        .build()

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
    }
}
