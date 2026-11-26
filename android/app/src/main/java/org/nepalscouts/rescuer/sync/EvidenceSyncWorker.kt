package org.nepalscouts.rescuer.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import org.nepalscouts.rescuer.data.RescueDatabase
import org.nepalscouts.rescuer.network.RescueApi
import org.nepalscouts.rescuer.security.SecureSessionStore
import java.io.ByteArrayOutputStream
import java.io.File

class EvidenceSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val token = SecureSessionStore(applicationContext).deviceToken() ?: return Result.failure()
        val dao = RescueDatabase.get(applicationContext).evidenceDao()
        val item = dao.pending(1).firstOrNull() ?: return Result.success()
        val file = File(item.localPath)
        if (!file.exists()) {
            dao.markFailed(item.id)
            return Result.success()
        }

        val encoded = compressForUpload(file) ?: return Result.retry()
        val payload = JSONObject()
            .put("action", "mission_photo")
            .put("photo_data_url", "data:image/jpeg;base64,$encoded")
            .put("caption", item.category.replace('_', ' '))
            .put("captured_at", item.capturedAt)
        item.latitude?.let { payload.put("latitude", it) }
        item.longitude?.let { payload.put("longitude", it) }
        item.accuracy?.let { payload.put("accuracy", it) }

        return runCatching { RescueApi().postRaw(token, payload) }
            .fold(
                onSuccess = {
                    dao.markUploaded(item.id, System.currentTimeMillis())
                    if (dao.pending(1).isNotEmpty()) Result.retry() else Result.success()
                },
                onFailure = { Result.retry() }
            )
    }

    private fun compressForUpload(file: File): String? {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val maxSide = 1280
        val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height).toFloat())
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true) else original
        var quality = 72
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 8
        } while (bytes.size > 520_000 && quality >= 40)
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
        if (bytes.size > 560_000) return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
