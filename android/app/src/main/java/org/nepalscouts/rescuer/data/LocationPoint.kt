package org.nepalscouts.rescuer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_points")
data class LocationPoint(
    @PrimaryKey val id: String,
    val missionId: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val battery: Int?,
    val capturedAt: Long,
    val uploadedAt: Long? = null,
    val state: String = "pending",
    val attempts: Int = 0
)
