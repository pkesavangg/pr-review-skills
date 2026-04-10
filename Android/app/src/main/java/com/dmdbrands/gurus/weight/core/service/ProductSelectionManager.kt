package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class ProductSelectionManager @Inject constructor(
  private val productSelectionRepository: IProductSelectionRepository,
) : IProductSelectionManager {

  private val _availableProducts = MutableStateFlow<List<ProductSelection>>(listOf(ProductSelection.MyWeight))
  override val availableProducts: StateFlow<List<ProductSelection>> = _availableProducts.asStateFlow()

  private val _selectedProduct = MutableStateFlow<ProductSelection>(ProductSelection.MyWeight)
  override val selectedProduct: StateFlow<ProductSelection> = _selectedProduct.asStateFlow()

  override suspend fun loadAvailableProducts(accountId: String) {
    if (USE_SAMPLE_PRODUCTS) {
      _availableProducts.value = listOf(
        ProductSelection.MyWeight,
        ProductSelection.BloodPressure,
        ProductSelection.Baby(BabyProfile(id = "sample-1", name = "Timmy", birthdate = null, accountId = accountId)),
        ProductSelection.Baby(BabyProfile(id = "sample-2", name = "Lucy", birthdate = null, accountId = accountId)),
      )
      AppLog.d(TAG, "Available (sample): ${_availableProducts.value}")
      return
    }

    val babyProfiles = productSelectionRepository.getBabyProfiles(accountId)
    val hasBpm = productSelectionRepository.hasBpmDevice(accountId)

    val products = mutableListOf<ProductSelection>(ProductSelection.MyWeight)

    if (hasBpm) {
      products.add(ProductSelection.BloodPressure)
    }

    babyProfiles.forEach { profile ->
      products.add(ProductSelection.Baby(profile))
    }

    _availableProducts.value = products
    AppLog.d(TAG, "Available: $products")
  }

  override suspend fun selectProduct(selection: ProductSelection) {
    AppLog.d(TAG, "Selecting: ${selection.productType}")
    productSelectionRepository.saveSelectedProductType(selection.productType)
    val babyProfileId = when (selection) {
      is ProductSelection.Baby -> selection.profile.id
      else -> null
    }
    productSelectionRepository.saveSelectedBabyProfileId(babyProfileId)
    _selectedProduct.value = selection
  }

  // Bottom sheet state

  private val _showSheet = MutableStateFlow(false)
  override val showSheet: StateFlow<Boolean> = _showSheet.asStateFlow()

  private val _sheetTitle = MutableStateFlow("")
  override val sheetTitle: StateFlow<String> = _sheetTitle.asStateFlow()

  override fun showProductSheet(title: String) {
    _sheetTitle.value = title
    _showSheet.value = true
  }

  override fun dismissProductSheet() {
    _showSheet.value = false
  }

  companion object {
    private const val TAG = "ProductSelectionManager"

    /** Flip to `true` to use sample products (BP + 2 babies) for testing. */
    var USE_SAMPLE_PRODUCTS = true
  }
}
