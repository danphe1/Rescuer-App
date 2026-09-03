package org.nepalscouts.rescuer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import org.nepalscouts.rescuer.ptt.PttAudioService
import org.nepalscouts.rescuer.tracking.RescueLocationService

@HiltAndroidApp
class RescuerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    RescueLocationService.CHANNEL_ID,
                    "Active rescue tracking",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Required while RescueGrid GPS tracking is active"
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    PttAudioService.CHANNEL_ID,
                    "RescueGrid push-to-talk",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming and background push-to-talk audio"
                    setShowBadge(false)
                }
            )
        }
    }
}
