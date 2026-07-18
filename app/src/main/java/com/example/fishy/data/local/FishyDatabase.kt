package com.example.fishy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.fishy.data.local.dao.DictionaryDao
import com.example.fishy.data.local.dao.EventDao
import com.example.fishy.data.local.dao.ReportTemplateDao
import com.example.fishy.data.local.dao.ScheduledShipmentDao
import com.example.fishy.data.local.dao.ShipmentDao
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.data.local.entity.ReportTemplateEntity
import com.example.fishy.data.local.entity.ScheduledShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEventEntity

@Database(
    entities = [
        ShipmentEntity::class,
        ScheduledShipmentEntity::class,
        ChecklistItemEntity::class,
        DictionaryEntity::class,
        ShipmentEventEntity::class,
        ReportTemplateEntity::class
    ],
    version = 1,
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

        fun get(context: Context): FishyDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FishyDatabase::class.java,
                    "fishy_v2.db"
                ).build().also { instance = it }
            }
        }
    }
}
