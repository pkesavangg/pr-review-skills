package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.model.common.ProductSelection
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IProductSelectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider

class ProductSelectionManager @Inject constructor(
  private val productSelectionRepository: IProductSelectionRepository,
  // Provider breaks a potential Hilt init cycle (AccountService graph is large).
  private val accountService: Provider<IAccountService>,
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

    // The account's productTypes (MOB-377) is the source of truth for which products the
    // account owns. A fresh baby-scale (or BP) signup has the product on the account before
    // any baby profile or paired device exists locally, so derive availability from it too —
    // otherwise the dashboard wrongly falls back to weight-only. (MOB-592)
    val productTypes = runCatching { accountService.get().getCurrentAccount()?.productTypes }
      .getOrNull().orEmpty()
    // productTypes is the account's owned-product list (MOB-377). Surface a product only
    // when the account owns it — a baby-only (or BP-only) account must NOT show My Weight.
    // Legacy/pre-Phase-2 accounts report an empty list, so default them to weight. (MOB-592)
    val accountHasWeight = productTypes.isEmpty() || ProductType.MY_WEIGHT.apiValue in productTypes
    val accountHasBp = ProductType.BLOOD_PRESSURE.apiValue in productTypes
    val accountHasBaby = ProductType.BABY.apiValue in productTypes

    val products = mutableListOf<ProductSelection>()

    if (accountHasWeight) {
      products.add(ProductSelection.MyWeight)
    }

    if (hasBpm || accountHasBp) {
      products.add(ProductSelection.BloodPressure)
    }

    if (babyProfiles.isNotEmpty()) {
      babyProfiles.forEach { profile ->
        products.add(ProductSelection.Baby(profile))
      }
    } else if (hasBabyScale || accountHasBaby) {
      // Owns the baby product but has no profiles yet (fresh baby-scale signup, or
      // deleted last baby): keep "Baby Scale" so the dashboard shows the add-a-baby
      // empty state and taps route to the add-baby flow. (MOB-416 / MOB-592)
      products.add(ProductSelection.BabyScale)
    }

    // Never leave the dashboard with no product (e.g. unexpected/empty productTypes).
    if (products.isEmpty()) {
      products.add(ProductSelection.MyWeight)
    }

    _availableProducts.value = products
    AppLog.d(TAG, "Available: $products")

    _selectedProduct.value = restoreSavedSelection(products)
    _isSnapshotMode.value = !productSelectionRepository.observeHasUserSelected().first()
  }

  override suspend fun persistProductForSetup(productType: ProductType) {
    // Submit to the server (spec §2.19). Swallow failures: a network hiccup here must never
    // disrupt the just-completed device setup — the next account sync will reconcile.
    runCatching { accountService.get().addProduct(productType) }
      .onFailure { AppLog.e(TAG, "persistProductForSetup failed for ${productType.apiValue}", it) }
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
    } ?: available.firstOrNull() ?: ProductSelection.MyWeight
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
