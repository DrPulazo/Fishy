package com.example.fishy.domain.calc

import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

data class DoubleControlStats(
    val totalPallets: Int = 0,
    val exportedPallets: Int = 0,
    val importedPallets: Int = 0,
    val exportedPlaces: Double = 0.0,
    val importedPlaces: Double = 0.0
)

data class ShipmentTotals(
    val productTypes: Int = 0,
    val pallets: Int = 0,
    val places: Double = 0.0,
    val quantity: Int = 0,
    val targetWeight: Double = 0.0,
    val actualWeight: Double = 0.0,
    val remainder: Double = 0.0,
    val targetGrossWeight: Double = 0.0,
    val actualGrossWeight: Double = 0.0
)

data class PalletForecast(
    val fullPallets: Int,
    val lastPalletPlaces: Double,
    val totalExpected: Int
)

data class BatchStatus(
    val key: String,
    val batchName: String,
    val productName: String = "",
    val manufacturer: String = "",
    val packageWeight: Double = 0.0,
    val planned: Double,
    val used: Double,
    val warnThreshold: Int = 5
) {
    val remaining: Double get() = (planned - used).coerceAtLeast(0.0)
    val exhausted: Boolean get() = used >= planned && planned > 0
    val nearLimit: Boolean get() = !exhausted && remaining <= warnThreshold && planned > 0
}

object ShipmentCalculator {

    /** Soft cap: more than this would flood the UI and risk OOM / ANR. */
    const val MAX_FORECAST_PALLETS = 100

    private const val EPS = 1e-9

    fun realPallets(product: Product): List<Pallet> =
        product.pallets.filter { !it.isPlaceholder }

    fun placesForProduct(product: Product, doubleControl: Boolean): Double {
        val pallets = realPallets(product)
        return if (doubleControl) {
            pallets.filter { it.isImported }.sumOf { it.places }
        } else {
            pallets.sumOf { it.places }
        }
    }

    fun remainder(product: Product, doubleControl: Boolean, unload: Boolean): Double {
        val places = placesForProduct(product, doubleControl)
        return product.quantity.toDouble() - places
    }

    fun totalsForProducts(
        products: List<Product>,
        doubleControl: Boolean,
        unload: Boolean = false
    ): ShipmentTotals {
        val types = products
            .filter {
                it.name.isNotBlank() || it.batch.isNotBlank() ||
                    it.manufacturer.isNotBlank() || it.packageWeight > 0.0 ||
                    it.quantity > 0 ||
                    realPallets(it).any { p -> p.places > 0.0 }
            }
            .distinctBy { batchKey(it) }
            .size
        val pallets = products.sumOf { realPallets(it).size }
        val places = products.sumOf { placesForProduct(it, doubleControl) }
        val quantity = products.sumOf { it.quantity }
        val targetWeight = products.sumOf { it.packageWeight * it.quantity }
        val actualWeight = products.sumOf { it.packageWeight * placesForProduct(it, doubleControl) }
        val targetGrossWeight = products.sumOf {
            it.packageWeight * it.quantity * it.grossCoefficient
        }
        val actualGrossWeight = products.sumOf {
            it.packageWeight * placesForProduct(it, doubleControl) * it.grossCoefficient
        }
        val rem = products.sumOf { remainder(it, doubleControl, unload) }
        return ShipmentTotals(
            types, pallets, places, quantity, targetWeight, actualWeight, rem,
            targetGrossWeight, actualGrossWeight
        )
    }

    fun totals(payload: ShipmentPayload): ShipmentTotals {
        val unload = payload.mode == ShipmentMode.UNLOAD
        return when (payload.mode) {
            ShipmentMode.MONO -> totalsForProducts(payload.products, payload.doubleControlEnabled, unload)
            ShipmentMode.MULTI_PORT -> {
                val products = payload.multiPorts.flatMap { it.products }
                val anyDc = payload.doubleControlEnabled || payload.multiPorts.any { it.doubleControlEnabled }
                totalsForProducts(products, anyDc, false)
            }
            ShipmentMode.MULTI_VEHICLE -> {
                val products = payload.multiVehicles.flatMap { it.products }
                val anyDc = payload.doubleControlEnabled || payload.multiVehicles.any { it.doubleControlEnabled }
                totalsForProducts(products, anyDc, false)
            }
            ShipmentMode.UNLOAD -> {
                val products = payload.unloadReceptions.flatMap { r ->
                    r.inbounds.flatMap { it.products }
                }
                totalsForProducts(products, false, unload = true)
            }
        }
    }

