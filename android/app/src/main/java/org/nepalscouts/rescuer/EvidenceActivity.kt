package org.nepalscouts.rescuer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import org.nepalscouts.rescuer.data.EvidenceItem
import org.nepalscouts.rescuer.data.RescueDatabase
import org.nepalscouts.rescuer.security.SecureSessionStore
import org.nepalscouts.rescuer.sync.EvidenceSyncWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class EvidenceActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private val session by lazy { SecureSessionStore(this) }
    private var pendingFile: File? = null
    private var pendingCategory: String = "incident"

    private val camera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = pendingFile
        if (result.resultCode == Activity.RESULT_OK && file != null && file.exists()) {
            lifecycleScope.launch {
                val latest = RescueDatabase.get(this@EvidenceActivity).locationDao().recent(1).firstOrNull()
                RescueDatabase.get(this@EvidenceActivity).evidenceDao().insert(
                    EvidenceItem(
                        id = UUID.randomUUID().toString(),
                        missionId = session.missionId(),
                        category = pendingCategory,
                        localPath = file.absolutePath,
                        capturedAt = System.currentTimeMillis(),
                        latitude = latest?.latitude,
                        longitude = latest?.longitude,
                        accuracy = latest?.accuracy
                    )
                )
                enqueueUpload()
                render(if (session.lowData()) "Evidence saved locally. Upload is restricted to an unmetered network in Low Data Mode." else "Evidence saved locally and queued for confirmed upload.")
            }
        } else {
            file?.delete()
            render("Capture cancelled.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(247, 249, 247))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        render(null)
    }

    private fun render(message: String?) {
        lifecycleScope.launch {
            val items = RescueDatabase.get(this@EvidenceActivity).evidenceDao().recent(50)
            root.removeAllViews()
            heading("Evidence")
            info("Photos are captured to app-controlled storage first. Upload status changes to UPLOADED only after the rescue backend confirms receipt.")
            if (!message.isNullOrBlank()) info(message)
            arrayOf("incident", "victim_location", "damage", "supplies").forEach { category ->
                root.addView(Button(this@EvidenceActivity).apply {
                    text = "CAPTURE ${category.replace('_', ' ').uppercase()} PHOTO"
                    setOnClickListener { startCapture(category) }
                }, match(dp(58)))
            }
            root.addView(Button(this@EvidenceActivity).apply {
                text = "SYNC PENDING EVIDENCE"
                setOnClickListener { enqueueUpload(); render("Evidence sync requested using the current data policy.") }
            }, match(dp(58)))
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            items.forEach { item ->
                root.addView(TextView(this@EvidenceActivity).apply {
                    text = "${item.category.replace('_', ' ').uppercase()} · ${item.state.uppercase()}\n${fmt.format(Date(item.capturedAt))}"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(if (item.state == "uploaded") Color.rgb(5, 110, 68) else Color.rgb(194, 120, 0))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }, match(dp(70)))
            }
            root.addView(Button(this@EvidenceActivity).apply { text = "BACK"; setOnClickListener { finish() } }, match(dp(56)))
        }
    }

    private fun enqueueUpload() {
        val network = if (session.lowData()) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<EvidenceSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        WorkManager.getInstance(this).enqueue(request)
    }

    private fun startCapture(category: String) {
        pendingCategory = category
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "evidence").apply { mkdirs() }
        val file = File(dir, "EV_${System.currentTimeMillis()}.jpg")
        pendingFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        camera.launch(intent)
    }

    private fun heading(value: String) = root.addView(TextView(this).apply { text = value; textSize = 26f; setTextColor(Color.rgb(5, 93, 55)); setPadding(0, 0, 0, dp(12)) })
    private fun info(value: String) = root.addView(TextView(this).apply { text = value; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(6), 0, dp(10)) })
    private fun match(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { setMargins(0, dp(4), 0, dp(4)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
