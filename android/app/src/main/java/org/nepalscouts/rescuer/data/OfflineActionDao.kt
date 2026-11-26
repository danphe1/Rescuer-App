package org.nepalscouts.rescuer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineActionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(action: OfflineAction)

    @Query("SELECT * FROM offline_actions WHERE state = 'pending' OR (state = 'failed' AND attempts < 8) ORDER BY capturedAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<OfflineAction>

    @Query("SELECT * FROM offline_actions ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<OfflineAction>

    @Query("UPDATE offline_actions SET state = 'synced', uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun markSynced(id: String, uploadedAt: Long)

    @Query("UPDATE offline_actions SET state = 'failed', attempts = attempts + 1 WHERE id = :id")
    suspend fun markFailed(id: String)

    @Query("SELECT COUNT(*) FROM offline_actions WHERE state != 'synced'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM offline_actions WHERE state = 'failed'")
    suspend fun failedCount(): Int

    @Query("DELETE FROM offline_actions WHERE state = 'synced' AND uploadedAt < :before")
    suspend fun pruneSynced(before: Long)
}
