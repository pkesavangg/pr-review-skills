package com.dmdbrands.gurus.weight.domain.repository

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import kotlinx.coroutines.flow.Flow

interface IProductSelectionRepository {

    fun observeSelectedProductType(): Flow<ProductType>

    fun observeSelectedBabyProfileId(): Flow<String?>

    fun observeHasUserSelected(): Flow<Boolean>

    suspend fun saveSelectedProductType(productType: ProductType)

    suspend fun saveSelectedBabyProfileId(profileId: String?)

    /**
     * Clears the saved product selection (product type + baby profile) for the active account so
     * the dashboard opens in snapshot (multi-product) mode instead of pinning a single product.
     */
    suspend fun clearSelectedProduct()

    suspend fun getBabyProfiles(accountId: String): List<BabyProfile>

    suspend fun hasBpmDevice(accountId: String): Boolean

    suspend fun hasBabyScaleDevice(accountId: String): Boolean
}
