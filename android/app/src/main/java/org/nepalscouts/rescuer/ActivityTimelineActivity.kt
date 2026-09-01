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
import org.json.JSONObject
import org.nepalscouts.rescuer.data.RescueDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityTimelineActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(247, 249, 247))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        render()
    }

    private fun render() {
        lifecycleScope.launch {
            val items = RescueDatabase.get(this@ActivityTimelineActivity).offlineActionDao().recent(100)
            root.removeAllViews()
            heading("Activity Timeline")
            info("This timeline shows actions captured on this phone and their sync state. Server-side Command history will be merged when the backend exposes the responder event-history endpoint.")
            if (items.isEmpty()) info("No local activity recorded yet.")
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            items.forEach { item ->
                val payload = runCatching { JSONObject(item.payload) }.getOrNull()
                val detail = when (item.type) {
                    "checkin" -> payload?.optString("type")?.replace('_', ' ') ?: "check-in"
                    "message" -> payload?.optString("message")?.take(90) ?: "message"
                    else -> item.type
                }
                val statusColor = when (item.state) {
                    "synced" -> Color.rgb(5, 110, 68)
                    "failed" -> Color.rgb(180, 35, 35)
                    else -> Color.rgb(194, 120, 0)
                }
                root.addView(TextView(this@ActivityTimelineActivity).apply {
                    text = "${item.type.uppercase()} · ${item.state.uppercase()}\n$detail\n${fmt.format(Date(item.capturedAt))}"
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(statusColor)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }, match(dp(88)))
            }
            root.addView(Button(this@ActivityTimelineActivity).apply { text = "BACK"; setOnClickListener { finish() } }, match(dp(56)))
        }
    }

    private fun heading(value: String) = root.addView(TextView(this).apply { text = value; textSize = 26f; setTextColor(Color.rgb(5, 93, 55)); setPadding(0, 0, 0, dp(12)) })
    private fun info(value: String) = root.addView(TextView(this).apply { text = value; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(6), 0, dp(10)) })
    private fun match(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { setMargins(0, dp(4), 0, dp(4)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
