package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ProductSelectionManager @Inject constructor(
  private val productSelectionRepository: IProductSelectionRepository,
) : IProductSelectionManager {

  private val _availableProducts = MutableStateFlow<List<ProductSelection>>(listOf(ProductSelection.MyWeight))
  override val availableProducts: StateFlow<List<ProductSelection>> = _availableProducts.asStateFlow()

  private val _selectedProduct = MutableStateFlow<ProductSelection>(ProductSelection.MyWeight)
  override val selectedProduct: StateFlow<ProductSelection> = _selectedProduct.asStateFlow()

  private val _isSnapshotMode = MutableStateFlow(true)
  override val isSnapshotMode: StateFlow<Boolean> = _isSnapshotMode.asStateFlow()

  private val _hasBabyScaleDevice = MutableStateFlow(false)
  override val hasBabyScaleDevice: StateFlow<Boolean> = _hasBabyScaleDevice.asStateFlow()

  override fun setSnapshotMode(enabled: Boolean) {
    _isSnapshotMode.value = enabled
  }

  override suspend fun loadAvailableProducts(accountId: String) {
    if (USE_SAMPLE_PRODUCTS) {
      val sample = listOf(
        ProductSelection.MyWeight,
        ProductSelection.BloodPressure,
        ProductSelection.Baby(BabyProfile(id = "sample-1", name = "Timmy", birthdate = "2026-01-10", sex = "male", accountId = accountId)),
      )
      _availableProducts.value = sample
      AppLog.d(TAG, "Available (sample): $sample")
      _selectedProduct.value = restoreSavedSelection(sample)
      _isSnapshotMode.value = !productSelectionRepository.observeHasUserSelected().first()
      return
    }

    val babyProfiles = productSelectionRepository.getBabyProfiles(accountId)
    val hasBpm = productSelectionRepository.hasBpmDevice(accountId)
    val hasBabyScale = productSelectionRepository.hasBabyScaleDevice(accountId)
    _hasBabyScaleDevice.value = hasBabyScale

    val products = mutableListOf<ProductSelection>(ProductSelection.MyWeight)

    if (hasBpm) {
      products.add(ProductSelection.BloodPressure)
    }

    if (babyProfiles.isNotEmpty()) {
      babyProfiles.forEach { profile ->
        products.add(ProductSelection.Baby(profile))
      }
    } else if (hasBabyScale) {
      // Owns a baby scale but has no profiles (e.g. deleted last baby): keep
      // "Baby Scale" in the dropdown so taps route to the add-baby flow. (MOB-416)
      products.add(ProductSelection.BabyScale)
    }

    _availableProducts.value = products
    AppLog.d(TAG, "Available: $products")

    _selectedProduct.value = restoreSavedSelection(products)
    _isSnapshotMode.value = !productSelectionRepository.observeHasUserSelected().first()
  }

  /**
   * Restore the user's last saved product (default MY_WEIGHT for legacy/upgraded users
   * who never explicitly chose). Falls back to MY_WEIGHT if the saved entry no longer
   * matches anything available. Does not write to storage — persistence happens on
   * signup pick and on detail-dashboard card tap via [selectProduct].
   */
  private suspend fun restoreSavedSelection(available: List<ProductSelection>): ProductSelection {
    val savedType = productSelectionRepository.observeSelectedProductType().first()
    val savedBabyId = productSelectionRepository.observeSelectedBabyProfileId().first()
    return when (savedType) {
      ProductType.MY_WEIGHT -> available.firstOrNull { it is ProductSelection.MyWeight }
      ProductType.BLOOD_PRESSURE -> available.firstOrNull { it is ProductSelection.BloodPressure }
      ProductType.BABY -> available.filterIsInstance<ProductSelection.Baby>()
        .firstOrNull { it.profile.id == savedBabyId }
        ?: available.firstOrNull { it is ProductSelection.Baby }
        ?: available.firstOrNull { it is ProductSelection.BabyScale }
    } ?: ProductSelection.MyWeight
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
    var USE_SAMPLE_PRODUCTS = false
  }
}
