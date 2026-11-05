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

class CommandInboxActivity : AppCompatActivity() {
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
        root.removeAllViews(); heading("Command Inbox")
        if (token.isNullOrBlank()) { info("No secure device session."); back(); return }
        info("Loading unread Command messages…")
        lifecycleScope.launch {
            runCatching { api.status(token) }
                .onSuccess { status ->
                    root.removeAllViews(); heading("Command Inbox")
                    if (status.messages.isEmpty()) info("No unread Command messages.")
                    status.messages.forEach { m ->
                        val urgent = m.priority.equals("urgent", true) || m.priority.equals("high", true)
                        root.addView(TextView(this@CommandInboxActivity).apply {
                            text = "${if (urgent) "URGENT · " else ""}${m.message}\n${m.createdAt ?: ""}"
                            textSize = 16f
                            setTextColor(Color.WHITE)
                            setBackgroundColor(if (urgent) Color.rgb(190, 32, 38) else Color.rgb(18, 89, 160))
                            setPadding(dp(12), dp(12), dp(12), dp(12))
                        }, match(dp(90)))
                        root.addView(Button(this@CommandInboxActivity).apply {
                            text = "ACKNOWLEDGE"
                            setOnClickListener {
                                isEnabled = false
                                lifecycleScope.launch {
                                    runCatching { api.acknowledgeMessage(token, m.id) }
                                        .onSuccess { load() }
                                        .onFailure { isEnabled = true; info(it.message ?: "Acknowledgement failed") }
                                }
                            }
                        }, match(dp(54)))
                    }
                    back()
                }
                .onFailure { root.removeAllViews(); heading("Command Inbox"); info(it.message ?: "Could not load messages"); back() }
        }
    }

    private fun heading(value: String) = root.addView(TextView(this).apply { text = value; textSize = 26f; setTextColor(Color.rgb(5, 93, 55)); setPadding(0, 0, 0, dp(12)) })
    private fun info(value: String) = root.addView(TextView(this).apply { text = value; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(6), 0, dp(10)) })
    private fun back() = root.addView(Button(this).apply { text = "BACK"; setOnClickListener { finish() } }, match(dp(56)))
    private fun match(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { setMargins(0, dp(4), 0, dp(4)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
