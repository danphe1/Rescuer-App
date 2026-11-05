package org.nepalscouts.rescuer.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.nepalscouts.rescuer.security.SecureSessionStore

object TrackingController {
    fun start(context: Context, missionId: String, lowData: Boolean) {
        require(missionId.isNotBlank()) { "Active mission is required to start rescue tracking" }
        SecureSessionStore(context).apply {
            setMission(missionId)
            setLowData(lowData)
            setTrackingActive(true)
        }
        val intent = Intent(context, RescueLocationService::class.java).setAction(RescueLocationService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, RescueLocationService::class.java).setAction(RescueLocationService.ACTION_STOP))
    }
}
