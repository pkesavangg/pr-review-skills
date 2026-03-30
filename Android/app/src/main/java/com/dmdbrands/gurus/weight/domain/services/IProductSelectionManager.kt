package com.dmdbrands.gurus.weight.domain.services

import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import kotlinx.coroutines.flow.StateFlow

interface IProductSelectionManager {

    val availableProducts: StateFlow<List<ProductSelection>>

    val selectedProduct: StateFlow<ProductSelection>

    suspend fun selectProduct(selection: ProductSelection)

    suspend fun loadAvailableProducts(accountId: String)

    val showSheet: StateFlow<Boolean>

    val sheetTitle: StateFlow<String>

    fun showProductSheet(title: String)

    fun dismissProductSheet()
}
