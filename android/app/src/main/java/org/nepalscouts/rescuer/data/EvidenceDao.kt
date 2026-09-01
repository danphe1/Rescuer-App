package org.nepalscouts.rescuer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: EvidenceItem)

    @Query("SELECT * FROM evidence_items ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<EvidenceItem>

    @Query("SELECT COUNT(*) FROM evidence_items WHERE state != 'uploaded'")
    suspend fun pendingCount(): Int

    @Query("UPDATE evidence_items SET state = 'uploaded', uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun markUploaded(id: String, uploadedAt: Long)
}
