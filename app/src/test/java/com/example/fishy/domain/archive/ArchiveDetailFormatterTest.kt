package com.example.fishy.domain.archive

import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.PortGroup
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
import com.example.fishy.domain.report.ReportGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveDetailFormatterTest {

    @Test
    fun multiPortKeepsIdenticalProductsSeparateAcrossPorts() {
        val same = { places: Double ->
            Product(
                name = "Корюшка н/р",
                manufacturer = "ООО «Весткамфиш»",
                packageWeight = 20.0,
                pallets = listOf(Pallet(places = places))
            )
        }
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_PORT,
            transport = Transport(containerNumber = "TEMU9420988"),
            multiPorts = listOf(
                PortGroup(port = "Холодильник", vessel = "Судно А", products = listOf(same(614.0))),
                PortGroup(port = "Подвоз", products = listOf(same(1.0)))
            )
        )
        val archive = ArchiveDetailFormatter.contentBlocks(
            payload,
            formatContainerSpaces = true
        ).joinToString("\n\n")
        assertTrue(archive.contains("Порт: Холодильник"))
        assertTrue(archive.contains("Судно: Судно А"))
        assertTrue(archive.contains("Порт: Подвоз"))
        assertTrue(archive.contains("614 мест"))
        assertTrue(archive.contains("1 место"))
        assertFalse(archive.contains("615 мест"))

        val report = ReportGenerator.generate(payload, formatContainerSpaces = true)
        assertTrue(report.contains("615 мест"))
        assertEquals(1, report.lines().count { it.contains("Весткамфиш") })
    }

    @Test
    fun blankVesselOmitted() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_PORT,
            multiPorts = listOf(
                PortGroup(
                    port = "Порт",
                    vessel = "   ",
                    products = listOf(
                        Product(name = "A", packageWeight = 1.0, pallets = listOf(Pallet(places = 1.0)))
                    )
                )
            )
        )
        val text = ArchiveDetailFormatter.contentBlocks(payload).joinToString("\n")
        assertTrue(text.contains("Порт: Порт"))
        assertFalse(text.contains("Судно:"))
        val portIdx = text.indexOf("Порт: Порт")
        val productIdx = text.indexOf("A ")
        val between = text.substring(portIdx, productIdx)
        assertEquals(1, between.lines().count { it.isNotBlank() })
    }

    @Test
    fun containerWithRoadShowsAllFieldsWithoutDriver() {
        val lines = ArchiveDetailFormatter.formatTransportFull(
            Transport(
                containerNumber = "CSQU3054383",
                truckNumber = "A123BC77",
                trailerNumber = "AB123477",
                sealNumber = "SEAL1",
                wagonNumber = "12345678" // ignored when road present
            ),
            formatContainerSpaces = true,
            formatVehicleSpaces = true
        )
        val text = lines.joinToString("\n")
        assertTrue(text.contains("Контейнер:"))
        assertTrue(text.contains("Авто:"))
        assertTrue(text.contains("Прицеп:"))
        assertTrue(text.contains("Пломба: SEAL1"))
        assertFalse(text.contains("Вагон:"))
        assertFalse(text.contains("Водитель"))
    }

    @Test
    fun wagonOnlyWhenNoRoad() {
        val lines = ArchiveDetailFormatter.formatTransportFull(
            Transport(wagonNumber = "12345678", sealNumber = "S1")
        )
        assertEquals(
            listOf("Вагон: 12345678", "Пломба: S1"),
            lines
        )
    }

    @Test
    fun notesReturnedWhenPresent() {
        val withNotes = ShipmentPayload(notes = "  Итого расход 100  ")
        assertEquals("Итого расход 100", ArchiveDetailFormatter.notes(withNotes))
        assertEquals(null, ArchiveDetailFormatter.notes(ShipmentPayload()))
    }
}
