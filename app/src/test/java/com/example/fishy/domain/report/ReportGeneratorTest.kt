package com.example.fishy.domain.report

import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
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
    fun vehicleWithoutContainerShowsTruckTrailerSeal() {
        val line = ReportGenerator.formatTransportLine(
            Transport(
                truckNumber = "A123BC77",
                trailerNumber = "AB123477",
                sealNumber = "SEAL1"
            ),
            formatContainerSpaces = false,
            formatVehicleSpaces = true
        )
        assertEquals("A 123 BC / 77, AB 1234 / 77, SEAL1", line)
    }

    @Test
    fun productLineUsesPlacesDeclensionAndMass() {
        val line = ReportGenerator.formatProductLine(
            Product(
                name = "Горбуша",
                batch = "12",
                manufacturer = "Океан",
                packageWeight = 10.0,
                pallets = listOf(Pallet(places = 1))
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
                pallets = listOf(Pallet(places = 2))
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
                    pallets = listOf(Pallet(places = 5))
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
                        Product(name = "A", batch = "1", manufacturer = "M", packageWeight = 1.0, pallets = listOf(Pallet(places = 1)))
                    )
                ),
                VehicleGroup(
                    transport = Transport(truckNumber = "A123BC77"),
                    products = listOf(
                        Product(name = "B", batch = "2", manufacturer = "M", packageWeight = 2.0, pallets = listOf(Pallet(places = 2)))
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
    fun convertQuotesPairsGuillemets() {
        assertEquals("Слово «раз» и «два»", ReportGenerator.convertQuotes("Слово \"раз\" и \"два\""))
    }
}
