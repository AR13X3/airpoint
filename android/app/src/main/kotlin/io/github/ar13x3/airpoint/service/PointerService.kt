package io.github.ar13x3.airpoint.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.ar13x3.airpoint.AirpointApp
import io.github.ar13x3.airpoint.MainActivity
import io.github.ar13x3.airpoint.R
import io.github.ar13x3.airpoint.core.LinkPhase
import io.github.ar13x3.airpoint.core.SessionState
import io.github.ar13x3.airpoint.core.SpenPhase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the process alive (and the S Pen and socket connected) while a session runs in the
 * background, and shows its status in a notification with Center and Stop actions.
 */
class PointerService : LifecycleService() {

    private val controller get() = (application as AirpointApp).controller

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build(controller.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        lifecycleScope.launch {
            controller.state.distinctUntilChanged { a, b -> summary(a) == summary(b) }.collect { s ->
                if (!s.active) {
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(s))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> controller.stop()
            ACTION_CENTER -> controller.centerCursor()
        }
        return START_NOT_STICKY
    }

    private fun summary(s: SessionState): String = when {
        !s.active -> ""
        s.spen.phase == SpenPhase.Failed -> getString(R.string.notif_spen_problem)
        s.link.phase == LinkPhase.Connected && s.spen.phase == SpenPhase.Ready ->
            getString(R.string.notif_pointing, s.link.pc?.name.orEmpty())
        s.link.phase == LinkPhase.Reconnecting -> getString(R.string.notif_reconnecting, s.link.pc?.name.orEmpty())
        s.link.phase == LinkPhase.Failed -> getString(R.string.notif_pc_problem)
        else -> getString(R.string.notif_connecting)
    }

    private fun build(s: SessionState): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String, req: Int) = PendingIntent.getService(
            this, req, Intent(this, PointerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_airpoint)
            .setColor(ContextCompat.getColor(this, R.color.accent))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(summary(s).ifEmpty { getString(R.string.notif_connecting) })
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.action_center), action(ACTION_CENTER, 1))
            .addAction(0, getString(R.string.action_stop), action(ACTION_STOP, 2))
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            .apply { description = getString(R.string.notif_channel_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "session"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.ar13x3.airpoint.STOP"
        private const val ACTION_CENTER = "io.github.ar13x3.airpoint.CENTER"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, PointerService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, PointerService::class.java))
        }
    }
}
