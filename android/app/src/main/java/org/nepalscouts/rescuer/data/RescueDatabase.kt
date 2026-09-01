package org.nepalscouts.rescuer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LocationPoint::class, OfflineAction::class, EvidenceItem::class], version = 3, exportSchema = true)
abstract class RescueDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun offlineActionDao(): OfflineActionDao
    abstract fun evidenceDao(): EvidenceDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS offline_actions (id TEXT NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL, capturedAt INTEGER NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, uploadedAt INTEGER, PRIMARY KEY(id))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS evidence_items (id TEXT NOT NULL, missionId TEXT, category TEXT NOT NULL, localPath TEXT NOT NULL, capturedAt INTEGER NOT NULL, latitude REAL, longitude REAL, accuracy REAL, state TEXT NOT NULL, uploadedAt INTEGER, PRIMARY KEY(id))"
                )
            }
        }

        @Volatile private var instance: RescueDatabase? = null
        fun get(context: Context): RescueDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RescueDatabase::class.java,
                "rescuer-offline.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
