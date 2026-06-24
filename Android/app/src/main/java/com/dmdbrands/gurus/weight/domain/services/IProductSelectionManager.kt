package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import kotlinx.coroutines.flow.StateFlow

interface IProductSelectionManager {

    val availableProducts: StateFlow<List<ProductSelection>>

    val selectedProduct: StateFlow<ProductSelection>

    /** True when snapshot multi-product view should be shown instead of single-product dashboard. */
    val isSnapshotMode: StateFlow<Boolean>

    /**
     * True when the account owns a baby scale device. Drives "Baby Scale" staying in the
     * product dropdown and "My Kids" being enabled in Settings even with zero baby profiles. (MOB-416)
     */
    val hasBabyScaleDevice: StateFlow<Boolean>

    fun setSnapshotMode(enabled: Boolean)

    suspend fun selectProduct(selection: ProductSelection)

    suspend fun loadAvailableProducts(accountId: String)

    /**
     * Submits [productType] to the account's `productTypes` on the server (spec §2.19) after a
     * device-setup flow completes, so the server reflects the newly connected product. No-op if
     * already present; failures are swallowed so they never disrupt setup completion.
     */
    suspend fun persistProductForSetup(productType: ProductType)

    val showSheet: StateFlow<Boolean>

    val sheetTitle: StateFlow<String>

    fun showProductSheet(title: String)

    fun dismissProductSheet()
}
