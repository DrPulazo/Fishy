package com.example.fishy.feature.shipment

import com.example.fishy.domain.model.Product

fun productAccordionTitle(
    product: Product,
    newProductLabel: String
): String {
    val head = buildString {
        if (product.name.isNotBlank()) append(product.name)
        if (product.batch.isNotBlank()) {
            if (isNotEmpty()) append(' ')
            append(product.batch)
        }
    }
    if (head.isBlank()) return newProductLabel
    return if (product.manufacturer.isNotBlank()) "$head - ${product.manufacturer}" else head
}
