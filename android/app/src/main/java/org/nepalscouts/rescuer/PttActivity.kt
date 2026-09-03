package org.nepalscouts.rescuer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.nepalscouts.rescuer.ptt.PttAudioService
import org.nepalscouts.rescuer.ptt.PttRepository
import javax.inject.Inject

@AndroidEntryPoint
class PttActivity : AppCompatActivity() {
    @Inject lateinit var ptt: PttRepository

    private lateinit var root: LinearLayout
    private lateinit var stateText: TextView
    private lateinit var incomingText: TextView
    private lateinit var teamButton: Button
    private lateinit var commandButton: Button
    private var teamName: String = ""
    private var teamChannel: String = ""
    private val commandChannel = BuildConfig.PTT_COMMAND_CHANNEL

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] != true && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Microphone permission is required for push-to-talk.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        teamName = intent.getStringExtra(PttAudioService.EXTRA_TEAM).orEmpty()
        teamChannel = intent.getStringExtra(EXTRA_TEAM_CHANNEL).orEmpty().ifBlank {
            if (teamName.isBlank()) "RescueGrid Team" else "RescueGrid Team - $teamName"
        }
        buildUi()
        requestPermissions()
        ptt.start()
        observeState()
        if (!ptt.state.value.connected) connectOrPrompt()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(30))
            setBackgroundColor(Color.rgb(246, 248, 251))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply {
            text = "RescueGrid PTT"
            textSize = 27f
            setTextColor(Color.rgb(6, 59, 120))
        })
        root.addView(TextView(this).apply {
            text = "Team voice + Command voice"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(12))
        })

        stateText = TextView(this).apply {
            text = "Starting voice service…"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 88, 100))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(stateText, full(dp(58)))

        incomingText = TextView(this).apply {
            text = "No incoming voice"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(5, 110, 68))
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        root.addView(incomingText, full(dp(64)))

        info("TEAM CHANNEL", teamChannel)
        teamButton = pttButton("HOLD TO TALK — TEAM", Color.rgb(7, 89, 168), teamChannel)
        root.addView(teamButton, full(dp(100)))

        info("COMMAND CHANNEL", commandChannel)
        commandButton = pttButton("HOLD TO TALK — COMMAND", Color.rgb(190, 32, 38), commandChannel)
        root.addView(commandButton, full(dp(100)))

        root.addView(Button(this).apply {
            text = "PTT ACCOUNT / CONNECTION"
            setOnClickListener { showConnectionDialog() }
        }, full(dp(58)))
        root.addView(TextView(this).apply {
            text = "Keep RescueGrid running to hear live Team and Command audio. Background audio uses a foreground service so Android does not silently stop rescue communications."
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(10), 0, dp(8))
        })
    }

    private fun pttButton(label: String, color: Int, channel: String) = Button(this).apply {
        text = label
        textSize = 17f
        setTextColor(Color.WHITE)
        setBackgroundColor(color)
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!ensureReady(channel)) return@setOnTouchListener true
                    text = "TRANSMITTING… RELEASE TO STOP"
                    ptt.pressToTalk(channel)
                    updateBackground("Transmitting on $channel")
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    ptt.releaseToStop()
                    text = label
                    updateBackground("Listening for Team and Command PTT")
                    v.performClick()
                    true
                }
                else -> true
            }
        }
    }

    private fun ensureReady(channel: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(); toast("Allow microphone access, then hold PTT again."); return false
        }
        if (!ptt.state.value.connected) {
            connectOrPrompt(); toast("Connect the PTT account first."); return false
        }
        if (ptt.findChannel(channel) == null) {
            toast("Channel '$channel' is not available for this Zello Work account.")
            return false
        }
        return true
    }

    private fun observeState() {
        lifecycleScope.launch {
            ptt.state.collect { s ->
                stateText.text = when {
                    s.transmitting -> "LIVE — TRANSMITTING ${s.selectedChannel.orEmpty()}"
                    s.connecting -> "CONNECTING TO PTT NETWORK…"
                    s.connected -> "PTT ONLINE · ${s.channels.size} channels available"
                    else -> s.error ?: "PTT OFFLINE"
                }
                stateText.setBackgroundColor(
                    when {
                        s.transmitting -> Color.rgb(190, 32, 38)
                        s.connected -> Color.rgb(5, 110, 68)
                        else -> Color.rgb(120, 85, 0)
                    }
                )
                incomingText.text = if (s.incoming) "🔊 INCOMING VOICE — ${s.incomingFrom ?: "PTT"}" else "Listening for incoming Team / Command voice"
                incomingText.setBackgroundColor(if (s.incoming) Color.rgb(194, 120, 0) else Color.rgb(5, 110, 68))
                teamButton.isEnabled = s.connected && !s.incoming
                commandButton.isEnabled = s.connected && !s.incoming
                if (s.connected) updateBackground(if (s.incoming) "Incoming voice from ${s.incomingFrom ?: "PTT"}" else "Listening for Team and Command PTT")
            }
        }
    }

    private fun connectOrPrompt() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val network = prefs.getString("network", "").orEmpty()
        val user = prefs.getString("user", "").orEmpty()
        val pass = prefs.getString("password", "").orEmpty()
        if (network.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) ptt.connect(network, user, pass) else showConnectionDialog()
    }

    private fun showConnectionDialog() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0) }
        val network = EditText(this).apply { hint = "Zello Work network"; setText(prefs.getString("network", "")) }
        val user = EditText(this).apply { hint = "PTT username"; setText(prefs.getString("user", "")) }
        val pass = EditText(this).apply { hint = "PTT password"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(network); box.addView(user); box.addView(pass)
        AlertDialog.Builder(this)
            .setTitle("Connect RescueGrid PTT")
            .setMessage("Use the Zello Work account assigned to this responder. Credentials remain on this device for v1.")
            .setView(box)
            .setPositiveButton("Connect") { _, _ ->
                val n = network.text.toString().trim(); val u = user.text.toString().trim(); val p = pass.text.toString()
                if (n.isBlank() || u.isBlank() || p.isBlank()) return@setPositiveButton toast("Network, username and password are required.")
                prefs.edit().putString("network", n).putString("user", u).putString("password", p).apply()
                ptt.connect(n, u, p)
            }
            .setNeutralButton("Disconnect") { _, _ -> ptt.disconnect() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun updateBackground(state: String) {
        val intent = Intent(this, PttAudioService::class.java)
            .putExtra(PttAudioService.EXTRA_TEAM, teamName)
            .putExtra(PttAudioService.EXTRA_STATE, state)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun info(label: String, value: String) {
        root.addView(TextView(this).apply {
            text = "$label\n$value"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(14), 0, dp(6))
        })
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun full(h: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h).apply { setMargins(0, dp(5), 0, dp(5)) }

    companion object {
        const val EXTRA_TEAM_CHANNEL = "ptt_team_channel"
        private const val PREFS = "rescuegrid_ptt_v1"
    }
}
