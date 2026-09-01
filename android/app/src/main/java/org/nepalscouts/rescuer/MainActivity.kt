package org.nepalscouts.rescuer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.nepalscouts.rescuer.network.RescueApi
import org.nepalscouts.rescuer.network.RescueStatus
import org.nepalscouts.rescuer.security.SecureSessionStore
import org.nepalscouts.rescuer.sync.OfflineActionQueue
import org.nepalscouts.rescuer.tracking.TrackingController

class MainActivity : AppCompatActivity() {
    private val session by lazy { SecureSessionStore(this) }
    private val api = RescueApi()
    private lateinit var root: LinearLayout
    private var current: RescueStatus? = null
    private var pendingStartAfterPermission = false
    private val sosHandler = Handler(Looper.getMainLooper())
    private var sosArmed = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            startTrackingForCurrentMission()
        } else if (!locationGranted) toast("Location permission is required for rescue tracking.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(247, 249, 247))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        session.deviceToken()?.let { restoreSession(it) } ?: showLogin()
    }

    private fun showLogin(message: String? = null) {
        current = null; root.removeAllViews(); title("Nepal Scouts Rescuer")
        text("Native field app · Android 6+ · Low Data ready", 14f)
        if (!message.isNullOrBlank()) text(message, 14f, Color.rgb(180, 35, 35))
        val phone = EditText(this).apply { hint = "Phone number"; inputType = android.text.InputType.TYPE_CLASS_PHONE }
        val pin = EditText(this).apply { hint = "Rescuer code"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        root.addView(phone, matchWrap()); root.addView(pin, matchWrap())
        button("LOGIN", Color.rgb(5, 93, 55)) {
            val p = phone.text.toString().trim(); val code = pin.text.toString().trim()
            if (p.isBlank() || code.isBlank()) return@button toast("Enter phone number and rescuer code.")
            it.isEnabled = false
            lifecycleScope.launch {
                runCatching { api.login(p, code) }.onSuccess { result -> session.saveDeviceToken(result.token); applyStatus(result.status); startStatusPolling() }
                    .onFailure { e -> toast(e.message ?: "Login failed"); it.isEnabled = true }
            }
        }
        text("Your device must be approved by Rescue Command before mission actions are enabled. GPS is not started by login.", 13f)
    }

    private fun restoreSession(token: String) {
        root.removeAllViews(); title("Nepal Scouts Rescuer"); text("Restoring secure device session…", 15f)
        lifecycleScope.launch { runCatching { api.status(token) }.onSuccess { applyStatus(it); startStatusPolling() }.onFailure { session.clear(); showLogin("Session expired. Please log in again.") } }
    }

    private fun applyStatus(status: RescueStatus) {
        current = status; session.setMission(status.activeMissionId)
        if (status.operationalStatus !in setOf("on_mission", "returning") && session.trackingActive()) TrackingController.stop(this)
        renderHome()
    }

    private fun renderHome() {
        val s = current ?: return
        root.removeAllViews(); title("Nepal Scouts Rescuer"); text(s.rescuerName, 20f)
        text(listOfNotNull(s.teamName, s.incidentName).joinToString(" · ").ifBlank { "No active assignment" }, 14f)
        statusCard("OPERATIONAL", s.operationalStatus.replace('_', ' ').uppercase(), Color.rgb(18, 89, 160))
        val safetyColor = if (s.sosStatus == "raised" || s.safetyStatus == "sos") Color.rgb(190, 32, 38) else Color.rgb(5, 110, 68)
        statusCard("SAFETY", if (s.sosStatus == "raised") "SOS RAISED" else s.safetyStatus.replace('_', ' ').uppercase(), safetyColor)
        statusCard("GPS", if (session.trackingActive()) "TRACKING ACTIVE" else "TRACKING STOPPED", if (session.trackingActive()) Color.rgb(5, 110, 68) else Color.DKGRAY)

        if (s.approvalStatus != "approved") {
            text("Device status: ${s.approvalStatus.uppercase()}. Rescue Command approval is required before field actions.", 15f, Color.rgb(180, 85, 0))
            button("REFRESH APPROVAL", Color.rgb(5, 93, 55)) { refreshStatus() }; button("LOG OUT", Color.DKGRAY) { logout() }; return
        }

        s.task?.let { text("Task: $it", 15f) }; s.safeDueAt?.let { text("Next SAFE check: $it", 13f) }
        val lowData = Switch(this).apply { text = "Low Data Mode"; isChecked = session.lowData(); setOnCheckedChangeListener { _, checked -> session.setLowData(checked) } }
        root.addView(lowData, matchWrap()); text("Low Data keeps GPS recording locally and batches uploads. Maps and media stay on-demand.", 12f)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }; root.addView(actions, matchWrap())
        primaryButton(actions, if (s.operationalStatus == "on_mission") "MISSION ACTIVE" else "START MISSION", Color.rgb(5, 110, 68), s.operationalStatus != "on_mission") { startMission() }
        primaryButton(actions, "MAP", Color.rgb(18, 89, 160), true) { startActivity(Intent(this, MapActivity::class.java)) }
        primaryButton(actions, "CHECK IN", Color.rgb(18, 89, 160), true) { showCheckIn() }
        primaryButton(actions, "MESSAGE", Color.rgb(18, 89, 160), true) { showMessage() }
        sosHoldButton(actions)

        button("MISSION DETAILS", Color.rgb(18, 89, 160)) { startActivity(Intent(this, MissionActivity::class.java)) }
        button("ACTIVITY TIMELINE", Color.rgb(18, 89, 160)) { startActivity(Intent(this, ActivityTimelineActivity::class.java)) }
        button("OFFLINE SYNC / QUEUE", Color.rgb(75, 88, 100)) { startActivity(Intent(this, OfflineSyncActivity::class.java)) }
        if (s.operationalStatus == "on_mission") {
            button("I AM SAFE", Color.rgb(5, 110, 68)) { sendEvent("safe", "SAFE confirmed") }
            button("RETURNING", Color.rgb(194, 120, 0)) { sendEvent("returning", "Returning") }
        } else if (s.operationalStatus == "returning") button("END MISSION & STOP TRACKING", Color.rgb(120, 35, 35)) { sendEvent("end", "Mission ended") }
        button("REFRESH", Color.DKGRAY) { refreshStatus() }; button("LOG OUT", Color.DKGRAY) { logout() }
    }

    private fun startMission() {
        val token = session.deviceToken() ?: return showLogin()
        lifecycleScope.launch { runCatching { api.event(token, "start") }.onSuccess { status -> applyStatus(status); requestPermissionsAndStart() }.onFailure { toast(it.message ?: "Could not start mission") } }
    }

    private fun sendEvent(event: String, success: String) {
        val token = session.deviceToken() ?: return
        lifecycleScope.launch { runCatching { api.event(token, event) }.onSuccess { status -> applyStatus(status); toast(success) }.onFailure { toast(it.message ?: "Action failed") } }
    }

    private fun requestPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation) { permissions += Manifest.permission.ACCESS_FINE_LOCATION; permissions += Manifest.permission.ACCESS_COARSE_LOCATION }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions += Manifest.permission.POST_NOTIFICATIONS
        if (permissions.isNotEmpty()) { pendingStartAfterPermission = true; permissionLauncher.launch(permissions.toTypedArray()) } else startTrackingForCurrentMission()
    }

    private fun startTrackingForCurrentMission() {
        val mission = current?.activeMissionId ?: session.missionId()
        if (mission.isNullOrBlank()) return toast("No approved active mission available for GPS tracking.")
        TrackingController.start(this, mission, session.lowData()); toast("Foreground GPS tracking started"); renderHome()
    }

    private fun showCheckIn() {
        val choices = arrayOf("Reached location", "Need medical", "Need transport", "Need food/water", "Area searched", "Victim found")
        AlertDialog.Builder(this).setTitle("Quick Check-In").setItems(choices) { _, which ->
            if (session.deviceToken() == null) return@setItems
            val type = choices[which].lowercase().replace(' ', '_').replace('/', '_')
            lifecycleScope.launch {
                val payload = JSONObject().put("action", "checkin").put("type", type).put("captured_at", System.currentTimeMillis())
                OfflineActionQueue.enqueue(this@MainActivity, "checkin", payload)
                toast("Check-in saved on device and queued for sync")
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showMessage() {
        val input = EditText(this).apply { hint = "Message to Command"; minLines = 3 }
        AlertDialog.Builder(this).setTitle("Message Command").setView(input).setPositiveButton("Send") { _, _ ->
            val message = input.text.toString().trim()
            if (message.isBlank() || session.deviceToken() == null) return@setPositiveButton
            lifecycleScope.launch {
                val payload = JSONObject().put("action", "send_message").put("message", message).put("priority", "normal").put("captured_at", System.currentTimeMillis())
                OfflineActionQueue.enqueue(this@MainActivity, "message", payload)
                toast("Message saved on device and queued for sync")
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun sosHoldButton(parent: LinearLayout) {
        val button = Button(this).apply {
            text = "SOS — HOLD 3 SECONDS"; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(190, 32, 38)); minHeight = dp(72)
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { sosArmed = true; text = "KEEP HOLDING…"; sosHandler.postDelayed({ if (sosArmed) { sosArmed = false; text = "SOS SENT"; sendEvent("sos", "SOS raised — waiting for Command acknowledgement") } }, 3000); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> { if (sosArmed) { sosArmed = false; sosHandler.removeCallbacksAndMessages(null); text = "SOS — HOLD 3 SECONDS" }; v.performClick(); true }
                    else -> true
                }
            }
        }
        parent.addView(button, matchHeight(dp(82)))
    }

    private fun refreshStatus() { val token = session.deviceToken() ?: return showLogin(); lifecycleScope.launch { runCatching { api.status(token) }.onSuccess { applyStatus(it) }.onFailure { toast(it.message ?: "Could not refresh") } } }
    private fun startStatusPolling() { lifecycleScope.launch { while (isActive) { delay(if (session.lowData()) 120_000L else 30_000L); val token = session.deviceToken() ?: break; runCatching { api.status(token) }.onSuccess { applyStatus(it) } } } }
    private fun logout() { TrackingController.stop(this); session.clear(); showLogin() }

    private fun title(value: String) = root.addView(TextView(this).apply { text = value; textSize = 26f; setTextColor(Color.rgb(5, 93, 55)); setPadding(0, 0, 0, dp(8)) }, matchWrap())
    private fun text(value: String, size: Float, color: Int = Color.DKGRAY) = root.addView(TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(5)) }, matchWrap())
    private fun statusCard(label: String, value: String, color: Int) = root.addView(TextView(this).apply { text = "$label  ·  $value"; textSize = 15f; setTextColor(Color.WHITE); setBackgroundColor(color); setPadding(dp(12), dp(10), dp(12), dp(10)) }, matchHeight(dp(50)))
    private fun button(label: String, color: Int, click: (Button) -> Unit): Button = Button(this).apply { text = label; setTextColor(Color.WHITE); setBackgroundColor(color); minHeight = dp(52); setOnClickListener { click(this) }; root.addView(this, matchHeight(dp(60))) }
    private fun primaryButton(parent: LinearLayout, label: String, color: Int, enabled: Boolean, click: () -> Unit) = parent.addView(Button(this).apply { text = label; isEnabled = enabled; setTextColor(Color.WHITE); setBackgroundColor(color); minHeight = dp(66); setOnClickListener { click() } }, matchHeight(dp(74)))
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(4)) }
    private fun matchHeight(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { setMargins(0, dp(4), 0, dp(4)) }
}
