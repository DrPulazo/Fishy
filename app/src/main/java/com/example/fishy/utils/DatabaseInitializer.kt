package com.example.fishy.utils

import android.content.Context
import com.example.fishy.database.AppDatabase
import com.example.fishy.database.entities.DictionaryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class DatabaseInitializer(private val context: Context) {

    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val dictionaryDao = database.dictionaryDao()

            // Добавляем начальные данные, если справочники пусты

            // Порты
            if (dictionaryDao.getItemsByType("port").first().isEmpty()) {
                val ports = listOf(
                    "ДКХ",
                    "ВМРП",
                    "ДРП",
                    "ДВ-Порт",
                    "Диомид",
                    "ХладЭко",
                    "ДВСК",
                    "Гудман",
                    "Пасифик-Фиш",
                    "ВРС",
                )
                ports.forEach { port ->
                    dictionaryDao.insertDictionaryItem(
                        DictionaryItem(
                            type = "port",
                            value = port,
                            lastUsed = System.currentTimeMillis(),
                            usageCount = 0
                        )
                    )
                }
            }

            // Заказчики
            if (dictionaryDao.getItemsByType("customer").first().isEmpty()) {
                val customers = listOf(
                    "Русский Рыбный Мир",
                    "ОкеанРыбФлот",
                    "Харвест-Фиш",
                    "ВладПоставка"
                )
                customers.forEach { customer ->
                    dictionaryDao.insertDictionaryItem(
                        DictionaryItem(
                            type = "customer",
                            value = customer,
                            lastUsed = System.currentTimeMillis(),
                            usageCount = 0
                        )
                    )
                }
            }

            // Продукция
            if (dictionaryDao.getItemsByType("product").first().isEmpty()) {
                val products = listOf(
                    "Горбуша",
                    "Кета",
                    "Нерка",
                    "Кижуч",
                    "Терпуг",
                    "Минтай",
                    "Треска",
                    "Сельдь"
                )
                products.forEach { product ->
                    dictionaryDao.insertDictionaryItem(
                        DictionaryItem(
                            type = "product",
                            value = product,
                            lastUsed = System.currentTimeMillis(),
                            usageCount = 0
                        )
                    )
                }
            }

            // Изготовители
            if (dictionaryDao.getItemsByType("manufacturer").first().isEmpty()) {
                val manufacturers = listOf(
                    "РА Олюторская",
                    "СРТМ Арктик Лидер",
                    "СРТМ Мыс Чупрова",
                    "РК им. Ленина"
                )
                manufacturers.forEach { manufacturer ->
                    dictionaryDao.insertDictionaryItem(
                        DictionaryItem(
                            type = "manufacturer",
                            value = manufacturer,
                            lastUsed = System.currentTimeMillis(),
                            usageCount = 0
                        )
                    )
                }
            }

            // Судна
            if (dictionaryDao.getItemsByType("vessel").first().isEmpty()) {
                val vessels = listOf(
                    "Бухта Наталии",
                    "Айс Винд",
                    "Капитан Мокеев",
                    "Камчатский пролив",
                    "Дионв"
                )
                vessels.forEach { vessel ->
                    dictionaryDao.insertDictionaryItem(
                        DictionaryItem(
                            type = "vessel",
                            value = vessel,
                            lastUsed = System.currentTimeMillis(),
                            usageCount = 0
                        )
                    )
                }
            }
        }
    }
}