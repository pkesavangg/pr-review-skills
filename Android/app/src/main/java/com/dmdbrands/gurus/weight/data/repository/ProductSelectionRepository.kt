package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.storage.datastore.ProductSelectionDataStore
import com.dmdbrands.gurus.weight.data.storage.db.dao.BabyProfileDao
import com.dmdbrands.gurus.weight.data.storage.db.dao.DeviceDao
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IProductSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ProductSelectionRepository @Inject constructor(
    private val productSelectionDataStore: ProductSelectionDataStore,
    private val babyProfileDao: BabyProfileDao,
    private val deviceDao: DeviceDao,
) : IProductSelectionRepository {

    override fun observeSelectedProductType(): Flow<ProductType> =
        productSelectionDataStore.observeSelectedProductType()

    override fun observeSelectedBabyProfileId(): Flow<String?> =
        productSelectionDataStore.observeSelectedBabyProfileId()

    override suspend fun saveSelectedProductType(productType: ProductType) {
        productSelectionDataStore.saveSelectedProductType(productType)
    }

    override suspend fun saveSelectedBabyProfileId(profileId: String?) {
        productSelectionDataStore.saveSelectedBabyProfileId(profileId)
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
}
