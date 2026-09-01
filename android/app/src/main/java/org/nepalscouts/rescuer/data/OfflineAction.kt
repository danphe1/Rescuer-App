package org.nepalscouts.rescuer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_actions")
data class OfflineAction(
    @PrimaryKey val id: String,
    val type: String,
    val payload: String,
    val capturedAt: Long,
    val state: String = "pending",
    val attempts: Int = 0,
    val uploadedAt: Long? = null
)
