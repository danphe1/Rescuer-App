package org.nepalscouts.rescuer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocationPoint::class, OfflineAction::class], version = 2, exportSchema = true)
abstract class RescueDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun offlineActionDao(): OfflineActionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS offline_actions (id TEXT NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL, capturedAt INTEGER NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, uploadedAt INTEGER, PRIMARY KEY(id))"
                )
            }
        }

        @Volatile private var instance: RescueDatabase? = null
        fun get(context: Context): RescueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RescueDatabase::class.java,
                "rescuer-offline.db"
            ).addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