    fun progressPercent(payload: ShipmentPayload): Float {
        if (batchControlActive(payload) && payload.batchLimits.isNotEmpty()) {
            // Status bar progress must follow batch control plan (plannedPlaces),
            // even when product.quantity is not filled in transport UI.
            val statuses = batchStatuses(payload, payload.batchWarnThreshold)
            val planned = statuses.sumOf { it.planned }
            if (planned <= EPS) return 0f
            val used = statuses.sumOf { it.used }
            val ratio = used / planned
            return if (ratio > 1f + EPS) 1.01f else ratio.toFloat()
        }
        val t = totals(payload)
        if (t.quantity <= 0) return 0f
        if (hasAnyProductOverload(payload)) return 1.01f
        val done = t.quantity - t.remainder
        // May exceed 1f on overload — FillProgressBar turns red in that case.
        return (done / t.quantity).toFloat()
    }

    /** Products with a planned quantity that are over the target places. */
    fun hasAnyProductOverload(payload: ShipmentPayload): Boolean =
        targetedProductRemainders(payload).any { it < -EPS }

    /** Products with a planned quantity that are under the target places. */
    fun hasAnyProductUnderload(payload: ShipmentPayload): Boolean =
        targetedProductRemainders(payload).any { it > EPS }

    private fun targetedProductRemainders(payload: ShipmentPayload): List<Double> {
        val unload = payload.mode == ShipmentMode.UNLOAD
        return when (payload.mode) {
            ShipmentMode.MONO -> payload.products
                .filter { it.quantity > 0 }
                .map { remainder(it, payload.doubleControlEnabled, unload) }
            ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.flatMap { vg ->
                val dc = payload.doubleControlEnabled || vg.doubleControlEnabled
                vg.products.filter { it.quantity > 0 }.map { remainder(it, dc, false) }
            }
            ShipmentMode.MULTI_PORT -> payload.multiPorts.flatMap { pg ->
                val dc = payload.doubleControlEnabled || pg.doubleControlEnabled
                pg.products.filter { it.quantity > 0 }.map { remainder(it, dc, false) }
            }
            ShipmentMode.UNLOAD -> payload.unloadReceptions
                .flatMap { it.inbounds }
                .flatMap { it.products }
                .filter { it.quantity > 0 }
                .map { remainder(it, false, unload = true) }
        }
    }

    fun doubleControlStats(products: List<Product>, enabled: Boolean): DoubleControlStats {
        if (!enabled) return DoubleControlStats()
        val all = products.flatMap { realPallets(it) }
        return DoubleControlStats(
            totalPallets = all.size,
            exportedPallets = all.size,
            importedPallets = all.count { it.isImported },
            exportedPlaces = all.sumOf { it.places },
            importedPlaces = all.filter { it.isImported }.sumOf { it.places }
        )
    }

    fun forecastFromFirstPallet(totalQuantity: Int, firstPalletPlaces: Double): PalletForecast? {
        if (firstPalletPlaces <= 0 || totalQuantity <= 0) return null
        val full = floor(totalQuantity / firstPalletPlaces).toInt()
        val rem = totalQuantity - full * firstPalletPlaces
        val remIsZero = abs(rem) < EPS
        val last = if (remIsZero) firstPalletPlaces else rem
        val expected = if (remIsZero) full else full + 1
        return PalletForecast(
            fullPallets = full,
            lastPalletPlaces = last,
            totalExpected = expected.coerceAtLeast(1)
        )
    }

    /** How many pallets the forecast would create (real + placeholders), or null if not applicable. */
    fun expectedForecastPallets(product: Product): Int? {
        val real = realPallets(product)
        if (real.size != 1) return null
        return forecastFromFirstPallet(product.quantity, real.first().places)?.totalExpected
    }

    /**
     * Forecast is allowed only with quantity set and exactly one real pallet that already has places.
     * Extra real pallets lock further auto-forecast for this product.
     */
    fun canAutoForecast(product: Product): Boolean {
        if (product.quantity <= 0) return false
        val real = realPallets(product)
        return real.size == 1 && real.first().places > 0
    }

    fun forecastSignature(product: Product): String? {
        if (!canAutoForecast(product)) return null
        val first = realPallets(product).first()
        return "${product.quantity}:${first.places}"
    }

