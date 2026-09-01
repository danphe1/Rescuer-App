package org.nepalscouts.rescuer.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import org.nepalscouts.rescuer.BuildConfig
import org.nepalscouts.rescuer.data.RescueDatabase
import org.nepalscouts.rescuer.security.SecureSessionStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class LocationSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private fun isoUtc(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))

    override suspend fun doWork(): Result {
        val session = SecureSessionStore(applicationContext)
        val token = session.deviceToken() ?: return Result.failure()
        val dao = RescueDatabase.get(applicationContext).locationDao()
        val batchSize = if (session.lowData()) 80 else 40
        val points = dao.pending(batchSize)
        if (points.isEmpty()) return Result.success()

        val payload = JSONObject().put("action", "location").put("event", "location")
        val array = JSONArray()
        points.forEach { p ->
            array.put(JSONObject()
                .put("latitude", p.latitude)
                .put("longitude", p.longitude)
                .put("accuracy", p.accuracy)
                .put("battery", p.battery)
                .put("recorded_at", isoUtc(p.capturedAt))
                .put("offline", true)
                .put("client_event_id", p.id))
        }
        payload.put("points", array)

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(BuildConfig.TRACKER_API_URL)
            .header("x-device-token", token)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    dao.markSynced(points.map { it.id }, System.currentTimeMillis())
                    dao.pruneSynced(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
                    Result.success()
                } else {
                    dao.markFailed(points.map { it.id })
                    if (response.code in 400..499 && response.code != 408 && response.code != 429) Result.failure() else Result.retry()
                }
            }
        } catch (_: Exception) {
            dao.markFailed(points.map { it.id })
            Result.retry()
        }
    }
}
