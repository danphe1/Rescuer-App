package org.nepalscouts.rescuer

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.nepalscouts.rescuer.network.RescueApi
import org.nepalscouts.rescuer.security.SecureSessionStore

class MissionActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private val session by lazy { SecureSessionStore(this) }
    private val api = RescueApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(247, 249, 247))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        load()
    }

    private fun load() {
        val token = session.deviceToken()
        root.removeAllViews(); heading("Mission")
        if (token.isNullOrBlank()) { info("No active device session."); back(); return }
        info("Loading current assignment…")
        lifecycleScope.launch {
            runCatching { api.status(token) }
                .onSuccess { s ->
                    root.removeAllViews(); heading("Mission")
                    row("Rescuer", s.rescuerName)
                    row("Incident", s.incidentName ?: "Not assigned")
                    row("Mission ID", s.activeMissionId ?: "No active mission")
                    row("Team", s.teamName ?: "Not assigned")
                    row("Task", s.task ?: "No task supplied")
                    row("Operational", s.operationalStatus.replace('_', ' ').uppercase())
                    row("Safety", s.safetyStatus.replace('_', ' ').uppercase())
                    row("SOS", s.sosStatus.uppercase())
                    row("Next SAFE", s.safeDueAt ?: "Not scheduled")
                    info("Meeting point, safe route, emergency contacts, members and optional vehicle will appear here when those fields are supplied by the assignment API. This screen does not invent missing assignment data.")
                    back()
                }
                .onFailure { root.removeAllViews(); heading("Mission"); info(it.message ?: "Could not load mission"); back() }
        }
    }

    private fun heading(value: String) = root.addView(TextView(this).apply { text = value; textSize = 26f; setTextColor(Color.rgb(5, 93, 55)); setPadding(0, 0, 0, dp(12)) })
    private fun row(label: String, value: String) = root.addView(TextView(this).apply { text = "$label\n$value"; textSize = 16f; setTextColor(Color.DKGRAY); setBackgroundColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10)) }, match(dp(66)))
    private fun info(value: String) = root.addView(TextView(this).apply { text = value; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(8), 0, dp(10)) })
    private fun back() = root.addView(Button(this).apply { text = "BACK"; setOnClickListener { finish() } }, match(dp(56)))
    private fun match(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { setMargins(0, dp(4), 0, dp(4)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
