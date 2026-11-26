package org.nepalscouts.rescuer.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.nepalscouts.rescuer.BuildConfig
import org.nepalscouts.rescuer.data.RescueDatabase
import org.nepalscouts.rescuer.security.SecureSessionStore
import java.util.concurrent.TimeUnit

class ActionSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val token = SecureSessionStore(applicationContext).deviceToken() ?: return Result.failure()
        val dao = RescueDatabase.get(applicationContext).offlineActionDao()
        val actions = dao.pending(25)
        if (actions.isEmpty()) return Result.success()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        var retryNeeded = false
        for (action in actions) {
            val payload = runCatching { JSONObject(action.payload) }.getOrNull()
            if (payload == null) {
                dao.markFailed(action.id)
                continue
            }
            val request = Request.Builder()
                .url(BuildConfig.TRACKER_API_URL)
                .header("x-device-token", token)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        dao.markSynced(action.id, System.currentTimeMillis())
                    } else {
                        dao.markFailed(action.id)
                        if (response.code == 408 || response.code == 429 || response.code >= 500) retryNeeded = true
                    }
                }
            } catch (_: Exception) {
                dao.markFailed(action.id)
                retryNeeded = true
            }
        }

        dao.pruneSynced(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
        return if (retryNeeded) Result.retry() else Result.success()
    }
}