    /** Russian forecast dialog: multiline «Ожидается:» + «N поддон(а) × M мест». */
    fun formatForecastExpectationRu(totalQuantity: Int, firstPalletPlaces: Double): String? {
        val forecast = forecastFromFirstPallet(totalQuantity, firstPalletPlaces) ?: return null
        val full = forecast.fullPallets
        val rem = totalQuantity - full * firstPalletPlaces
        val remIsZero = abs(rem) < EPS
        return when {
            full == 0 && !remIsZero ->
                "Ожидается:\n${ruPallets(1)} × ${ruPlaces(rem)}"
            remIsZero ->
                "Ожидается:\n${ruPallets(full)} × ${ruPlaces(firstPalletPlaces)}"
            else ->
                "Ожидается:\n${ruPallets(full)} × ${ruPlaces(firstPalletPlaces)}\n${ruPallets(1)} × ${ruPlaces(rem)}"
        }
    }

    private fun ruPallets(n: Int): String {
        val mod100 = abs(n) % 100
        val mod10 = abs(n) % 10
        val word = when {
            mod100 in 11..14 -> "поддонов"
            mod10 == 1 -> "поддон"
            mod10 in 2..4 -> "поддона"
            else -> "поддонов"
        }
        return "$n $word"
    }

    /** «1 поддон», «2 поддона», «5 поддонов». */
    fun formatPalletsRu(n: Int): String = ruPallets(n)

    /** «1 место», «2 места», «5 мест»; fractions → «1,5 мест». */
    fun formatPlacesRu(n: Int, thousandsSeparator: Boolean = false): String {
        val mod100 = abs(n) % 100
        val mod10 = abs(n) % 10
        val word = when {
            mod100 in 11..14 -> "мест"
            mod10 == 1 -> "место"
            mod10 in 2..4 -> "места"
            else -> "мест"
        }
        val num = QuantityFormatters.formatInteger(n, thousandsSeparator)
        return "$num $word"
    }

    fun formatPlacesRu(n: Double, thousandsSeparator: Boolean = false): String {
        val asLong = n.roundToLong()
        if (abs(n - asLong) < EPS) {
            return formatPlacesRu(asLong.toInt(), thousandsSeparator)
        }
        val num = QuantityFormatters.formatWeight(n, thousandsSeparator)
        return "$num мест"
    }

    /** «1 вид», «2 вида», «5 видов». */
    fun formatKindsRu(n: Int): String {
        val mod100 = abs(n) % 100
        val mod10 = abs(n) % 10
        val word = when {
            mod100 in 11..14 -> "видов"
            mod10 == 1 -> "вид"
            mod10 in 2..4 -> "вида"
            else -> "видов"
        }
        return "$n $word"
    }

    private fun ruPlaces(n: Double): String = formatPlacesRu(n)

    fun applyForecastPlaceholders(product: Product): Product {
        val real = realPallets(product)
        if (real.isEmpty()) return product.copy(pallets = emptyList())
        // Only seed from a single real pallet — never rewrite when more reals exist.
        if (real.size != 1) return product
        val firstPlaces = real.first().places
        val forecast = forecastFromFirstPallet(product.quantity, firstPlaces)
            ?: return product.copy(pallets = real)
        if (forecast.totalExpected > MAX_FORECAST_PALLETS) {
            return product.copy(pallets = real)
        }
        val placeholders = mutableListOf<Pallet>()
        for (i in real.size until forecast.totalExpected) {
            val places = if (i == forecast.totalExpected - 1) forecast.lastPalletPlaces else firstPlaces
            placeholders += Pallet(
                id = product.id + i + 1_000_000,
                palletNumber = i + 1,
                places = places,
                isPlaceholder = true
            )
        }
        return product.copy(pallets = real + placeholders)
    }

    fun batchKey(
        productName: String,
        batchName: String,
        manufacturer: String,
        packageWeight: Double
    ): String {
        val tare = if (packageWeight == 0.0) "0" else packageWeight.toString()
        return listOf(
            productName.trim().lowercase(),
            batchName.trim().lowercase(),
            manufacturer.trim().lowercase(),
            tare
        ).joinToString("|")
    }

    fun batchKey(product: Product): String =
        batchKey(product.name, product.batch, product.manufacturer, product.packageWeight)

    fun batchKey(limit: BatchLimit): String =
        batchKey(limit.productName, limit.batchName, limit.manufacturer, limit.packageWeight)

