package com.example.fishy.domain.calc

import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import kotlin.math.ceil

data class DoubleControlStats(
    val totalPallets: Int = 0,
    val exportedPallets: Int = 0,
    val importedPallets: Int = 0,
    val exportedPlaces: Int = 0,
    val importedPlaces: Int = 0
)

data class ShipmentTotals(
    val productTypes: Int = 0,
    val pallets: Int = 0,
    val places: Int = 0,
    val quantity: Int = 0,
    val targetWeight: Double = 0.0,
    val actualWeight: Double = 0.0,
    val remainder: Int = 0
)

data class PalletForecast(
    val fullPallets: Int,
    val lastPalletPlaces: Int,
    val totalExpected: Int
)

data class BatchStatus(
    val key: String,
    val batchName: String,
    val productName: String = "",
    val manufacturer: String = "",
    val packageWeight: Double = 0.0,
    val planned: Int,
    val used: Int,
    val warnThreshold: Int = 5
) {
    val remaining: Int get() = (planned - used).coerceAtLeast(0)
    val exhausted: Boolean get() = used >= planned && planned > 0
    val nearLimit: Boolean get() = !exhausted && remaining <= warnThreshold && planned > 0
}

object ShipmentCalculator {

    /** Soft cap: more than this would flood the UI and risk OOM / ANR. */
    const val MAX_FORECAST_PALLETS = 100

    fun realPallets(product: Product): List<Pallet> =
        product.pallets.filter { !it.isPlaceholder }

    fun placesForProduct(product: Product, doubleControl: Boolean): Int {
        val pallets = realPallets(product)
        return if (doubleControl) {
            pallets.filter { it.isImported }.sumOf { it.places }
        } else {
            pallets.sumOf { it.places }
        }
    }

    fun remainder(product: Product, doubleControl: Boolean, unload: Boolean): Int {
        val places = placesForProduct(product, doubleControl)
        return if (unload) {
            product.quantity + places // quantity is initial stock; places increase "removed"?
            // Unload: counters decrease — quantity is plan to unload; each pallet.places counts toward unloading
            product.quantity - places
        } else {
            product.quantity - places
        }
    }

    fun totalsForProducts(
        products: List<Product>,
        doubleControl: Boolean,
        unload: Boolean = false
    ): ShipmentTotals {
        val types = products.count { it.name.isNotBlank() }
        val pallets = products.sumOf { realPallets(it).size }
        val places = products.sumOf { placesForProduct(it, doubleControl) }
        val quantity = products.sumOf { it.quantity }
        val targetWeight = products.sumOf { it.packageWeight * it.quantity }
        val actualWeight = products.sumOf { it.packageWeight * placesForProduct(it, doubleControl) }
        val rem = products.sumOf { remainder(it, doubleControl, unload) }
        return ShipmentTotals(types, pallets, places, quantity, targetWeight, actualWeight, rem)
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
                totalsForProducts(products, false, true)
            }
        }
    }

    fun progressPercent(payload: ShipmentPayload): Float {
        val t = totals(payload)
        if (t.quantity <= 0) return 0f
        val done = t.quantity - t.remainder
        // May exceed 1f on overload — FillProgressBar turns red in that case.
        return done.toFloat() / t.quantity.toFloat()
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

    fun forecastFromFirstPallet(totalQuantity: Int, firstPalletPlaces: Int): PalletForecast? {
        if (firstPalletPlaces <= 0 || totalQuantity <= 0) return null
        val full = totalQuantity / firstPalletPlaces
        val rem = totalQuantity % firstPalletPlaces
        val last = if (rem == 0) firstPalletPlaces else rem
        val expected = if (rem == 0) full else full + 1
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

    /** Russian message: «Ожидается N поддон(а/ов) по M мест(а/о) …». */
    fun formatForecastExpectationRu(totalQuantity: Int, firstPalletPlaces: Int): String? {
        val forecast = forecastFromFirstPallet(totalQuantity, firstPalletPlaces) ?: return null
        val full = forecast.fullPallets
        val rem = totalQuantity % firstPalletPlaces
        return when {
            full == 0 && rem > 0 ->
                "Ожидается ${ruPallets(1)} по ${ruPlaces(rem)}"
            rem == 0 ->
                "Ожидается ${ruPallets(full)} по ${ruPlaces(firstPalletPlaces)}"
            else ->
                "Ожидается ${ruPallets(full)} по ${ruPlaces(firstPalletPlaces)} и ${ruPallets(1)} по ${ruPlaces(rem)}"
        }
    }

    private fun ruPallets(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "поддонов"
            mod10 == 1 -> "поддон"
            mod10 in 2..4 -> "поддона"
            else -> "поддонов"
        }
        return "$n $word"
    }

    /** «1 место», «2 места», «5 мест». */
    fun formatPlacesRu(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "мест"
            mod10 == 1 -> "место"
            mod10 in 2..4 -> "места"
            else -> "мест"
        }
        return "$n $word"
    }

    private fun ruPlaces(n: Int): String = formatPlacesRu(n)

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

    fun batchStatuses(
        payload: ShipmentPayload,
        warnThreshold: Int
    ): List<BatchStatus> {
        if (!payload.batchControlEnabled) return emptyList()
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
                used = usedByKey[key] ?: 0,
                warnThreshold = warnThreshold
            )
        }
    }

    fun canAddPlaces(
        payload: ShipmentPayload,
        product: Product,
        additionalPlaces: Int
    ): Boolean {
        if (!payload.batchControlEnabled) return true
        val key = batchKey(product)
        val limit = payload.batchLimits.find { batchKey(it) == key } ?: return true
        val used = allProducts(payload)
            .filter { batchKey(it) == key }
            .sumOf { placesForProduct(it, payload.doubleControlEnabled) }
        return used + additionalPlaces <= limit.plannedPlaces
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
