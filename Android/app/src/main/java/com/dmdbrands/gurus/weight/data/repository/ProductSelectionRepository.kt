package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.UserDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Per-account product-selection state stored alongside the user's other account
 * preferences in [UserDataStore]. Switching accounts naturally surfaces the
 * other account's saved pick; signing out removes the account and its pick with it.
 */
class ProductSelectionRepository @Inject constructor(
    private val userDataStore: UserDataStore,
    private val babyProfileDao: BabyProfileDao,
    private val deviceDao: DeviceDao,
) : IProductSelectionRepository {

    override fun observeSelectedProductType(): Flow<ProductType> =
        userDataStore.selectedProductTypeForCurrentAccountFlow.map { raw ->
            if (raw.isBlank()) {
                ProductType.MY_WEIGHT
            } else {
                runCatching { ProductType.valueOf(raw) }.getOrDefault(ProductType.MY_WEIGHT)
            }
        }

    override fun observeSelectedBabyProfileId(): Flow<String?> =
        userDataStore.selectedBabyProfileIdForCurrentAccountFlow

    override fun observeHasUserSelected(): Flow<Boolean> =
        userDataStore.selectedProductTypeForCurrentAccountFlow.map { it.isNotBlank() }

    override suspend fun saveSelectedProductType(productType: ProductType) {
        val accountId = userDataStore.currentAccountIdFlow.first()
        if (accountId == null) {
            AppLog.w(TAG, "No active account; skipping saveSelectedProductType")
            return
        }
        userDataStore.setSelectedProductType(accountId, productType.name)
    }

    override suspend fun saveSelectedBabyProfileId(profileId: String?) {
        val accountId = userDataStore.currentAccountIdFlow.first()
        if (accountId == null) {
            AppLog.w(TAG, "No active account; skipping saveSelectedBabyProfileId")
            return
        }
        userDataStore.setSelectedBabyProfileId(accountId, profileId.orEmpty())
    }

    override suspend fun getBabyProfiles(accountId: String): List<BabyProfile> =
        babyProfileDao.observeByAccountId(accountId).first().map { entity ->
            BabyProfile(
                id = entity.babyId,
                name = entity.name,
                birthdate = entity.birthdate,
                accountId = entity.accountId,
            )
        }

    override suspend fun hasBpmDevice(accountId: String): Boolean =
        deviceDao.getDevicesByTypeWithAccount("BPM", accountId).first().isNotEmpty()

    private companion object {
        const val TAG = "ProductSelectionRepo"
    }
}
