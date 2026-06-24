package com.dmdbrands.gurus.weight.data.storage.db.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.account
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.bodyScale
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.bpmDevice
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.device
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.deviceDetails
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.deviceMeta
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.insertFullAccount
import com.dmdbrands.gurus.weight.data.storage.db.dao.DaoTestFixtures.r4Preference
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DeviceDaoTest : BaseDaoTest() {

    private suspend fun insertParentAccount(accountId: String = "acc-1") {
        accountDao.insertFullAccount(account(id = accountId, email = "$accountId@test.com"))
    }

    // -------------------------------------------------------------------------
    // insertDevice (DeviceEntity)
    // -------------------------------------------------------------------------

    @Test
    fun insertDevice_storesAndRetrievesEntity() = runTest {
        insertParentAccount()
        val dev = device()
        deviceDao.insertDevice(dev)

        val result = deviceDao.getDevice("dev-1")
        assertThat(result).isNotNull()
        assertThat(result?.device).isEqualTo(dev)
    }

    @Test
    fun insertDevice_replacesOnDuplicateId() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(nickname = "Old Name"))
        deviceDao.insertDevice(device(nickname = "New Name"))

        val result = deviceDao.getDevice("dev-1")
        assertThat(result?.device?.nickname).isEqualTo("New Name")
    }

    // -------------------------------------------------------------------------
    // insertDevice (DeviceDetails @Transaction)
    // -------------------------------------------------------------------------

    @Test
    fun insertDeviceDetails_storesAllSubEntities() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(
            deviceDetails(includeScale = true, includeBpm = true, includeMeta = true, includeR4 = true)
        )

        val result = deviceDao.getDevice("dev-1")
        assertThat(result).isNotNull()
        assertThat(result?.scale).isNotNull()
        assertThat(result?.bpm).isNotNull()
        assertThat(result?.meta).isNotNull()
        assertThat(result?.r4Preference).isNotNull()
    }

    @Test
    fun insertDeviceDetails_withNullOptionals() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(
            deviceDetails(includeScale = false, includeBpm = false, includeMeta = false, includeR4 = false)
        )

        val result = deviceDao.getDevice("dev-1")
        assertThat(result).isNotNull()
        assertThat(result?.scale).isNull()
        assertThat(result?.bpm).isNull()
        assertThat(result?.meta).isNull()
        assertThat(result?.r4Preference).isNull()
    }

    // -------------------------------------------------------------------------
    // getDevice
    // -------------------------------------------------------------------------

    @Test
    fun getDevice_returnsNullForMissingId() = runTest {
        assertThat(deviceDao.getDevice("nonexistent")).isNull()
    }

    // -------------------------------------------------------------------------
    // getDevices (Flow)
    // -------------------------------------------------------------------------

    @Test
    fun getDevices_returnsAllForAccount() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(id = "dev-1"))
        deviceDao.insertDevice(device(id = "dev-2"))

        assertThat(deviceDao.getDevices("acc-1").first()).hasSize(2)
    }

    @Test
    fun getDevices_emptyForDifferentAccount() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(device(id = "dev-1", accountId = "acc-1"))

        assertThat(deviceDao.getDevices("acc-2").first()).isEmpty()
    }

    @Test
    fun getDevices_emitsUpdatesAfterInsert() = runTest {
        insertParentAccount()

        assertThat(deviceDao.getDevices("acc-1").first()).isEmpty()
        deviceDao.insertDevice(device(id = "dev-1"))
        assertThat(deviceDao.getDevices("acc-1").first()).hasSize(1)
        deviceDao.insertDevice(device(id = "dev-2"))
        assertThat(deviceDao.getDevices("acc-1").first()).hasSize(2)
    }

    // -------------------------------------------------------------------------
    // Lookup queries
    // -------------------------------------------------------------------------

    @Test
    fun getDeviceByMac_returnsMatch() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(mac = "AA:BB:CC:DD:EE:FF"))

        assertThat(deviceDao.getDeviceByMac("AA:BB:CC:DD:EE:FF", "acc-1")).isNotNull()
    }

    @Test
    fun getDeviceByMac_returnsNullForWrongAccount() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(device(mac = "AA:BB:CC:DD:EE:FF", accountId = "acc-1"))

        assertThat(deviceDao.getDeviceByMac("AA:BB:CC:DD:EE:FF", "acc-2")).isNull()
    }

    @Test
    fun getDeviceByPeripheralId_returnsMatch() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(peripheralIdentifier = "peripheral-1"))

        assertThat(deviceDao.getDeviceByPeripheralId("peripheral-1")).isNotNull()
    }

    @Test
    fun getDeviceByPeripheralId_returnsNullForMissing() = runTest {
        assertThat(deviceDao.getDeviceByPeripheralId("nonexistent")).isNull()
    }

    @Test
    fun getDeviceByBroadcastId_returnsMatch() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(broadcastId = 12345L))

        assertThat(deviceDao.getDeviceByBroadcastId("12345", "acc-1")).isNotNull()
    }

    @Test
    fun getDeviceByBroadcastIdString_returnsMatch() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(broadcastIdString = "broadcast-1"))

        assertThat(deviceDao.getDeviceByBroadcastIdString("broadcast-1", "acc-1")).isNotNull()
    }

    @Test
    fun getDeviceByBroadcastId_returnsNullForWrongAccount() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(device(broadcastId = 12345L, accountId = "acc-1"))

        assertThat(deviceDao.getDeviceByBroadcastId("12345", "acc-2")).isNull()
    }

    @Test
    fun getDeviceByBroadcastIdString_returnsNullForWrongAccount() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(device(broadcastIdString = "broadcast-1", accountId = "acc-1"))

        assertThat(deviceDao.getDeviceByBroadcastIdString("broadcast-1", "acc-2")).isNull()
    }

    @Test
    fun getDevicesByTypeWithAccount_filtersCorrectly() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(id = "dev-1", deviceType = "scale"))
        deviceDao.insertDevice(device(id = "dev-2", deviceType = "bpm"))

        val result = deviceDao.getDevicesByTypeWithAccount("scale", "acc-1").first()
        assertThat(result).hasSize(1)
        assertThat(result[0].device.deviceType).isEqualTo("scale")
    }

    @Test
    fun getDevicesByTypeWithAccount_returnsEmptyForNonMatchingType() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(deviceType = "scale"))

        assertThat(deviceDao.getDevicesByTypeWithAccount("bpm", "acc-1").first()).isEmpty()
    }

    @Test
    fun getDevicesByTypeWithAccount_returnsEmptyForWrongAccount() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(device(deviceType = "scale", accountId = "acc-1"))

        assertThat(deviceDao.getDevicesByTypeWithAccount("scale", "acc-2").first()).isEmpty()
    }

    @Test
    fun getDevicesByTypeWithAccount_emitsUpdatesAfterInsert() = runTest {
        insertParentAccount()

        assertThat(deviceDao.getDevicesByTypeWithAccount("scale", "acc-1").first()).isEmpty()
        deviceDao.insertDevice(device(id = "dev-1", deviceType = "scale"))
        assertThat(deviceDao.getDevicesByTypeWithAccount("scale", "acc-1").first()).hasSize(1)
    }

    // -------------------------------------------------------------------------
    // Update operations
    // -------------------------------------------------------------------------

    @Test
    fun updateDevice_modifiesExistingEntity() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(nickname = "Old"))

        val updated = device(nickname = "New")
        deviceDao.updateDevice(updated)

        assertThat(deviceDao.getDevice("dev-1")?.device?.nickname).isEqualTo("New")
    }

    @Test
    fun updateSyncStatus_updatesFlag() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(isSynced = false))

        assertThat(deviceDao.updateSyncStatus("dev-1", true)).isEqualTo(1)
        assertThat(deviceDao.getDevice("dev-1")?.device?.isSynced).isTrue()
    }

    @Test
    fun updateSyncStatus_togglesBothDirections() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(isSynced = false))

        deviceDao.updateSyncStatus("dev-1", true)
        assertThat(deviceDao.getDevice("dev-1")?.device?.isSynced).isTrue()

        deviceDao.updateSyncStatus("dev-1", false)
        assertThat(deviceDao.getDevice("dev-1")?.device?.isSynced).isFalse()
    }

    @Test
    fun updateSyncStatus_returnsZeroForMissingDevice() = runTest {
        assertThat(deviceDao.updateSyncStatus("nonexistent", true)).isEqualTo(0)
    }

    @Test
    fun updateDeletionStatus_updatesFlag() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(isDeleted = false))

        assertThat(deviceDao.updateDeletionStatus("dev-1", true)).isEqualTo(1)
        assertThat(deviceDao.getDevice("dev-1")?.device?.isDeleted).isTrue()
    }

    @Test
    fun updateDeletionStatus_togglesBothDirections() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(isDeleted = false))

        deviceDao.updateDeletionStatus("dev-1", true)
        assertThat(deviceDao.getDevice("dev-1")?.device?.isDeleted).isTrue()

        deviceDao.updateDeletionStatus("dev-1", false)
        assertThat(deviceDao.getDevice("dev-1")?.device?.isDeleted).isFalse()
    }

    @Test
    fun updateDeletionStatus_returnsZeroForMissingDevice() = runTest {
        assertThat(deviceDao.updateDeletionStatus("nonexistent", true)).isEqualTo(0)
    }

    @Test
    fun updateNickname_updatesField() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(nickname = "Old Name"))

        assertThat(deviceDao.updateNickname("dev-1", "New Name")).isEqualTo(1)
        assertThat(deviceDao.getDevice("dev-1")?.device?.nickname).isEqualTo("New Name")
    }

    @Test
    fun updateNickname_returnsZeroForMissingDevice() = runTest {
        assertThat(deviceDao.updateNickname("nonexistent", "Name")).isEqualTo(0)
    }

    @Test
    fun updateToken_updatesField() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(token = null))

        assertThat(deviceDao.updateToken("dev-1", "new-token")).isEqualTo(1)
        assertThat(deviceDao.getDevice("dev-1")?.device?.token).isEqualTo("new-token")
    }

    @Test
    fun updateToken_returnsZeroForMissingDevice() = runTest {
        assertThat(deviceDao.updateToken("nonexistent", "token")).isEqualTo(0)
    }

    @Test
    fun updateHasServerID_updatesFlag() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(hasServerID = false))

        assertThat(deviceDao.updateHasServerID("dev-1", true)).isEqualTo(1)
        assertThat(deviceDao.getDevice("dev-1")?.device?.hasServerID).isTrue()
    }

    @Test
    fun updateHasServerID_togglesBothDirections() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(hasServerID = false))

        deviceDao.updateHasServerID("dev-1", true)
        assertThat(deviceDao.getDevice("dev-1")?.device?.hasServerID).isTrue()

        deviceDao.updateHasServerID("dev-1", false)
        assertThat(deviceDao.getDevice("dev-1")?.device?.hasServerID).isFalse()
    }

    @Test
    fun updateHasServerID_returnsZeroForMissingDevice() = runTest {
        assertThat(deviceDao.updateHasServerID("nonexistent", true)).isEqualTo(0)
    }

    @Test
    fun updateDevice_transactionUpdatesAllSubEntities() = runTest {
        insertParentAccount()
        val details = deviceDetails(includeScale = true, includeMeta = true)
        deviceDao.insertDevice(details)

        val updatedDetails = DeviceDetails(
            device = details.device.copy(nickname = "Updated Scale"),
            scale = details.scale?.copy(bodyComp = false),
            bpm = null,
            meta = details.meta?.copy(firmwareRevision = "2.0.0"),
            r4Preference = null,
        )
        deviceDao.updateDevice(updatedDetails)

        val result = deviceDao.getDevice("dev-1")
        assertThat(result?.device?.nickname).isEqualTo("Updated Scale")
        assertThat(result?.scale?.bodyComp).isFalse()
        assertThat(result?.meta?.firmwareRevision).isEqualTo("2.0.0")
    }

    @Test
    fun updateDevice_transactionWithNullSubEntity_preservesExistingRow() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(
            deviceDetails(includeScale = true, includeBpm = true, includeMeta = true, includeR4 = true)
        )

        // Update with bpm=null and r4=null — DAO uses ?.let{} so nulls are skipped, not deleted
        val updatedDetails = DeviceDetails(
            device = device(nickname = "Updated"),
            scale = bodyScale(bodyComp = false),
            bpm = null,
            meta = deviceMeta(firmwareRevision = "2.0.0"),
            r4Preference = null,
        )
        deviceDao.updateDevice(updatedDetails)

        val result = deviceDao.getDevice("dev-1")
        assertThat(result?.device?.nickname).isEqualTo("Updated")
        assertThat(result?.scale?.bodyComp).isFalse()
        assertThat(result?.meta?.firmwareRevision).isEqualTo("2.0.0")
        // Existing bpm and r4 rows are preserved — null in update payload means "skip", not "delete"
        assertThat(result?.bpm).isNotNull()
        assertThat(result?.r4Preference).isNotNull()
    }

    @Test
    fun updateScale_modifiesExistingScale() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertScale(bodyScale(bodyComp = true))

        deviceDao.updateScale(bodyScale(bodyComp = false))

        assertThat(deviceDao.getDevice("dev-1")?.scale?.bodyComp).isFalse()
    }

    @Test
    fun updateBpm_modifiesExistingBpm() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertBpm(bpmDevice(hasNumericUsers = false))

        deviceDao.updateBpm(bpmDevice(hasNumericUsers = true))

        assertThat(deviceDao.getDevice("dev-1")?.bpm?.hasNumericUsers).isTrue()
    }

    @Test
    fun updateMeta_modifiesExistingMeta() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertMeta(deviceMeta(firmwareRevision = "1.0.0"))

        deviceDao.updateMeta(deviceMeta(firmwareRevision = "2.0.0"))

        assertThat(deviceDao.getDevice("dev-1")?.meta?.firmwareRevision).isEqualTo("2.0.0")
    }

    @Test
    fun updateR4Preference_modifiesExistingR4() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertR4Preference(r4Preference(shouldMeasureImpedance = true))

        deviceDao.updateR4Preference(r4Preference(shouldMeasureImpedance = false))

        assertThat(deviceDao.getDevice("dev-1")?.r4Preference?.shouldMeasureImpedance).isFalse()
    }

    // -------------------------------------------------------------------------
    // Delete operations
    // -------------------------------------------------------------------------

    @Test
    fun deleteDevice_byEntity() = runTest {
        insertParentAccount()
        val dev = device()
        deviceDao.insertDevice(dev)

        deviceDao.deleteDevice(dev)
        assertThat(deviceDao.getDevice("dev-1")).isNull()
    }

    @Test
    fun deleteDevice_byId() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())

        deviceDao.deleteDevice("dev-1")
        assertThat(deviceDao.getDevice("dev-1")).isNull()
    }

    @Test
    fun deleteAllDevicesForAccount_removesOnlyForAccount() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(device(id = "dev-1", accountId = "acc-1"))
        deviceDao.insertDevice(device(id = "dev-2", accountId = "acc-2"))

        assertThat(deviceDao.deleteAllDevicesForAccount("acc-1")).isEqualTo(1)
        assertThat(deviceDao.getDevice("dev-1")).isNull()
        assertThat(deviceDao.getDevice("dev-2")).isNotNull()
    }

    @Test
    fun deleteDevice_cascadesToSubEntities() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(
            deviceDetails(includeScale = true, includeBpm = true, includeMeta = true, includeR4 = true)
        )

        deviceDao.deleteDevice("dev-1")
        assertThat(deviceDao.getDevice("dev-1")).isNull()

        // Re-insert ONLY the device entity (no sub-entities) to verify cascade deleted them
        deviceDao.insertDevice(device())
        val reinserted = deviceDao.getDevice("dev-1")
        assertThat(reinserted).isNotNull()
        assertThat(reinserted?.scale).isNull()
        assertThat(reinserted?.bpm).isNull()
        assertThat(reinserted?.meta).isNull()
        assertThat(reinserted?.r4Preference).isNull()
    }

    @Test
    fun deleteAllDevicesForAccount_returnsZeroForEmptyAccount() = runTest {
        insertParentAccount()
        assertThat(deviceDao.deleteAllDevicesForAccount("acc-1")).isEqualTo(0)
    }

    @Test
    fun deleteAccount_cascadesToDevices() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(deviceDetails())

        accountDao.deleteAccountById("acc-1")
        assertThat(deviceDao.getDevice("dev-1")).isNull()
    }

    // -------------------------------------------------------------------------
    // Unsynced
    // -------------------------------------------------------------------------

    @Test
    fun getUnsyncedDevicesList_returnsUnsynced() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(id = "dev-1", isSynced = false))
        deviceDao.insertDevice(device(id = "dev-2", isSynced = true))

        val result = deviceDao.getUnsyncedDevicesList()
        assertThat(result).hasSize(1)
        assertThat(result[0].device.id).isEqualTo("dev-1")
    }

    @Test
    fun getUnsyncedDevicesList_emptyWhenAllSynced() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device(isSynced = true))

        assertThat(deviceDao.getUnsyncedDevicesList()).isEmpty()
    }

    @Test
    fun getUnsyncedDevicesList_returnsAcrossAllAccounts() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(device(id = "dev-1", accountId = "acc-1", isSynced = false))
        deviceDao.insertDevice(device(id = "dev-2", accountId = "acc-2", isSynced = false))
        deviceDao.insertDevice(device(id = "dev-3", accountId = "acc-2", isSynced = true))

        val result = deviceDao.getUnsyncedDevicesList()
        assertThat(result).hasSize(2)
        assertThat(result.map { it.device.id }).containsExactly("dev-1", "dev-2")
    }

    // -------------------------------------------------------------------------
    // Sub-entity insert
    // -------------------------------------------------------------------------

    @Test
    fun insertScale_storesBodyScaleEntity() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertScale(bodyScale())

        assertThat(deviceDao.getDevice("dev-1")?.scale?.bodyComp).isTrue()
    }

    @Test
    fun insertBpm_storesBpmEntity() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertBpm(bpmDevice())

        assertThat(deviceDao.getDevice("dev-1")?.bpm).isNotNull()
    }

    @Test
    fun insertMeta_storesMetaDataEntity() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertMeta(deviceMeta())

        assertThat(deviceDao.getDevice("dev-1")?.meta?.modelNumber).isEqualTo("Model-A")
    }

    @Test
    fun insertR4Preference_storesR4Entity() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertR4Preference(r4Preference())

        assertThat(deviceDao.getDevice("dev-1")?.r4Preference?.shouldMeasureImpedance).isTrue()
    }

    // -------------------------------------------------------------------------
    // getDeviceByMacWithAccount
    // -------------------------------------------------------------------------

    @Test
    fun getDeviceByMacWithAccount_returnsDetailsWithRelations() = runTest {
        insertParentAccount()
        // Default deviceDetails: includeScale=true, includeBpm=false, includeMeta=true, includeR4=false
        deviceDao.insertDevice(deviceDetails())

        val result = deviceDao.getDeviceByMacWithAccount("AA:BB:CC:DD:EE:FF", "acc-1")
        assertThat(result).isNotNull()
        assertThat(result?.scale).isNotNull()
        assertThat(result?.meta).isNotNull()
        assertThat(result?.bpm).isNull()
        assertThat(result?.r4Preference).isNull()
    }

    @Test
    fun getDeviceByMacWithAccount_returnsNullForWrongAccount() = runTest {
        insertParentAccount("acc-1")
        insertParentAccount("acc-2")
        deviceDao.insertDevice(deviceDetails(accountId = "acc-1"))

        assertThat(deviceDao.getDeviceByMacWithAccount("AA:BB:CC:DD:EE:FF", "acc-2")).isNull()
    }

    @Test
    fun getDeviceByMacWithAccount_returnsNullForMissingMac() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(deviceDetails())

        assertThat(deviceDao.getDeviceByMacWithAccount("00:00:00:00:00:00", "acc-1")).isNull()
    }

    // -------------------------------------------------------------------------
    // Sub-entity REPLACE conflict strategy
    // -------------------------------------------------------------------------

    @Test
    fun insertScale_replacesOnDuplicateId() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertScale(bodyScale(bodyComp = true))
        deviceDao.insertScale(bodyScale(bodyComp = false))

        assertThat(deviceDao.getDevice("dev-1")?.scale?.bodyComp).isFalse()
    }

    @Test
    fun insertBpm_replacesOnDuplicateId() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertBpm(bpmDevice(hasNumericUsers = false))
        deviceDao.insertBpm(bpmDevice(hasNumericUsers = true))

        assertThat(deviceDao.getDevice("dev-1")?.bpm?.hasNumericUsers).isTrue()
    }

    @Test
    fun insertMeta_replacesOnDuplicateId() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertMeta(deviceMeta(modelNumber = "Model-A"))
        deviceDao.insertMeta(deviceMeta(modelNumber = "Model-B"))

        assertThat(deviceDao.getDevice("dev-1")?.meta?.modelNumber).isEqualTo("Model-B")
    }

    @Test
    fun insertR4Preference_replacesOnDuplicateId() = runTest {
        insertParentAccount()
        deviceDao.insertDevice(device())
        deviceDao.insertR4Preference(r4Preference(shouldMeasureImpedance = true))
        deviceDao.insertR4Preference(r4Preference(shouldMeasureImpedance = false))

        assertThat(deviceDao.getDevice("dev-1")?.r4Preference?.shouldMeasureImpedance).isFalse()
    }
}
