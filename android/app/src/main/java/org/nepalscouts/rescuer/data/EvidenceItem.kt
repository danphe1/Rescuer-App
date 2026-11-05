package org.nepalscouts.rescuer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evidence_items")
data class EvidenceItem(
    @PrimaryKey val id: String,
    val missionId: String?,
    val category: String,
    val localPath: String,
    val capturedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val state: String = "pending_upload",
    val uploadedAt: Long? = null
)
