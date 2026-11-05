package org.nepalscouts.rescuer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.nepalscouts.rescuer.tracking.RescueLocationService

class RescuerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RescueLocationService.CHANNEL_ID,
                "Active rescue tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required while Nepal Scouts rescue GPS tracking is active"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
