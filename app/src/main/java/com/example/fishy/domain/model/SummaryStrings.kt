package com.example.fishy.domain.model

import android.content.res.Resources
import com.example.fishy.R

/** Localized copy for schedule/archive summary lines. */
data class SummaryStrings(
    val tonnageFmt: String,
    val portFmt: String,
    val portNumberedFmt: String,
    val receptionFmt: String,
    val receptionNumberedFmt: String,
    val productFmt: String,
    val productKindsFmt: String,
    val moreFmt: String,
    val container: Triple<String, String, String>,
    val wagon: Triple<String, String, String>,
    val truck: Triple<String, String, String>,
    val transport: Triple<String, String, String>,
    val portWord: Triple<String, String, String>,
    val pointWord: Triple<String, String, String>,
    val kindWord: Triple<String, String, String>
) {
    companion object {
        /** RU defaults for unit tests without Android Resources. */
        val Russian = SummaryStrings(
            tonnageFmt = "Тоннаж: %1\$s кг",
            portFmt = "Порт: %1\$s",
            portNumberedFmt = "Порт %1\$d: %2\$s",
            receptionFmt = "Точка приёма: %1\$s",
            receptionNumberedFmt = "Точка приёма %1\$d: %2\$s",
            productFmt = "Продукция: %1\$s",
            productKindsFmt = "%1\$s продукции",
            moreFmt = "Ещё %1\$s",
            container = Triple("контейнер", "контейнера", "контейнеров"),
            wagon = Triple("вагон", "вагона", "вагонов"),
            truck = Triple("авто", "авто", "авто"),
            transport = Triple("транспорт", "транспорта", "транспортов"),
            portWord = Triple("порт", "порта", "портов"),
            pointWord = Triple("точка", "точки", "точек"),
            kindWord = Triple("вид", "вида", "видов")
        )

        fun from(resources: Resources): SummaryStrings = SummaryStrings(
            tonnageFmt = resources.getString(R.string.schedule_tonnage),
            portFmt = resources.getString(R.string.port_prefix),
            portNumberedFmt = resources.getString(R.string.port_numbered),
            receptionFmt = resources.getString(R.string.reception_prefix),
            receptionNumberedFmt = resources.getString(R.string.reception_numbered),
            productFmt = resources.getString(R.string.product_prefix),
            productKindsFmt = resources.getString(R.string.schedule_product_kinds),
            moreFmt = resources.getString(R.string.schedule_more),
            container = Triple(
                resources.getString(R.string.plural_container_one),
                resources.getString(R.string.plural_container_few),
                resources.getString(R.string.plural_container_many)
            ),
            wagon = Triple(
                resources.getString(R.string.plural_wagon_one),
                resources.getString(R.string.plural_wagon_few),
                resources.getString(R.string.plural_wagon_many)
            ),
            truck = Triple(
                resources.getString(R.string.plural_truck_one),
                resources.getString(R.string.plural_truck_few),
                resources.getString(R.string.plural_truck_many)
            ),
            transport = Triple(
                resources.getString(R.string.plural_transport_one),
                resources.getString(R.string.plural_transport_few),
                resources.getString(R.string.plural_transport_many)
            ),
            portWord = Triple(
                resources.getString(R.string.plural_port_one),
                resources.getString(R.string.plural_port_few),
                resources.getString(R.string.plural_port_many)
            ),
            pointWord = Triple(
                resources.getString(R.string.plural_point_one),
                resources.getString(R.string.plural_point_few),
                resources.getString(R.string.plural_point_many)
            ),
            kindWord = Triple(
                resources.getString(R.string.plural_kind_one),
                resources.getString(R.string.plural_kind_few),
                resources.getString(R.string.plural_kind_many)
            )
        )
    }
}
