package org.nepalscouts.rescuer

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import org.nepalscouts.rescuer.data.RescueDatabase
import org.nepalscouts.rescuer.security.SecureSessionStore
import org.nepalscouts.rescuer.sync.LocationSyncWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfflineSyncActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private val session by lazy { SecureSessionStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(247, 249, 247))
        }
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val dao = RescueDatabase.get(this@OfflineSyncActivity).locationDao()
            val pending = dao.pendingCount()
            val failed = dao.failedCount()
            val latest = dao.latestCapturedAt()
            root.removeAllViews()
            heading("Offline Sync")
            line("GPS capture", if (session.trackingActive()) "ACTIVE" else "STOPPED", if (session.trackingActive()) Color.rgb(5, 110, 68) else Color.DKGRAY)
            line("Queued GPS points", pending.toString(), if (pending > 0) Color.rgb(194, 120, 0) else Color.rgb(5, 110, 68))
            line("Retry-needed points", failed.toString(), if (failed > 0) Color.rgb(180, 35, 35) else Color.rgb(5, 110, 68))
            line("Low Data Mode", if (session.lowData()) "ON" else "OFF", Color.rgb(18, 89, 160))
            line("Last local GPS", latest?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it)) } ?: "No point captured yet", Color.DKGRAY)
            info("GPS is stored on this device before upload. A queued point is not the same as tracking lost. Original capture time is preserved when the point syncs later.")
            val sync = Button(this@OfflineSyncActivity).apply {
                text = "SYNC NOW"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(5, 93, 55))
                setOnClickListener {
                    val request = OneTimeWorkRequestBuilder<LocationSyncWorker>().build()
                    WorkManager.getInstance(this@OfflineSyncActivity).enqueueUniqueWork("manual-location-sync", ExistingWorkPolicy.REPLACE, request)
                    postDelayed({ refresh() }, 1200)
                }
            }
            root.addView(sync, match(dp(60)))
            val close = Button(this@OfflineSyncActivity).apply { text = "BACK"; setOnClickListener { finish() } }
            root.addView(close, match(dp(56)))
        }
    }

    private fun heading(value: String) = root.addView(TextView(this).apply { text = value; textSize = 26f; setTextColor(Color.rgb(5, 93, 55)); setPadding(0, 0, 0, dp(12)) })
    private fun line(label: String, value: String, color: Int) = root.addView(TextView(this).apply { text = "$label  ·  $value"; textSize = 16f; setTextColor(Color.WHITE); setBackgroundColor(color); setPadding(dp(12), dp(12), dp(12), dp(12)) }, match(dp(54)))
    private fun info(value: String) = root.addView(TextView(this).apply { text = value; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(12), 0, dp(12)) })
    private fun match(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { setMargins(0, dp(4), 0, dp(4)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