    /** Batch UI / enforcement only for multi-vehicle and unload. */
    fun batchControlActive(payload: ShipmentPayload): Boolean =
        payload.batchControlEnabled &&
            (payload.mode == ShipmentMode.MULTI_VEHICLE || payload.mode == ShipmentMode.UNLOAD)

    fun batchPlanTonnageKg(payload: ShipmentPayload): Double =
        payload.batchLimits.sumOf { it.packageWeight * it.plannedPlaces }

    /**
     * Planner / notification tonnage: batch plan when control is active and limits exist,
     * otherwise product quantities ([totals] targetWeight).
     */
    fun plannedTonnageKg(payload: ShipmentPayload): Double {
        if (batchControlActive(payload) && payload.batchLimits.isNotEmpty()) {
            return batchPlanTonnageKg(payload)
        }
        return totals(payload).targetWeight
    }

    data class BatchTransportMismatch(
        val key: String,
        val plannedPlaces: Double,
        val transportPlaces: Double
    )

    /**
     * Compares batch [BatchLimit.plannedPlaces] to sum of product [Product.quantity] per key.
     * Only keys with a positive limit and positive transport quantity; either direction counts.
     */
    fun batchTransportMismatches(payload: ShipmentPayload): List<BatchTransportMismatch> {
        if (!batchControlActive(payload) || payload.batchLimits.isEmpty()) return emptyList()
        val qtyByKey = allProducts(payload)
            .groupBy { batchKey(it) }
            .mapValues { (_, list) -> list.sumOf { it.quantity.toDouble() } }
        return payload.batchLimits.mapNotNull { limit ->
            if (limit.plannedPlaces <= EPS) return@mapNotNull null
            val key = batchKey(limit)
            val transport = qtyByKey[key] ?: 0.0
            if (transport <= EPS) return@mapNotNull null
            if (abs(transport - limit.plannedPlaces) <= EPS) return@mapNotNull null
            BatchTransportMismatch(key, limit.plannedPlaces, transport)
        }
    }

    fun batchStatuses(
        payload: ShipmentPayload,
        warnThreshold: Int
    ): List<BatchStatus> {
        if (!batchControlActive(payload)) return emptyList()
        val usedByKey = allProducts(payload)
            .groupBy { batchKey(it) }
            .mapValues { (_, list) ->
                list.sumOf { placesForProduct(it, payload.doubleControlEnabled) }
            }
        return payload.batchLimits.map { limit ->
            val key = batchKey(limit)
            BatchStatus(
                key = key,
                batchName = limit.batchName,
                productName = limit.productName,
                manufacturer = limit.manufacturer,
                packageWeight = limit.packageWeight,
                planned = limit.plannedPlaces,
                used = usedByKey[key] ?: 0.0,
                warnThreshold = warnThreshold
            )
        }
    }

    fun canAddPlaces(
        payload: ShipmentPayload,
        product: Product,
        additionalPlaces: Double
    ): Boolean {
        if (!batchControlActive(payload)) return true
        val key = batchKey(product)
        val limit = payload.batchLimits.find { batchKey(it) == key } ?: return true
        val used = allProducts(payload)
            .filter { batchKey(it) == key }
            .sumOf { placesForProduct(it, payload.doubleControlEnabled) }
        return used + additionalPlaces <= limit.plannedPlaces + EPS
    }

    /**
     * True when batch control is on, limits exist, product identity is complete
     * (name + batch + manufacturer + tare), and its batchKey matches none of the limits.
     */
    fun isUnknownBatch(product: Product, payload: ShipmentPayload): Boolean {
        if (!batchControlActive(payload) || payload.batchLimits.isEmpty()) return false
        if (product.name.isBlank() ||
            product.batch.isBlank() ||
            product.manufacturer.isBlank() ||
            product.packageWeight <= 0.0
        ) {
            return false
        }
        val key = batchKey(product)
        return payload.batchLimits.none { batchKey(it) == key }
    }

    fun allProducts(payload: ShipmentPayload): List<Product> = when (payload.mode) {
        ShipmentMode.MONO -> payload.products
        ShipmentMode.MULTI_PORT -> payload.multiPorts.flatMap { it.products }
        ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.flatMap { it.products }
        ShipmentMode.UNLOAD -> payload.unloadReceptions.flatMap { r ->
            r.inbounds.flatMap { it.products }
        }
    }

    fun ceilPositive(value: Double): Int = ceil(value).toInt().coerceAtLeast(0)
}
