package com.example.fishy.domain.report

import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.PortGroup
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
import com.example.fishy.domain.model.UnloadInbound
import com.example.fishy.domain.model.UnloadReception
import com.example.fishy.domain.model.VehicleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportGeneratorTest {

    @Test
    fun containerExcludesOtherTransportFields() {
        val line = ReportGenerator.formatTransportLine(
            Transport(
                containerNumber = "CSQU3054383",
                truckNumber = "A123BC77",
                trailerNumber = "AB123477",
                sealNumber = "SEAL1",
                wagonNumber = "12345678"
            ),
            formatContainerSpaces = true,
            formatVehicleSpaces = true
        )
        assertEquals("CSQU 3054383", line)
    }

    @Test
    fun wagonExcludesRoadFieldsWhenNoContainer() {
        val line = ReportGenerator.formatTransportLine(
            Transport(
                wagonNumber = "12345678",
                truckNumber = "A123BC77",
                sealNumber = "SEAL1"
            ),
            formatContainerSpaces = false,
            formatVehicleSpaces = true
        )
        assertEquals("12345678", line)
    }

    @Test
    fun furaShowsTruckTrailerDriverPlaceholderAndSeal() {
        val line = ReportGenerator.formatTransportLine(
            Transport(
                truckNumber = "A123BC77",
                trailerNumber = "AB123477",
                sealNumber = "SEAL1"
            ),
            formatContainerSpaces = false,
            formatVehicleSpaces = true
        )
        assertEquals(
            "A 123 BC / 77 – AB 1234 / 77\n" +
                "Водитель: *Добавьте ФИО и контактный телефон водителя*\n" +
                "Пломба: SEAL1",
            line
        )
    }

    @Test
    fun furaTruckOnlyWithSealIncludesDriverPlaceholder() {
        val line = ReportGenerator.formatTransportLine(
            Transport(
                truckNumber = "A123BC77",
                sealNumber = "774206"
            ),
            formatContainerSpaces = false,
            formatVehicleSpaces = true
        )
        assertEquals(
            "A 123 BC / 77\n" +
                "Водитель: *Добавьте ФИО и контактный телефон водителя*\n" +
                "Пломба: 774206",
            line
        )
    }

    @Test
    fun furaWithoutSealStillShowsDriverPlaceholder() {
        val line = ReportGenerator.formatTransportLine(
            Transport(
                truckNumber = "A123BC77",
                trailerNumber = "AB123477"
            ),
            formatContainerSpaces = false,
            formatVehicleSpaces = true
        )
        assertEquals(
            "A 123 BC / 77 – AB 1234 / 77\n" +
                "Водитель: *Добавьте ФИО и контактный телефон водителя*",
            line
        )
    }

    @Test
    fun sealOnlyWithoutVehicleShowsNothingInReport() {
        val line = ReportGenerator.formatTransportLine(
            Transport(sealNumber = "ONLY"),
            formatContainerSpaces = false,
            formatVehicleSpaces = false
        )
        assertEquals("", line)
    }

    @Test
    fun productLineUsesPlacesDeclensionAndMass() {
        val line = ReportGenerator.formatProductLine(
            Product(
                name = "Горбуша",
                batch = "12",
                manufacturer = "Океан",
                packageWeight = 10.0,
                pallets = listOf(Pallet(places = 1.0))
            ),
            doubleControl = false
        )
        assertEquals("Горбуша 12 (1/10) – Океан - 1 место – 10 кг", line)
    }

    @Test
    fun placesDeclensionTwo() {
        val line = ReportGenerator.formatProductLine(
            Product(
                name = "Кета",
                batch = "B",
                manufacturer = "Завод",
                packageWeight = 20.0,
                pallets = listOf(Pallet(places = 2.0))
            ),
            doubleControl = false
        )
        assertTrue(line.contains("2 места"))
        assertTrue(line.contains("40 кг"))
    }

    @Test
    fun fullReportLayoutMono() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MONO,
            customer = "Не должен попасть",
            port = "Не должен",
            vessel = "Не должен",
            transport = Transport(containerNumber = "CSQU3054383"),
            products = listOf(
                Product(
                    name = "Горбуша",
                    batch = "12",
                    manufacturer = "Океан",
                    packageWeight = 10.0,
                    pallets = listOf(Pallet(places = 5.0))
                )
            ),
            notes = "Проверить пломбу",
            createdAtMillis = 1_700_000_000_000L,
            completedAtMillis = 1_700_000_000_000L
        )
        val report = ReportGenerator.generate(
            payload = payload,
            formatContainerSpaces = true,
            formatVehicleSpaces = true,
            generatedAtMillis = 1_700_000_100_000L
        )
        assertFalse(report.contains("Не должен"))
        assertTrue(report.contains("CSQU 3054383"))
        assertTrue(report.contains("Горбуша 12 (1/10) – Океан - 5 мест – 50 кг"))
        assertTrue(report.contains("Общий тоннаж: 50 кг"))
        assertTrue(report.contains("Проверить пломбу"))
        assertTrue(report.contains("Сгенерировано приложением «Фишка».\n"))
        assertFalse(report.contains("\"Фишка\""))
        // No blank line between shipment date and first transport
        assertTrue(report.contains(Regex("""\d{2}\.\d{2}\.\d{4}\nCSQU""")))
        // No blank line between generated footer and timestamp
        assertTrue(report.contains(Regex("""«Фишка»\.\n\d{2}\.\d{2}\.\d{4}""")))
    }

    @Test
    fun multiVehicleRepeatsTransportBlocks() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_VEHICLE,
            multiVehicles = listOf(
                VehicleGroup(
                    transport = Transport(wagonNumber = "11111111"),
                    products = listOf(
                        Product(name = "A", batch = "1", manufacturer = "M", packageWeight = 1.0, pallets = listOf(Pallet(places = 1.0)))
                    )
                ),
                VehicleGroup(
                    transport = Transport(truckNumber = "A123BC77"),
                    products = listOf(
                        Product(name = "B", batch = "2", manufacturer = "M", packageWeight = 2.0, pallets = listOf(Pallet(places = 2.0)))
                    )
                )
            ),
            createdAtMillis = 1_700_000_000_000L,
            completedAtMillis = 1_700_000_000_000L
        )
        val report = ReportGenerator.generate(
            payload = payload,
            formatVehicleSpaces = true,
            generatedAtMillis = 1_700_000_000_000L
        )
        assertTrue(report.contains("11111111"))
        assertTrue(report.contains("A 123 BC / 77"))
        assertTrue(report.indexOf("11111111") < report.indexOf("A 123 BC / 77"))
    }

    @Test
    fun unloadMergesIdenticalProductsAcrossInbounds() {
        val same = { places: Double ->
            Product(
                name = "Горбуша ПБГ",
                batch = "П-19",
                manufacturer = "ООО «Тымлатский РК»",
                packageWeight = 24.0,
                pallets = listOf(Pallet(places = places))
            )
        }
        val payload = ShipmentPayload(
            mode = ShipmentMode.UNLOAD,
            unloadReceptions = listOf(
                UnloadReception(
                    transport = Transport(wagonNumber = "12345678"),
                    inbounds = listOf(
                        UnloadInbound(
                            transport = Transport(truckNumber = "A111AA11"),
                            products = listOf(same(833.0))
                        ),
                        UnloadInbound(
                            transport = Transport(truckNumber = "A222AA22"),
                            products = listOf(same(833.0))
                        ),
                        UnloadInbound(
                            transport = Transport(truckNumber = "A333AA33"),
                            products = listOf(same(800.0))
                        )
                    )
                )
            ),
            createdAtMillis = 1_700_000_000_000L,
            completedAtMillis = 1_700_000_000_000L
        )
        val report = ReportGenerator.generate(
            payload = payload,
            generatedAtMillis = 1_700_000_000_000L
        )
        assertTrue(report.contains("12345678"))
        assertFalse(report.contains("A111"))
        assertTrue(
            report.contains(
                "Горбуша ПБГ П-19 (1/24) – ООО «Тымлатский РК» - 2466 мест – 59184 кг"
            )
        )
        assertEquals(1, report.lines().count { it.contains("Горбуша ПБГ П-19") })
        assertTrue(report.contains("Общий тоннаж: 59184 кг"))
    }

    @Test
    fun unloadDoesNotMergeWhenTareDiffers() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.UNLOAD,
            unloadReceptions = listOf(
                UnloadReception(
                    transport = Transport(wagonNumber = "99"),
                    inbounds = listOf(
                        UnloadInbound(
                            products = listOf(
                                Product(
                                    name = "Кета",
                                    batch = "B",
                                    manufacturer = "M",
                                    packageWeight = 24.0,
                                    pallets = listOf(Pallet(places = 10.0))
                                )
                            )
                        ),
                        UnloadInbound(
                            products = listOf(
                                Product(
                                    name = "Кета",
                                    batch = "B",
                                    manufacturer = "M",
                                    packageWeight = 22.0,
                                    pallets = listOf(Pallet(places = 10.0))
                                )
                            )
                        )
                    )
                )
            ),
            createdAtMillis = 1_700_000_000_000L,
            completedAtMillis = 1_700_000_000_000L
        )
        val report = ReportGenerator.generate(payload = payload, generatedAtMillis = 1_700_000_000_000L)
        assertEquals(2, report.lines().count { it.startsWith("Кета B") })
    }

    @Test
    fun multiPortMergesIdenticalProductsAcrossPorts() {
        val same = { places: Double ->
            Product(
                name = "Корюшка н/р",
                batch = "",
                manufacturer = "ООО «Весткамфиш»",
                packageWeight = 20.0,
                pallets = listOf(Pallet(places = places))
            )
        }
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_PORT,
            transport = Transport(containerNumber = "TEMU9420988"),
            multiPorts = listOf(
                PortGroup(
                    port = "Холодильник",
                    products = listOf(same(614.0), Product(name = "Корюшка н/р", manufacturer = "ООО «Другое»", packageWeight = 20.0, pallets = listOf(Pallet(places = 2.0))))
                ),
                PortGroup(
                    port = "Подвоз",
                    products = listOf(same(1.0))
                )
            ),
            createdAtMillis = 1_700_000_000_000L,
            completedAtMillis = 1_700_000_000_000L
        )
        val report = ReportGenerator.generate(
            payload = payload,
            formatContainerSpaces = true,
            generatedAtMillis = 1_700_000_000_000L
        )
        assertTrue(report.contains("TEMU 9420988"))
        assertTrue(report.contains("Корюшка н/р (1/20) – ООО «Весткамфиш» - 615 мест – 12300 кг"))
        assertEquals(1, report.lines().count { it.contains("Весткамфиш") })
        assertTrue(report.contains("Корюшка н/р (1/20) – ООО «Другое» - 2 места – 40 кг"))
        assertFalse(report.trimEnd().endsWith("\n"))
    }

    @Test
    fun convertQuotesPairsGuillemets() {
        assertEquals("Слово «раз» и «два»", ReportGenerator.convertQuotes("Слово \"раз\" и \"два\""))
    }
}
