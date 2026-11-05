package org.nepalscouts.rescuer.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import org.nepalscouts.rescuer.data.OfflineAction
import org.nepalscouts.rescuer.data.RescueDatabase
import java.util.UUID

object OfflineActionQueue {
    suspend fun enqueue(context: Context, type: String, payload: JSONObject): String {
        val id = payload.optString("client_event_id").ifBlank { UUID.randomUUID().toString() }
        val capturedAt = payload.optLong("captured_at", System.currentTimeMillis())
        payload.put("client_event_id", id)
        payload.put("captured_at", capturedAt)
        RescueDatabase.get(context).offlineActionDao().insert(
            OfflineAction(
                id = id,
                type = type,
                payload = payload.toString(),
                capturedAt = capturedAt
            )
        )
        schedule(context)
        return id
    }

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ActionSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "rescuer-field-action-sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
