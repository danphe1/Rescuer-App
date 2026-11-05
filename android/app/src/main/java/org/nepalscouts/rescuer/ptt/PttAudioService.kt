package org.nepalscouts.rescuer.ptt

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.nepalscouts.rescuer.PttActivity
import org.nepalscouts.rescuer.R

class PttAudioService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val team = intent?.getStringExtra(EXTRA_TEAM).orEmpty()
        val state = intent?.getStringExtra(EXTRA_STATE) ?: "Listening for Team and Command PTT"
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(team, state),
            if (android.os.Build.VERSION.SDK_INT >= 29)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else 0
        )
        return START_STICKY
    }

    private fun notification(team: String, state: String): Notification {
        val open = Intent(this, PttActivity::class.java).putExtra(EXTRA_TEAM, team)
        val pending = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("RescueGrid PTT")
            .setContentText(state)
            .setSubText(if (team.isBlank()) "Command + Team" else team)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pending)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "rescuegrid_ptt"
        const val NOTIFICATION_ID = 2202
        const val EXTRA_TEAM = "ptt_team"
        const val EXTRA_STATE = "ptt_state"
    }
}
