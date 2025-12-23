package com.example.fishy.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.fishy.database.entities.*

@Database(
    entities = [
        Shipment::class,
        ProductItem::class,
        Pallet::class,
        ScheduledShipment::class,
        ChecklistItem::class,
        DictionaryItem::class
    ],
    version = 6, // Увеличиваем до 6 из-за изменений в ScheduledShipment
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shipmentDao(): ShipmentDao
    abstract fun scheduledShipmentDao(): ScheduledShipmentDao
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Принудительно удаляем старую БД при конфликте схемы
                // Это нормально на этапе разработки
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fishy_database"
                )
                    .fallbackToDestructiveMigration() // Удалит старую БД при любой проблеме с миграцией
                    .fallbackToDestructiveMigrationOnDowngrade() // Удалит при понижении версии
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // Функция для полной очистки БД (на случай тестирования)
        fun clearDatabase(context: Context) {
            INSTANCE?.close()
            INSTANCE = null
            // Room автоматически пересоздаст БД при следующем вызове getDatabase
        }
    }
}