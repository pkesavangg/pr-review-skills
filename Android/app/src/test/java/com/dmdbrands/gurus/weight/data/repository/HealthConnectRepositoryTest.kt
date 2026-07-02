package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.data.api.HealthConnectIntegrationRequest
import com.dmdbrands.gurus.weight.data.api.HealthConnectSyncEntry
import com.dmdbrands.gurus.weight.data.api.IHealthConnectAPI
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectData
import com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStore
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegratedDeviceInfo
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegrationData
import com.dmdbrands.gurus.weight.data.storage.datastore.ProtoIntegrationOperationType
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationData
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationOperationType
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationPreferences
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import java.io.IOException

class HealthConnectRepositoryTest {

    companion object {
        private const val ACCOUNT_ID = "acc-123"
        private const val DEVICE_ID = "device-abc"
        private const val ASSIGNED_TO = "acc-assigned"
    }

    @MockK
    lateinit var accountRepository: IAccountRepository

    @MockK
    lateinit var healthConnectAPI: IHealthConnectAPI

    @MockK
    lateinit var healthConnectDataStore: HealthConnectDataStore

    private lateinit var repository: HealthConnectRepository

    private val mockHealthConnectData: HealthConnectData = mockk(relaxed = true)
    private val mockAccount: Account = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { mockAccount.id } returns ACCOUNT_ID
        repository = HealthConnectRepository(accountRepository, healthConnectAPI, healthConnectDataStore)
    }

    // -----------------------------------------------------------------------
    // syncEntry
    // -----------------------------------------------------------------------

    @Test
    fun `syncEntry delegates to healthConnectAPI sync with correct entry`() = runTest {
        val entry = HealthConnectSyncEntry(
            type = "weight",
            sentAt = "2026-03-12T00:00:00Z",
            timestamp = "2026-03-12T00:00:00Z",
            weight = 80.0,
            bodyFat = null,
            muscleMass = null,
            water = null,
            bmi = null,
            data = null
        )
        coEvery { healthConnectAPI.sync(entry) } returns Unit

        repository.syncEntry(entry)

        coVerify { healthConnectAPI.sync(entry) }
    }

    @Test
    fun `syncEntry rethrows exception when API fails`() = runTest {
        val entry = mockk<HealthConnectSyncEntry>(relaxed = true)
        coEvery { healthConnectAPI.sync(entry) } throws IOException("Network error")

        assertFailsWith<IOException> {
            repository.syncEntry(entry)
        }
    }

    // -----------------------------------------------------------------------
    // updateOutOfSyncStatus
    // -----------------------------------------------------------------------

    @Test
    fun `updateOutOfSyncStatus calls updateOutOfSync on DataStore`() = runTest {
        coEvery { healthConnectDataStore.updateOutOfSync(ACCOUNT_ID, true) } returns Unit

        repository.updateOutOfSyncStatus(ACCOUNT_ID, true)

        coVerify { healthConnectDataStore.updateOutOfSync(ACCOUNT_ID, true) }
    }

    @Test
    fun `updateOutOfSyncStatus swallows exception when DataStore throws`() = runTest {
        coEvery { healthConnectDataStore.updateOutOfSync(ACCOUNT_ID, false) } throws RuntimeException("DataStore error")

        // Should not throw
        repository.updateOutOfSyncStatus(ACCOUNT_ID, false)
    }

    // -----------------------------------------------------------------------
    // getAccountDataMap
    // -----------------------------------------------------------------------

    @Test
    fun `getAccountDataMap returns map from DataStore`() = runTest {
        val expectedMap = mapOf(ACCOUNT_ID to mockHealthConnectData)
        coEvery { healthConnectDataStore.healthConnectData() } returns expectedMap

        val result = repository.getAccountDataMap()

        assertThat(result).isEqualTo(expectedMap)
        coVerify { healthConnectDataStore.healthConnectData() }
    }

    // -----------------------------------------------------------------------
    // addAccount
    // -----------------------------------------------------------------------

    @Test
    fun `addAccount calls setHealthConnectData with correct args`() = runTest {
        coEvery { healthConnectDataStore.setHealthConnectData(ACCOUNT_ID, mockHealthConnectData) } returns Unit

        repository.addAccount(ACCOUNT_ID, mockHealthConnectData)

        coVerify { healthConnectDataStore.setHealthConnectData(ACCOUNT_ID, mockHealthConnectData) }
    }

    // -----------------------------------------------------------------------
    // clearData
    // -----------------------------------------------------------------------

    @Test
    fun `clearData delegates to DataStore clearData`() = runTest {
        coEvery { healthConnectDataStore.clearData() } returns Unit

        repository.clearData()

        coVerify { healthConnectDataStore.clearData() }
    }

    // -----------------------------------------------------------------------
    // getAccountByID
    // -----------------------------------------------------------------------

    @Test
    fun `getAccountByID returns data from DataStore`() = runTest {
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns mockHealthConnectData

        val result = repository.getAccountByID(ACCOUNT_ID)

        assertThat(result).isEqualTo(mockHealthConnectData)
        coVerify { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) }
    }

    @Test
    fun `getAccountByID returns null when no data exists`() = runTest {
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns null

        val result = repository.getAccountByID(ACCOUNT_ID)

        assertThat(result).isNull()
    }

    // -----------------------------------------------------------------------
    // hasAccountData
    // -----------------------------------------------------------------------

    @Test
    fun `hasAccountData returns true when DataStore has data`() = runTest {
        coEvery { healthConnectDataStore.hasHealthConnectData(ACCOUNT_ID) } returns true

        val result = repository.hasAccountData(ACCOUNT_ID)

        assertThat(result).isTrue()
    }

    @Test
    fun `hasAccountData returns false when DataStore has no data`() = runTest {
        coEvery { healthConnectDataStore.hasHealthConnectData(ACCOUNT_ID) } returns false

        val result = repository.hasAccountData(ACCOUNT_ID)

        assertThat(result).isFalse()
    }

    // -----------------------------------------------------------------------
    // updateOutOfSync (direct)
    // -----------------------------------------------------------------------

    @Test
    fun `updateOutOfSync delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.updateOutOfSync(ACCOUNT_ID, true) } returns Unit

        repository.updateOutOfSync(ACCOUNT_ID, true)

        coVerify { healthConnectDataStore.updateOutOfSync(ACCOUNT_ID, true) }
    }

    // -----------------------------------------------------------------------
    // setOpen / getOpen
    // -----------------------------------------------------------------------

    @Test
    fun `setOpen delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.setOpen(ACCOUNT_ID, true) } returns Unit

        repository.setOpen(ACCOUNT_ID, true)

        coVerify { healthConnectDataStore.setOpen(ACCOUNT_ID, true) }
    }

    @Test
    fun `getOpen returns value from DataStore`() = runTest {
        coEvery { healthConnectDataStore.getOpen(ACCOUNT_ID) } returns true

        val result = repository.getOpen(ACCOUNT_ID)

        assertThat(result).isTrue()
    }

    // -----------------------------------------------------------------------
    // setHcIntegrationStatus
    // -----------------------------------------------------------------------

    @Test
    fun `setHcIntegrationStatus delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.setHcIntegrationStatus(ACCOUNT_ID, true) } returns Unit

        repository.setHcIntegrationStatus(ACCOUNT_ID, true)

        coVerify { healthConnectDataStore.setHcIntegrationStatus(ACCOUNT_ID, true) }
    }

    // -----------------------------------------------------------------------
    // updateAlertSeen
    // -----------------------------------------------------------------------

    @Test
    fun `updateAlertSeen calls DataStore and fetches updated account`() = runTest {
        coEvery { healthConnectDataStore.updateAlertSeen(ACCOUNT_ID, true) } returns Unit
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns mockHealthConnectData

        repository.updateAlertSeen(ACCOUNT_ID, true)

        coVerify { healthConnectDataStore.updateAlertSeen(ACCOUNT_ID, true) }
        coVerify { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) }
    }

    // -----------------------------------------------------------------------
    // setAssignedTo / getAssignedTo / clearAssignedTo
    // -----------------------------------------------------------------------

    @Test
    fun `setAssignedTo delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.setAssignedTo(ACCOUNT_ID, ASSIGNED_TO) } returns Unit

        repository.setAssignedTo(ACCOUNT_ID, ASSIGNED_TO)

        coVerify { healthConnectDataStore.setAssignedTo(ACCOUNT_ID, ASSIGNED_TO) }
    }

    @Test
    fun `getAssignedTo returns value from DataStore`() = runTest {
        coEvery { healthConnectDataStore.getAssignedTo(ACCOUNT_ID) } returns ASSIGNED_TO

        val result = repository.getAssignedTo(ACCOUNT_ID)

        assertThat(result).isEqualTo(ASSIGNED_TO)
    }

    @Test
    fun `clearAssignedTo delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.clearAssignedTo(ACCOUNT_ID) } returns Unit

        repository.clearAssignedTo(ACCOUNT_ID)

        coVerify { healthConnectDataStore.clearAssignedTo(ACCOUNT_ID) }
    }

    // -----------------------------------------------------------------------
    // updateModalState
    // -----------------------------------------------------------------------

    @Test
    fun `updateModalState delegates to DataStore`() = runTest {
        coEvery { healthConnectDataStore.updateModalState(ACCOUNT_ID, true) } returns Unit

        repository.updateModalState(ACCOUNT_ID, true)

        coVerify { healthConnectDataStore.updateModalState(ACCOUNT_ID, true) }
    }

    // -----------------------------------------------------------------------
    // setHcPermissions
    // -----------------------------------------------------------------------

    @Test
    fun `setHcPermissions delegates to DataStore setPermissions`() = runTest {
        val permissions = listOf("read:weight", "write:weight")
        coEvery { healthConnectDataStore.setPermissions(ACCOUNT_ID, permissions) } returns Unit

        repository.setHcPermissions(ACCOUNT_ID, permissions)

        coVerify { healthConnectDataStore.setPermissions(ACCOUNT_ID, permissions) }
    }

    // -----------------------------------------------------------------------
    // getHealthConnectDataFlow
    // -----------------------------------------------------------------------

    @Test
    fun `getHealthConnectDataFlow returns flow from DataStore`() = runTest {
        val flow = flowOf(mockHealthConnectData)
        every { healthConnectDataStore.getHealthConnectDataFlow(ACCOUNT_ID) } returns flow

        val result = repository.getHealthConnectDataFlow(ACCOUNT_ID)

        assertThat(result).isEqualTo(flow)
    }

    // -----------------------------------------------------------------------
    // saveIntegration
    // -----------------------------------------------------------------------

    @Test
    fun `saveIntegration calls API and saves locally on success`() = runTest {
        val integrationData = buildIntegrationData()
        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectAPI.saveIntegration(any()) } returns Unit
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.saveIntegration(integrationData)

        coVerify { healthConnectAPI.saveIntegration(any()) }
        coVerify { healthConnectDataStore.setIntegrationInfo(eq(ACCOUNT_ID), any()) }
    }

    @Test
    fun `saveIntegration saves locally when API fails`() = runTest {
        val integrationData = buildIntegrationData()
        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectAPI.saveIntegration(any()) } throws IOException("Network error")
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.saveIntegration(integrationData)

        coVerify { healthConnectDataStore.setIntegrationInfo(eq(ACCOUNT_ID), any()) }
    }

    @Test
    fun `saveIntegration returns early when no active account`() = runTest {
        val integrationData = buildIntegrationData()
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        repository.saveIntegration(integrationData)

        coVerify(exactly = 0) { healthConnectAPI.saveIntegration(any()) }
    }

    @Test
    fun `saveIntegration returns early when getActiveAccount throws`() = runTest {
        val integrationData = buildIntegrationData()
        every { accountRepository.getActiveAccount() } throws RuntimeException("Error")

        repository.saveIntegration(integrationData)

        coVerify(exactly = 0) { healthConnectAPI.saveIntegration(any()) }
    }

    // -----------------------------------------------------------------------
    // syncIntegration
    // -----------------------------------------------------------------------

    @Test
    fun `syncIntegration returns early when no active account`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        repository.syncIntegration(null)

        coVerify(exactly = 0) { healthConnectAPI.saveIntegration(any()) }
        coVerify(exactly = 0) { healthConnectAPI.removeIntegration(any()) }
    }

    @Test
    fun `syncIntegration with SAVE operation calls saveIntegration API`() = runTest {
        val integrationInfo = IntegratedDeviceInfo(
            operationType = IntegrationOperationType.SAVE.value,
            scopes = buildIntegrationData(),
            isCurrentDeviceDeleted = false,
        )
        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectAPI.saveIntegration(any()) } returns Unit
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.syncIntegration(integrationInfo)

        coVerify { healthConnectAPI.saveIntegration(any()) }
    }

    @Test
    fun `syncIntegration with REMOVE operation calls removeIntegration API`() = runTest {
        val integrationInfo = IntegratedDeviceInfo(
            operationType = IntegrationOperationType.REMOVE.value,
            scopes = buildIntegrationData(),
            isCurrentDeviceDeleted = false,
        )
        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectAPI.removeIntegration(any()) } returns Unit
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.syncIntegration(integrationInfo)

        coVerify { healthConnectAPI.removeIntegration(any()) }
    }

    @Test
    fun `syncIntegration with null integrationInfo uses stored data`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns mockHealthConnectData
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.syncIntegration(null)

        coVerify { healthConnectDataStore.setIntegrationInfo(eq(ACCOUNT_ID), any()) }
    }

    // -----------------------------------------------------------------------
    // removeServerHcIntegration
    // -----------------------------------------------------------------------

    @Test
    fun `removeServerHcIntegration returns early when account is null`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(null)

        repository.removeServerHcIntegration(DEVICE_ID)

        coVerify(exactly = 0) { healthConnectAPI.removeIntegration(any()) }
    }

    @Test
    fun `removeServerHcIntegration returns early when no integration info`() = runTest {
        val dataWithoutIntegration: HealthConnectData = mockk(relaxed = true)
        every { dataWithoutIntegration.integrationInfo } returns null
        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithoutIntegration

        repository.removeServerHcIntegration(DEVICE_ID)

        coVerify(exactly = 0) { healthConnectAPI.removeIntegration(any()) }
    }

    @Test
    fun `removeServerHcIntegration calls API and stores result on success`() = runTest {
        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns mockHealthConnectData
        coEvery { healthConnectAPI.removeIntegration(DEVICE_ID) } returns Unit
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.removeServerHcIntegration(DEVICE_ID)

        coVerify { healthConnectAPI.removeIntegration(DEVICE_ID) }
    }

    // -----------------------------------------------------------------------
    // setStoredIntegrationData
    // -----------------------------------------------------------------------

    @Test
    fun `setStoredIntegrationData does nothing when integrationInfo is null`() = runTest {
        repository.setStoredIntegrationData(ACCOUNT_ID, null)

        coVerify(exactly = 0) { healthConnectDataStore.setHealthConnectData(any(), any()) }
    }

    @Test
    fun `setStoredIntegrationData completes without throwing when integrationInfo non-null`() = runTest {
        val integrationInfo = IntegratedDeviceInfo(
            operationType = IntegrationOperationType.SAVE.value,
            scopes = buildIntegrationData(),
        )
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns mockHealthConnectData
        coEvery { healthConnectDataStore.setHealthConnectData(any(), any()) } returns Unit

        // Should complete without throwing — proto builder chain is swallowed if it fails
        repository.setStoredIntegrationData(ACCOUNT_ID, integrationInfo)
    }

    // -----------------------------------------------------------------------
    // getStoredIntegrationData
    // -----------------------------------------------------------------------

    @Test
    fun `getStoredIntegrationData returns IntegratedDeviceInfo from stored data`() = runTest {
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns mockHealthConnectData

        val result = repository.getStoredIntegrationData(ACCOUNT_ID)

        assertThat(result).isNotNull()
    }

    @Test
    fun `getStoredIntegrationData returns null on exception`() = runTest {
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } throws RuntimeException("DB error")

        val result = repository.getStoredIntegrationData(ACCOUNT_ID)

        assertThat(result).isNull()
    }

    // -----------------------------------------------------------------------
    // setHealthConnectIntegrationStatus
    // -----------------------------------------------------------------------

    @Test
    fun `setHealthConnectIntegrationStatus sets integrated true`() = runTest {
        coEvery { healthConnectDataStore.setHcIntegrationStatus(ACCOUNT_ID, true) } returns Unit
        coEvery { healthConnectDataStore.setAssignedTo(ACCOUNT_ID, ACCOUNT_ID) } returns Unit

        repository.setHealthConnectIntegrationStatus(ACCOUNT_ID, true)

        coVerify { healthConnectDataStore.setHcIntegrationStatus(ACCOUNT_ID, true) }
        coVerify { healthConnectDataStore.setAssignedTo(ACCOUNT_ID, ACCOUNT_ID) }
    }

    @Test
    fun `setHealthConnectIntegrationStatus sets integrated false when assignedTo matches`() = runTest {
        val dataWithMatchingAssignedTo: HealthConnectData = mockk(relaxed = true)
        every { dataWithMatchingAssignedTo.assignedTo } returns ACCOUNT_ID
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithMatchingAssignedTo
        coEvery { healthConnectDataStore.setHcIntegrationStatus(ACCOUNT_ID, false) } returns Unit
        coEvery { healthConnectDataStore.clearAssignedTo(ACCOUNT_ID) } returns Unit

        repository.setHealthConnectIntegrationStatus(ACCOUNT_ID, false)

        coVerify { healthConnectDataStore.setHcIntegrationStatus(ACCOUNT_ID, false) }
        coVerify { healthConnectDataStore.clearAssignedTo(ACCOUNT_ID) }
    }

    @Test
    fun `setHealthConnectIntegrationStatus does not unset when assignedTo does not match`() = runTest {
        val dataWithDifferentAssignedTo: HealthConnectData = mockk(relaxed = true)
        every { dataWithDifferentAssignedTo.assignedTo } returns "other-account"
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithDifferentAssignedTo

        repository.setHealthConnectIntegrationStatus(ACCOUNT_ID, false)

        coVerify(exactly = 0) { healthConnectDataStore.setHcIntegrationStatus(any(), any()) }
    }

    @Test
    fun `setHealthConnectIntegrationStatus rethrows exception`() = runTest {
        coEvery { healthConnectDataStore.setHcIntegrationStatus(any(), any()) } throws RuntimeException("DataStore error")

        try {
            repository.setHealthConnectIntegrationStatus(ACCOUNT_ID, true)
            error("Expected exception not thrown")
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("DataStore error")
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // toDomain (ProtoIntegratedDeviceInfo) — exercised through removeServerHcIntegration
    // -----------------------------------------------------------------------

    @Test
    fun `removeServerHcIntegration converts PROTO_SAVE to domain SAVE via toDomain`() = runTest {
        val protoScopes = ProtoIntegrationData.newBuilder()
            .setDeviceId(DEVICE_ID)
            .addAllScopes(listOf("read:weight", "write:weight"))
            .build()
        val protoIntegrationInfo = ProtoIntegratedDeviceInfo.newBuilder()
            .setOperationType(ProtoIntegrationOperationType.PROTO_SAVE)
            .setScopes(protoScopes)
            .build()
        val dataWithProto: HealthConnectData = HealthConnectData.newBuilder()
            .setIntegrated(true)
            .setIntegrationInfo(protoIntegrationInfo)
            .addAllGrantedPermission(listOf("read:weight"))
            .build()

        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithProto
        coEvery { healthConnectAPI.removeIntegration(DEVICE_ID) } returns Unit
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.removeServerHcIntegration(DEVICE_ID)

        coVerify { healthConnectAPI.removeIntegration(DEVICE_ID) }
        coVerify {
            healthConnectDataStore.setIntegrationInfo(eq(ACCOUNT_ID), match {
                it.operationType == IntegrationOperationType.REMOVE.value &&
                    it.isCurrentDeviceDeleted
            })
        }
    }

    @Test
    fun `removeServerHcIntegration converts PROTO_REMOVE to domain REMOVE via toDomain`() = runTest {
        val protoScopes = ProtoIntegrationData.newBuilder()
            .setDeviceId(DEVICE_ID)
            .addAllScopes(listOf("read:weight"))
            .build()
        val protoIntegrationInfo = ProtoIntegratedDeviceInfo.newBuilder()
            .setOperationType(ProtoIntegrationOperationType.PROTO_REMOVE)
            .setScopes(protoScopes)
            .build()
        val dataWithProto: HealthConnectData = HealthConnectData.newBuilder()
            .setIntegrated(false)
            .setIntegrationInfo(protoIntegrationInfo)
            .addAllGrantedPermission(listOf("read:weight"))
            .build()

        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithProto
        coEvery { healthConnectAPI.removeIntegration(DEVICE_ID) } returns Unit
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.removeServerHcIntegration(DEVICE_ID)

        coVerify { healthConnectAPI.removeIntegration(DEVICE_ID) }
        coVerify {
            healthConnectDataStore.setIntegrationInfo(eq(ACCOUNT_ID), match {
                it.operationType == IntegrationOperationType.REMOVE.value &&
                    it.isCurrentDeviceDeleted
            })
        }
    }

    @Test
    fun `removeServerHcIntegration on API failure stores REMOVE with isCurrentDeviceDeleted false`() = runTest {
        val protoScopes = ProtoIntegrationData.newBuilder()
            .setDeviceId(DEVICE_ID)
            .addAllScopes(listOf("read:weight"))
            .build()
        val protoIntegrationInfo = ProtoIntegratedDeviceInfo.newBuilder()
            .setOperationType(ProtoIntegrationOperationType.PROTO_SAVE)
            .setScopes(protoScopes)
            .build()
        val dataWithProto: HealthConnectData = HealthConnectData.newBuilder()
            .setIntegrated(true)
            .setIntegrationInfo(protoIntegrationInfo)
            .addAllGrantedPermission(listOf("read:weight"))
            .build()

        every { accountRepository.getActiveAccount() } returns flowOf(mockAccount)
        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithProto
        coEvery { healthConnectAPI.removeIntegration(any()) } throws IOException("Network error")
        coEvery { healthConnectDataStore.setIntegrationInfo(any(), any()) } returns Unit

        repository.removeServerHcIntegration(DEVICE_ID)

        coVerify {
            healthConnectDataStore.setIntegrationInfo(eq(ACCOUNT_ID), match {
                it.operationType == IntegrationOperationType.REMOVE.value &&
                    !it.isCurrentDeviceDeleted
            })
        }
    }

    @Test
    fun `getStoredIntegrationData with real proto SAVE converts operationType correctly`() = runTest {
        val protoScopes = ProtoIntegrationData.newBuilder()
            .setDeviceId(DEVICE_ID)
            .addAllScopes(listOf("read:weight", "write:weight"))
            .build()
        val protoIntegrationInfo = ProtoIntegratedDeviceInfo.newBuilder()
            .setOperationType(ProtoIntegrationOperationType.PROTO_SAVE)
            .setScopes(protoScopes)
            .build()
        val dataWithProto: HealthConnectData = HealthConnectData.newBuilder()
            .setIntegrated(true)
            .setIntegrationInfo(protoIntegrationInfo)
            .addAllGrantedPermission(listOf("read:weight", "write:weight"))
            .build()

        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithProto

        val result = repository.getStoredIntegrationData(ACCOUNT_ID)

        assertThat(result).isNotNull()
        assertThat(result?.operationType).isEqualTo(IntegrationOperationType.SAVE.value)
        assertThat(result?.scopes?.deviceId).isEqualTo(DEVICE_ID)
        assertThat(result?.scopes?.preferences?.scopes).containsExactly("read:weight", "write:weight")
    }

    @Test
    fun `getStoredIntegrationData with real proto REMOVE converts operationType correctly`() = runTest {
        val protoScopes = ProtoIntegrationData.newBuilder()
            .setDeviceId(DEVICE_ID)
            .addAllScopes(listOf("read:weight"))
            .build()
        val protoIntegrationInfo = ProtoIntegratedDeviceInfo.newBuilder()
            .setOperationType(ProtoIntegrationOperationType.PROTO_REMOVE)
            .setScopes(protoScopes)
            .build()
        val dataWithProto: HealthConnectData = HealthConnectData.newBuilder()
            .setIntegrated(false)
            .setIntegrationInfo(protoIntegrationInfo)
            .addAllGrantedPermission(listOf("read:weight"))
            .build()

        coEvery { healthConnectDataStore.getHealthConnectData(ACCOUNT_ID) } returns dataWithProto

        val result = repository.getStoredIntegrationData(ACCOUNT_ID)

        assertThat(result).isNotNull()
        assertThat(result?.operationType).isEqualTo(IntegrationOperationType.REMOVE.value)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildIntegrationData() = IntegrationData(
        deviceId = DEVICE_ID,
        type = IntegrationType.HEALTH_CONNECT.value,
        preferences = IntegrationPreferences(scopes = emptyList()),
    )
}
