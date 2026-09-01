package org.nepalscouts.rescuer.tracking

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.nepalscouts.rescuer.MainActivity
import org.nepalscouts.rescuer.data.LocationPoint
import org.nepalscouts.rescuer.data.RescueDatabase
import org.nepalscouts.rescuer.security.SecureSessionStore
import org.nepalscouts.rescuer.sync.LocationSyncWorker
import java.util.UUID

class RescueLocationService : Service() {
    companion object {
        const val CHANNEL_ID = "rescue_tracking"
        const val NOTIFICATION_ID = 2009
        const val ACTION_START = "org.nepalscouts.rescuer.START_TRACKING"
        const val ACTION_STOP = "org.nepalscouts.rescuer.STOP_TRACKING"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val session by lazy { SecureSessionStore(this) }
    private val dao by lazy { RescueDatabase.get(this).locationDao() }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                val battery = getSystemService(BatteryManager::class.java)
                    ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    ?.takeIf { it in 0..100 }
                val point = LocationPoint(
                    id = UUID.randomUUID().toString(),
                    missionId = session.missionId(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    battery = battery,
                    capturedAt = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
                )
                scope.launch {
                    dao.insert(point)
                    scheduleSync()
                    updateNotification(dao.pendingCount(), point.accuracy)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Starting GPS…"), type)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking()
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return
        }
        session.setTrackingActive(true)
        val lowData = session.lowData()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, if (lowData) 30_000L else 15_000L)
            .setMinUpdateIntervalMillis(if (lowData) 15_000L else 8_000L)
            .setMaxUpdateDelayMillis(if (lowData) 120_000L else 30_000L)
            .build()
        fused.removeLocationUpdates(callback)
        fused.requestLocationUpdates(request, callback, mainLooper)
        updateNotification(0, null)
    }

    private fun stopTracking() {
        fused.removeLocationUpdates(callback)
        session.setTrackingActive(false)
        scheduleSync()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = OneTimeWorkRequestBuilder<LocationSyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniqueWork("rescue-gps-sync", ExistingWorkPolicy.KEEP, work)
    }

    private fun notification(message: String): Notification {
        val openPending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopPending = PendingIntent.getService(this, 1, Intent(this, RescueLocationService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Nepal Scouts Rescue — GPS active")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop after mission", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(queued: Int, accuracy: Float?) {
        val mode = if (session.lowData()) "Low data" else "Standard"
        val accuracyText = accuracy?.let { " · ±${it.toInt()}m" } ?: ""
        ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification("$mode · $queued queued$accuracyText"))
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
