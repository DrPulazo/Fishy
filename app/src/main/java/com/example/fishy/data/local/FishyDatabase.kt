package com.example.fishy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.fishy.data.local.dao.DictionaryDao
import com.example.fishy.data.local.dao.EventDao
import com.example.fishy.data.local.dao.ReportTemplateDao
import com.example.fishy.data.local.dao.ScheduledShipmentDao
import com.example.fishy.data.local.dao.ShipmentDao
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.data.local.entity.ReportTemplateEntity
import com.example.fishy.data.local.entity.ScheduledReminderEntity
import com.example.fishy.data.local.entity.ScheduledShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEventEntity

@Database(
    entities = [
        ShipmentEntity::class,
        ScheduledShipmentEntity::class,
        ChecklistItemEntity::class,
        ScheduledReminderEntity::class,
        DictionaryEntity::class,
        ShipmentEventEntity::class,
        ReportTemplateEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FishyDatabase : RoomDatabase() {
    abstract fun shipmentDao(): ShipmentDao
    abstract fun scheduledShipmentDao(): ScheduledShipmentDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun eventDao(): EventDao
    abstract fun reportTemplateDao(): ReportTemplateDao

    companion object {
        @Volatile
        private var instance: FishyDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scheduled_shipments ADD COLUMN startNotificationSent INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE dictionary_items ADD COLUMN lastUsedAtMillis INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scheduled_reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduledShipmentId INTEGER NOT NULL,
                        atMillis INTEGER NOT NULL,
                        sent INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO scheduled_reminders (scheduledShipmentId, atMillis, sent, sortOrder)
                    SELECT id, notificationAtMillis, notificationSent, 0
                    FROM scheduled_shipments
                    WHERE notificationAtMillis IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE scheduled_shipments ADD COLUMN linkedDraftId INTEGER DEFAULT NULL"
                )
            }
        }

        fun get(context: Context): FishyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FishyDatabase::class.java,
                    "fishy_v2.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
