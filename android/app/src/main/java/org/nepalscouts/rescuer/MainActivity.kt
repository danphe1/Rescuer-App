package org.nepalscouts.rescuer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.nepalscouts.rescuer.security.SecureSessionStore
import org.nepalscouts.rescuer.tracking.TrackingController

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var lowData: Switch
    private val session by lazy { SecureSessionStore(this) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) startForCurrentMission() else status.text = "Location permission is required for rescue tracking."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Nepal Scouts Rescuer"; textSize = 24f })
        status = TextView(this).apply { text = if (session.trackingActive()) "GPS tracking active" else "GPS tracking stopped"; textSize = 16f }
        root.addView(status)
        lowData = Switch(this).apply { text = "Low Data Mode"; isChecked = session.lowData() }
        root.addView(lowData)
        root.addView(Button(this).apply { text = "START MISSION TRACKING"; setOnClickListener { requestAndStart() } })
        root.addView(Button(this).apply { text = "STOP TRACKING"; setOnClickListener { TrackingController.stop(this@MainActivity); status.text = "GPS tracking stopping…" } })
        root.addView(TextView(this).apply {
            text = "Tracking only runs during an approved active mission. Low Data Mode stores GPS locally and batches network uploads."
        })
        setContentView(root)
    }

    private fun requestAndStart() {
        val missionId = session.missionId()
        if (missionId.isNullOrBlank()) {
            status.text = "No approved active mission. Login/assignment must set the mission before tracking starts."
            return
        }
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
            permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray()) else startForCurrentMission()
    }

    private fun startForCurrentMission() {
        val missionId = session.missionId() ?: return
        session.setLowData(lowData.isChecked)
        TrackingController.start(this, missionId, lowData.isChecked)
        status.text = "GPS tracking active — persistent notification enabled"
    }
}
