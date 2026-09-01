package org.nepalscouts.rescuer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: LocationPoint)

    @Query("SELECT * FROM location_points WHERE state != 'synced' ORDER BY capturedAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<LocationPoint>

    @Query("SELECT * FROM location_points ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<LocationPoint>

    @Query("UPDATE location_points SET state = 'synced', uploadedAt = :uploadedAt WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, uploadedAt: Long)

    @Query("UPDATE location_points SET state = 'failed', attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<String>)

    @Query("SELECT COUNT(*) FROM location_points WHERE state != 'synced'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM location_points WHERE state = 'failed'")
    suspend fun failedCount(): Int

    @Query("SELECT MAX(capturedAt) FROM location_points")
    suspend fun latestCapturedAt(): Long?

    @Query("DELETE FROM location_points WHERE state = 'synced' AND uploadedAt < :before")
    suspend fun pruneSynced(before: Long)
}
