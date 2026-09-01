package org.nepalscouts.rescuer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LocationPoint::class], version = 1, exportSchema = true)
abstract class RescueDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile private var instance: RescueDatabase? = null
        fun get(context: Context): RescueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RescueDatabase::class.java,
                "rescuer-offline.db"
            ).build().also { instance = it }
        }
    }
}
