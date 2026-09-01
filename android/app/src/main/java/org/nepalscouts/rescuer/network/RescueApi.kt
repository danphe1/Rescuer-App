package org.nepalscouts.rescuer.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.nepalscouts.rescuer.BuildConfig
import java.util.concurrent.TimeUnit

data class CommandMessage(
    val id: String,
    val message: String,
    val priority: String,
    val createdAt: String?,
    val deliveredAt: String?,
    val readAt: String?
)

data class RescueStatus(
    val rescuerName: String,
    val approvalStatus: String,
    val operationalStatus: String,
    val safetyStatus: String,
    val sosStatus: String,
    val activeMissionId: String?,
    val teamName: String?,
    val incidentName: String?,
    val task: String?,
    val safeDueAt: String?,
    val messages: List<CommandMessage> = emptyList()
)

data class LoginResult(val token: String, val status: RescueStatus)

class RescueApi {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    suspend fun login(phone: String, pin: String): LoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("action", "login").put("phone", phone.trim()).put("pin", pin.trim()).put("device_label", "Android Rescuer")
        val json = post(body, null)
        val token = json.optString("device_token")
        if (token.isBlank()) throw IllegalStateException(json.optString("error", "Login failed"))
        LoginResult(token, parseStatus(json))
    }

    suspend fun status(token: String): RescueStatus = withContext(Dispatchers.IO) { parseStatus(post(JSONObject().put("action", "status"), token)) }

    suspend fun event(token: String, event: String, note: String? = null): RescueStatus = withContext(Dispatchers.IO) {
        val body = JSONObject().put("action", event).put("event", event)
        if (!note.isNullOrBlank()) body.put("note", note)
        parseStatus(post(body, token))
    }

    suspend fun acknowledgeMessage(token: String, id: String) = withContext(Dispatchers.IO) {
        post(JSONObject().put("action", "read_message").put("message_id", id), token)
    }

    suspend fun postRaw(token: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) { post(body, token) }

    private fun post(body: JSONObject, token: String?): JSONObject {
        val builder = Request.Builder().url(BuildConfig.TRACKER_API_URL).post(body.toString().toRequestBody("application/json".toMediaType()))
        if (!token.isNullOrBlank()) builder.header("x-device-token", token)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", "Invalid server response") }
            if (!response.isSuccessful || json.optBoolean("ok", true).not()) throw IllegalStateException(json.optString("error", "Request failed (${response.code})"))
            return json
        }
    }

    private fun parseStatus(json: JSONObject): RescueStatus {
        val team = json.optJSONObject("team")
        val assignment = json.optJSONObject("assignment")
        val operational = json.optString("operational_status").ifBlank {
            when (json.optString("mission_status")) {
                "active" -> "on_mission"
                "returning" -> "returning"
                "ended" -> "off_duty"
                else -> "available"
            }
        }
        val messages = buildList {
            val arr = json.optJSONArray("messages")
            if (arr != null) for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                add(CommandMessage(
                    id = m.optString("id"),
                    message = m.optString("message"),
                    priority = m.optString("priority", "normal"),
                    createdAt = m.optString("created_at").takeIf { it.isNotBlank() && it != "null" },
                    deliveredAt = m.optString("delivered_at").takeIf { it.isNotBlank() && it != "null" },
                    readAt = m.optString("read_at").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        }
        return RescueStatus(
            rescuerName = json.optString("rescuer_name", "Rescuer"),
            approvalStatus = json.optString("approval_status", "pending"),
            operationalStatus = operational,
            safetyStatus = json.optString("safety_status", "safe_confirmed"),
            sosStatus = json.optString("sos_status", "none"),
            activeMissionId = json.optString("active_mission_id").takeIf { it.isNotBlank() && it != "null" },
            teamName = team?.optString("name")?.takeIf { it.isNotBlank() },
            incidentName = assignment?.optString("incident_name")?.takeIf { it.isNotBlank() },
            task = assignment?.optString("task")?.takeIf { it.isNotBlank() },
            safeDueAt = json.optString("safe_due_at").takeIf { it.isNotBlank() && it != "null" },
            messages = messages
        )
    }
}
