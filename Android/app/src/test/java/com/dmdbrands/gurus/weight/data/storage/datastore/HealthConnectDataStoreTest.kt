package com.dmdbrands.gurus.weight.data.storage.datastore

import android.content.Context
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationData
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationOperationType
import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectDataStoreTest {

    private lateinit var fakeDataStore: FakeDataStore<HealthConnectDataMap>
    private lateinit var healthConnectDataStore: HealthConnectDataStore

    private val testAccountId = "acc-1"
    private val testAccountId2 = "acc-2"

    private val testHealthConnectData: HealthConnectData = HealthConnectData.newBuilder()
        .setIntegrated(true)
        .setAlertSeen(false)
        .setOpen(true)
        .setOutOfSync(false)
        .setModalState(false)
        .setAssignedTo("device-1")
        .addGrantedPermission("READ_STEPS")
        .addGrantedPermission("READ_WEIGHT")
        .build()

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.i(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Exception>()) } returns Unit

        mockkStatic("com.dmdbrands.gurus.weight.data.storage.datastore.HealthConnectDataStoreKt")
        val mockContext = mockk<Context>(relaxed = true)
        fakeDataStore = FakeDataStore(HealthConnectDataMap.getDefaultInstance())
        every { mockContext.healthConnectDataStore } returns fakeDataStore
        healthConnectDataStore = HealthConnectDataStore(mockContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private suspend fun seedData(
        accountId: String = testAccountId,
        data: HealthConnectData = testHealthConnectData,
    ) {
        fakeDataStore.updateData { current ->
            current.toBuilder().putData(accountId, data).build()
        }
    }

    // -------------------------------------------------------------------------
    // getHealthConnectDataFlow
    // -------------------------------------------------------------------------

    @Test
    fun `getHealthConnectDataFlow emits null for unknown account`() = runTest {
        healthConnectDataStore.getHealthConnectDataFlow(testAccountId).test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getHealthConnectDataFlow emits data for known account`() = runTest {
        seedData()
        healthConnectDataStore.getHealthConnectDataFlow(testAccountId).test {
            assertThat(awaitItem()).isEqualTo(testHealthConnectData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // healthConnectData
    // -------------------------------------------------------------------------

    @Test
    fun `healthConnectData returns empty map initially`() = runTest {
        assertThat(healthConnectDataStore.healthConnectData()).isEmpty()
    }

    @Test
    fun `healthConnectData returns all stored entries`() = runTest {
        seedData(testAccountId)
        seedData(testAccountId2, HealthConnectData.newBuilder().setIntegrated(false).build())

        val result = healthConnectDataStore.healthConnectData()
        assertThat(result).hasSize(2)
        assertThat(result).containsKey(testAccountId)
        assertThat(result).containsKey(testAccountId2)
    }

    // -------------------------------------------------------------------------
    // setHealthConnectData
    // -------------------------------------------------------------------------

    @Test
    fun `setHealthConnectData stores data for account`() = runTest {
        healthConnectDataStore.setHealthConnectData(testAccountId, testHealthConnectData)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result).isEqualTo(testHealthConnectData)
    }

    @Test
    fun `setHealthConnectData overwrites existing data`() = runTest {
        seedData()
        val updatedData = HealthConnectData.newBuilder().setIntegrated(false).build()

        healthConnectDataStore.setHealthConnectData(testAccountId, updatedData)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.integrated).isFalse()
    }

    // -------------------------------------------------------------------------
    // getHealthConnectData
    // -------------------------------------------------------------------------

    @Test
    fun `getHealthConnectData returns null for unknown account`() = runTest {
        assertThat(healthConnectDataStore.getHealthConnectData("nonexistent")).isNull()
    }

    @Test
    fun `getHealthConnectData returns stored data`() = runTest {
        seedData()
        assertThat(healthConnectDataStore.getHealthConnectData(testAccountId)).isEqualTo(testHealthConnectData)
    }

    // -------------------------------------------------------------------------
    // hasHealthConnectData
    // -------------------------------------------------------------------------

    @Test
    fun `hasHealthConnectData returns false for unknown account`() = runTest {
        assertThat(healthConnectDataStore.hasHealthConnectData("nonexistent")).isFalse()
    }

    @Test
    fun `hasHealthConnectData returns true for known account`() = runTest {
        seedData()
        assertThat(healthConnectDataStore.hasHealthConnectData(testAccountId)).isTrue()
    }

    // -------------------------------------------------------------------------
    // setHcIntegrationStatus
    // -------------------------------------------------------------------------

    @Test
    fun `setHcIntegrationStatus updates existing entry`() = runTest {
        seedData()

        healthConnectDataStore.setHcIntegrationStatus(testAccountId, false)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.integrated).isFalse()
    }

    @Test
    fun `setHcIntegrationStatus preserves other fields`() = runTest {
        seedData()

        healthConnectDataStore.setHcIntegrationStatus(testAccountId, false)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.integrated).isFalse()
        assertThat(result?.alertSeen).isFalse()
        assertThat(result?.open).isTrue()
        assertThat(result?.outOfSync).isFalse()
        assertThat(result?.modalState).isFalse()
        assertThat(result?.assignedTo).isEqualTo("device-1")
        assertThat(result?.grantedPermissionList).containsExactly("READ_STEPS", "READ_WEIGHT")
    }

    @Test
    fun `setHcIntegrationStatus creates new entry if account not found`() = runTest {
        healthConnectDataStore.setHcIntegrationStatus(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.integrated).isTrue()
    }

    // -------------------------------------------------------------------------
    // updateAlertSeen
    // -------------------------------------------------------------------------

    @Test
    fun `updateAlertSeen updates existing entry`() = runTest {
        seedData()

        healthConnectDataStore.updateAlertSeen(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.alertSeen).isTrue()
    }

    @Test
    fun `updateAlertSeen preserves other fields`() = runTest {
        seedData()

        healthConnectDataStore.updateAlertSeen(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.alertSeen).isTrue()
        assertThat(result?.integrated).isTrue()
        assertThat(result?.open).isTrue()
        assertThat(result?.outOfSync).isFalse()
        assertThat(result?.assignedTo).isEqualTo("device-1")
        assertThat(result?.grantedPermissionList).containsExactly("READ_STEPS", "READ_WEIGHT")
    }

    @Test
    fun `updateAlertSeen creates new entry if account not found`() = runTest {
        healthConnectDataStore.updateAlertSeen(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.alertSeen).isTrue()
    }

    // -------------------------------------------------------------------------
    // updateOutOfSync
    // -------------------------------------------------------------------------

    @Test
    fun `updateOutOfSync updates existing entry`() = runTest {
        seedData()

        healthConnectDataStore.updateOutOfSync(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.outOfSync).isTrue()
    }

    @Test
    fun `updateOutOfSync creates new entry if account not found`() = runTest {
        healthConnectDataStore.updateOutOfSync(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.outOfSync).isTrue()
    }

    // -------------------------------------------------------------------------
    // setOpen / getOpen
    // -------------------------------------------------------------------------

    @Test
    fun `setOpen updates existing entry`() = runTest {
        seedData()

        healthConnectDataStore.setOpen(testAccountId, false)

        assertThat(healthConnectDataStore.getOpen(testAccountId)).isFalse()
    }

    @Test
    fun `setOpen creates new entry if account not found`() = runTest {
        healthConnectDataStore.setOpen(testAccountId, true)

        assertThat(healthConnectDataStore.getOpen(testAccountId)).isTrue()
    }

    @Test
    fun `getOpen returns false for unknown account`() = runTest {
        assertThat(healthConnectDataStore.getOpen("nonexistent")).isFalse()
    }

    // -------------------------------------------------------------------------
    // setAssignedTo / getAssignedTo / clearAssignedTo
    // -------------------------------------------------------------------------

    @Test
    fun `setAssignedTo updates existing entry`() = runTest {
        seedData()

        healthConnectDataStore.setAssignedTo(testAccountId, "new-device")

        assertThat(healthConnectDataStore.getAssignedTo(testAccountId)).isEqualTo("new-device")
    }

    @Test
    fun `setAssignedTo creates new entry if account not found`() = runTest {
        healthConnectDataStore.setAssignedTo(testAccountId, "device-x")

        assertThat(healthConnectDataStore.getAssignedTo(testAccountId)).isEqualTo("device-x")
    }

    @Test
    fun `getAssignedTo returns null for unknown account`() = runTest {
        assertThat(healthConnectDataStore.getAssignedTo("nonexistent")).isNull()
    }

    @Test
    fun `clearAssignedTo removes assignedTo from existing entry`() = runTest {
        seedData()

        healthConnectDataStore.clearAssignedTo(testAccountId)

        val result = healthConnectDataStore.getAssignedTo(testAccountId)
        assertThat(result).isEmpty()
    }

    @Test
    fun `clearAssignedTo creates new entry if account not found`() = runTest {
        healthConnectDataStore.clearAssignedTo(testAccountId)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result).isNotNull()
    }

    // -------------------------------------------------------------------------
    // updateModalState
    // -------------------------------------------------------------------------

    @Test
    fun `updateModalState updates existing entry`() = runTest {
        seedData()

        healthConnectDataStore.updateModalState(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.modalState).isTrue()
    }

    @Test
    fun `updateModalState creates new entry if account not found`() = runTest {
        healthConnectDataStore.updateModalState(testAccountId, true)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.modalState).isTrue()
    }

    // -------------------------------------------------------------------------
    // setPermissions
    // -------------------------------------------------------------------------

    @Test
    fun `setPermissions updates existing entry`() = runTest {
        seedData()

        healthConnectDataStore.setPermissions(testAccountId, listOf("READ_HEART_RATE"))

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.grantedPermissionList).containsExactly("READ_HEART_RATE")
    }

    @Test
    fun `setPermissions creates new entry if account not found`() = runTest {
        healthConnectDataStore.setPermissions(testAccountId, listOf("READ_STEPS", "READ_WEIGHT"))

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.grantedPermissionList).containsExactly("READ_STEPS", "READ_WEIGHT")
    }

    @Test
    fun `setPermissions preserves other fields`() = runTest {
        seedData()

        healthConnectDataStore.setPermissions(testAccountId, listOf("WRITE_STEPS"))

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.grantedPermissionList).containsExactly("WRITE_STEPS")
        assertThat(result?.integrated).isTrue()
        assertThat(result?.alertSeen).isFalse()
        assertThat(result?.open).isTrue()
        assertThat(result?.outOfSync).isFalse()
        assertThat(result?.assignedTo).isEqualTo("device-1")
    }

    @Test
    fun `setPermissions replaces all previous permissions`() = runTest {
        seedData()

        healthConnectDataStore.setPermissions(testAccountId, listOf("WRITE_STEPS"))

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.grantedPermissionList).containsExactly("WRITE_STEPS")
    }

    // -------------------------------------------------------------------------
    // setIntegrationInfo
    // -------------------------------------------------------------------------

    @Test
    fun `setIntegrationInfo stores integration info on existing entry`() = runTest {
        seedData()

        val info = IntegratedDeviceInfo(
            operationType = IntegrationOperationType.SAVE.value,
            scopes = IntegrationData(
                deviceId = "device-123",
                type = "healthconnect",
                preferences = IntegrationPreferences(scopes = listOf("steps", "weight")),
            ),
        )
        healthConnectDataStore.setIntegrationInfo(testAccountId, info)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.hasIntegrationInfo()).isTrue()
        assertThat(result?.integrationInfo?.operationType).isEqualTo(ProtoIntegrationOperationType.PROTO_SAVE)
        assertThat(result?.integrationInfo?.scopes?.deviceId).isEqualTo("device-123")
        assertThat(result?.integrationInfo?.scopes?.scopesList).containsExactly("steps", "weight")
    }

    @Test
    fun `setIntegrationInfo with REMOVE operation type`() = runTest {
        seedData()

        val info = IntegratedDeviceInfo(
            operationType = IntegrationOperationType.REMOVE.value,
            scopes = IntegrationData(
                deviceId = "device-123",
                type = "healthconnect",
            ),
        )
        healthConnectDataStore.setIntegrationInfo(testAccountId, info)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.integrationInfo?.operationType).isEqualTo(ProtoIntegrationOperationType.PROTO_REMOVE)
    }

    @Test
    fun `setIntegrationInfo with null clears integration info`() = runTest {
        seedData()
        val info = IntegratedDeviceInfo(
            operationType = IntegrationOperationType.SAVE.value,
            scopes = IntegrationData(deviceId = "device-123", type = "healthconnect"),
        )
        healthConnectDataStore.setIntegrationInfo(testAccountId, info)

        healthConnectDataStore.setIntegrationInfo(testAccountId, null)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.hasIntegrationInfo()).isFalse()
    }

    @Test
    fun `setIntegrationInfo creates new entry if account not found`() = runTest {
        val info = IntegratedDeviceInfo(
            operationType = IntegrationOperationType.SAVE.value,
            scopes = IntegrationData(
                deviceId = "device-123",
                type = "healthconnect",
                preferences = IntegrationPreferences(scopes = listOf("steps")),
            ),
        )
        healthConnectDataStore.setIntegrationInfo(testAccountId, info)

        val result = healthConnectDataStore.getHealthConnectData(testAccountId)
        assertThat(result?.hasIntegrationInfo()).isTrue()
    }

    // -------------------------------------------------------------------------
    // clearData
    // -------------------------------------------------------------------------

    @Test
    fun `clearData removes all entries`() = runTest {
        seedData(testAccountId)
        seedData(testAccountId2, HealthConnectData.newBuilder().setIntegrated(false).build())

        healthConnectDataStore.clearData()

        assertThat(healthConnectDataStore.healthConnectData()).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getData
    // -------------------------------------------------------------------------

    @Test
    fun `getData returns default instance initially`() = runTest {
        val data = healthConnectDataStore.getData()
        assertThat(data.dataMap).isEmpty()
    }
}
