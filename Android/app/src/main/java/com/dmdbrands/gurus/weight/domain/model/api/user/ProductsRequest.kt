package com.dmdbrands.gurus.weight.domain.model.api.user

/**
 * Request body for `PATCH /v3/account/products` (spec §2.19).
 *
 * Dedicated setter for the account's `productTypes` so the product-selection / device-setup
 * flow can add or remove products directly without pairing a device or creating an entry.
 *
 * @property productTypes Non-empty list of `weight` / `blood_pressure` / `baby`.
 */
data class ProductsRequest(
    val productTypes: List<String>,
)
