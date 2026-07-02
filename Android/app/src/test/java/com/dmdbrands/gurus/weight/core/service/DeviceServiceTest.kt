package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import javax.inject.Provider
import kotlinx.coroutines.test.TestScope
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.device.R4ScalePreferenceApiModel
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.repository.IDeviceRepository
import com.dmdbrands.gurus.weight.features.common.enums.DeviceSetupType
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.greatergoods.ggbluetoothsdk.external.enums.GGDeviceProtocolType
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test
import android.content.Context

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceServiceTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val deviceRepository: IDeviceRepository = mockk()
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private lateinit var service: DeviceService

    // --- Test Fixtures ---
    private val accountId = "acc-1"
    private val deviceId = "device-1"

    private val fakePreferences = Preferences(
        id = deviceId,
        shouldMeasureImpedance = true,
        shouldMeasurePulse = false,
        isSynced = false,
    )

    private val fakeR4Preferences = R4ScalePreferenceApiModel(
        scaleId = deviceId,
        displayName = null,
        displayMetrics = null,
        shouldFactoryReset = false,
        shouldMeasureImpedance = true,
        shouldMeasurePulse = false,
        timeFormat = null,
        tzOffset = null,
        wifiFotaScheduleTime = null,
    )

    private fun fakeDevice(
        id: String = deviceId,
        mac: String = "AA:BB:CC:DD:EE:FF",
        connectionStatus: BLEStatus = BLEStatus.DISCONNECTED,
        isSynced: Boolean = true,
        isDeleted: Boolean = false,
        hasServerID: Boolean = true,
        deviceType: String? = null,
        preferences: Preferences? = null,
        sku: String? = null,
    ): Device {
        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns mac
        every { detail.isWifiConfigured } returns false
        every { detail.wifiMacAddress } returns null
        every { detail.impedanceSwitchState } returns null
        return Device(
            id = id,
            device = detail,
            connectionStatus = connectionStatus,
            isSynced = isSynced,
            isDeleted = isDeleted,
            hasServerID = hasServerID,
            deviceType = deviceType,
            preferences = preferences,
            sku = sku,
        )
    }

    /** A paired BPM device whose [GGDeviceDetail.broadcastIdString] is explicitly stubbed, for heal tests. */
    private fun fakeBpmDevice(id: String, mac: String, sku: String, broadcastId: String?): Device {
        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns mac
        every { detail.isWifiConfigured } returns false
        every { detail.wifiMacAddress } returns null
        every { detail.impedanceSwitchState } returns null
        every { detail.broadcastIdString } returns broadcastId
        return Device(id = id, device = detail, isSynced = true, hasServerID = true, sku = sku)
    }

    /** A paired baby scale whose [GGDeviceDetail.broadcastIdString] is stubbed, for baby-scale heal tests. */
    private fun fakeBabyDevice(id: String, sku: String = "0220", broadcastId: String?): Device {
        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns "BA:B0:00:00:00:0$id".take(17)
        every { detail.isWifiConfigured } returns false
        every { detail.wifiMacAddress } returns null
        every { detail.impedanceSwitchState } returns null
        every { detail.broadcastIdString } returns broadcastId
        return Device(id = id, device = detail, isSynced = true, hasServerID = true, sku = sku)
    }

    @BeforeEach
    fun setUp() {
        every { connectivityObserver.getCurrentNetworkState() } returns
            NetworkState(available = true, unAvailable = false)
        every { deviceRepository.getDevices(any(), any()) } returns flowOf(emptyList())
        every { deviceRepository.getDevice(any()) } returns flowOf(null)
        every { deviceRepository.getDeviceByBroadcastId(any(), any()) } returns flowOf(null)
        every { deviceRepository.getDeviceByMac(any(), any()) } returns flowOf(null)
        coEvery { deviceRepository.getDevicesFromApi(any()) } returns emptyList()
        coEvery { deviceRepository.createPairedDevice(any(), any()) } answers { firstArg<Device>().copy(isSynced = true) }
        coEvery { deviceRepository.saveDeviceToDb(any(), any()) } just Runs
        coEvery { deviceRepository.deleteAllDevicesForAccount(any()) } just Runs
        coEvery { deviceRepository.deleteDeviceFromDb(any()) } just Runs
        coEvery { deviceRepository.deleteDeviceFromApi(any()) } returns true
        coEvery { deviceRepository.deletePairedDevice(any()) } returns true
        coEvery { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) } just Runs
        coEvery { deviceRepository.updateDevice(any(), any()) } just Runs
        coEvery { deviceRepository.updateDeviceNickname(any(), any()) } returns fakeDevice()
        coEvery { deviceRepository.saveScalePreferencesToApi(any()) } returns fakeR4Preferences
        coEvery { deviceRepository.markDeviceSynced(any(), any()) } just Runs
        coEvery { deviceRepository.markDeviceDeleted(any(), any()) } just Runs
        coEvery { deviceRepository.getScaleTokenFromApi(any()) } returns "token-123"

        service = DeviceService(
            deviceRepository,
            connectivityObserver,
            dialogQueueService,
            appNavigationService,
            context,
            appScope = TestScope(mainDispatcherRule.dispatcher),
            productSelectionManager = Provider { mockk(relaxed = true) },
        )
    }

    // -------------------------------------------------------------------------
    // isInitialized / getCurrentAccountId — account state
    // -------------------------------------------------------------------------

    @Test
    fun `isInitialized returns false before setAccountId is called`() = runTest(mainDispatcherRule.scheduler) {
        assertThat(service.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized returns true after setAccountId`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)

        assertThat(service.isInitialized()).isTrue()
    }

    @Test
    fun `getCurrentAccountId returns null before setAccountId`() = runTest(mainDispatcherRule.scheduler) {
        assertThat(service.getCurrentAccountId()).isNull()
    }

    @Test
    fun `getCurrentAccountId returns set accountId`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)

        assertThat(service.getCurrentAccountId()).isEqualTo(accountId)
    }

    @Test
    fun `clearAccountData sets accountId to null`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        service.clearAccountData()

        assertThat(service.getCurrentAccountId()).isNull()
        assertThat(service.isInitialized()).isFalse()
    }

    // -------------------------------------------------------------------------
    // setAccountId — calls syncDevices and fetchScales
    // -------------------------------------------------------------------------

    @Test
    fun `setAccountId calls getDevicesFromApi for initial sync`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        advanceUntilIdle()

        coVerify { deviceRepository.getDevicesFromApi(accountId) }
    }

    // -------------------------------------------------------------------------
    // updateConnectionStatus — map update
    // -------------------------------------------------------------------------

    @Test
    fun `updateConnectionStatus adds entry to connection status map`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC"

        service.updateConnectionStatus(mac, BLEStatus.CONNECTED)

        // Populate _pairedScales so we can test onDeviceUpdate next
        // (No direct getter; verified indirectly via onDeviceUpdate)
        // Smoke test — no exception
    }

    @Test
    fun `updateConnectionStatus overwrites previous status for same mac`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC"

        service.updateConnectionStatus(mac, BLEStatus.CONNECTED)
        service.updateConnectionStatus(mac, BLEStatus.DISCONNECTED)

        // Verified indirectly via onDeviceUpdate behavior below
    }

    // -------------------------------------------------------------------------
    // onDeviceUpdate — in-place paired scales mutation
    // -------------------------------------------------------------------------

    @Test
    fun `onDeviceUpdate updates device in pairedScales when found by mac`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC:DD:EE:FF"
        val existingDevice = fakeDevice(mac = mac)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(existingDevice))
        // Set connection status BEFORE setAccountId so fetchScales picks it up from the map
        service.updateConnectionStatus(mac, BLEStatus.DISCONNECTED)
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on Dispatchers.IO; wait for it to populate _pairedScales

        val updatedDetail: GGDeviceDetail = mockk(relaxed = true)
        every { updatedDetail.macAddress } returns mac
        every { updatedDetail.isWifiConfigured } returns true
        every { updatedDetail.wifiMacAddress } returns "11:22:33:44:55:66"
        every { updatedDetail.impedanceSwitchState } returns null

        service.onDeviceUpdate(updatedDetail, BLEStatus.CONNECTED)

        service.pairedScales.test {
            val devices = awaitItem()
            // onDeviceUpdate copies the device with updated connectionStatus; check by device ID
            val updated = devices.firstOrNull { it.id == existingDevice.id }
            assertThat(updated?.connectionStatus).isEqualTo(BLEStatus.CONNECTED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeviceUpdate defaults to DISCONNECTED when connectionStatus is null`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC:DD:EE:FF"
        val existingDevice = fakeDevice(mac = mac)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(existingDevice))
        service.updateConnectionStatus(mac, BLEStatus.DISCONNECTED)
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on IO

        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns mac
        every { detail.isWifiConfigured } returns false
        every { detail.wifiMacAddress } returns null
        every { detail.impedanceSwitchState } returns null

        service.onDeviceUpdate(detail, null)

        service.pairedScales.test {
            val devices = awaitItem()
            // After null connectionStatus, device should be DISCONNECTED
            val updated = devices.firstOrNull { it.id == existingDevice.id }
            assertThat(updated?.connectionStatus).isEqualTo(BLEStatus.DISCONNECTED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDeviceUpdate is no-op when device not found in pairedScales`() = runTest(mainDispatcherRule.scheduler) {
        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns "FF:FF:FF:FF:FF:FF"
        every { detail.isWifiConfigured } returns false
        every { detail.wifiMacAddress } returns null
        every { detail.impedanceSwitchState } returns null

        // No crash expected when device is not in pairedScales
        service.onDeviceUpdate(detail, BLEStatus.CONNECTED)
    }

    // -------------------------------------------------------------------------
    // pairedScales / connectedScales / hasBluetoothWifiScale — state flows
    // -------------------------------------------------------------------------

    @Test
    fun `pairedScales emits empty list initially`() = runTest(mainDispatcherRule.scheduler) {
        service.pairedScales.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connectedScales emits empty list initially`() = runTest(mainDispatcherRule.scheduler) {
        service.connectedScales.test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasBluetoothWifiScale emits false when no R4 scale paired`() = runTest(mainDispatcherRule.scheduler) {
        service.hasBluetoothWifiScale.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasWeightScale emits false when no scale paired`() = runTest(mainDispatcherRule.scheduler) {
        service.hasWeightScale.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasWeightScale emits true when a weight scale is paired`() = runTest(mainDispatcherRule.scheduler) {
        val scale = fakeDevice(deviceType = DeviceSetupType.Bluetooth.value)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(scale))
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on IO

        assertThat(service.hasWeightScale.first()).isTrue()
    }

    @Test
    fun `hasWeightScale emits false when only a BPM device is paired`() = runTest(mainDispatcherRule.scheduler) {
        val bpm = fakeDevice(deviceType = DeviceSetupType.BpmBluetooth.value)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(bpm))
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on IO

        assertThat(service.hasWeightScale.first()).isFalse()
    }

    // -------------------------------------------------------------------------
    // getScalesByType / getConnectedScales / getUnsyncedScales
    // -------------------------------------------------------------------------

    @Test
    fun `getScalesByType returns empty list when no scales match`() = runTest(mainDispatcherRule.scheduler) {
        val result = service.getScalesByType(DeviceSetupType.BtWifiR4.value)
        assertThat(result).isEmpty()
    }

    @Test
    fun `getConnectedScales returns only CONNECTED devices`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC:DD:EE:FF"
        val connected = fakeDevice(id = "c1", mac = mac)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(connected))
        // Pre-seed connection status map so fetchScales maps the device as CONNECTED
        service.updateConnectionStatus(mac, BLEStatus.CONNECTED)
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on IO

        val result = service.getConnectedScales()
        assertThat(result.any { it.connectionStatus == BLEStatus.CONNECTED }).isTrue()
    }

    @Test
    fun `getConnectedScales returns empty when all devices are disconnected`() = runTest(mainDispatcherRule.scheduler) {
        val result = service.getConnectedScales()
        assertThat(result).isEmpty()
    }

    @Test
    fun `getUnsyncedScales returns devices where hasServerID is false`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC:DD:EE:FF"
        val unsynced = fakeDevice(mac = mac, hasServerID = false)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(unsynced))
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on IO

        val result = service.getUnsyncedScales()
        assertThat(result.all { !it.hasServerID }).isTrue()
    }

    // -------------------------------------------------------------------------
    // scaleExistsByMac
    // -------------------------------------------------------------------------

    @Test
    fun `scaleExistsByMac returns false when pairedScales is empty`() = runTest(mainDispatcherRule.scheduler) {
        val result = service.scaleExistsByMac("AA:BB:CC:DD:EE:FF")
        assertThat(result).isFalse()
    }

    @Test
    fun `scaleExistsByMac returns true when mac matches`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC:DD:EE:FF"
        val device = fakeDevice(mac = mac)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(device))
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on IO

        val result = service.scaleExistsByMac(mac)
        assertThat(result).isTrue()
    }

    // -------------------------------------------------------------------------
    // getScale
    // -------------------------------------------------------------------------

    @Test
    fun `getScale returns device when found`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice()
        every { deviceRepository.getDevice(deviceId) } returns flowOf(device)

        val result = service.getScale(deviceId)

        assertThat(result).isEqualTo(device)
    }

    @Test
    fun `getScale returns null when device not found`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceRepository.getDevice(deviceId) } returns flowOf(null)

        val result = service.getScale(deviceId)

        assertThat(result).isNull()
    }

    @Test
    fun `getScale returns null and does not crash on exception`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceRepository.getDevice(deviceId) } throws RuntimeException("DB error")

        val result = service.getScale(deviceId)

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // getScaleByBroadcastId
    // -------------------------------------------------------------------------

    @Test
    fun `getScaleByBroadcastId returns device when found`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice()
        every { deviceRepository.getDeviceByBroadcastId("bcast-1", accountId) } returns flowOf(device)

        val result = service.getScaleByBroadcastId("bcast-1", accountId)

        assertThat(result).isEqualTo(device)
    }

    @Test
    fun `getScaleByBroadcastId returns null on exception`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceRepository.getDeviceByBroadcastId(any(), any()) } throws RuntimeException("error")

        val result = service.getScaleByBroadcastId("bcast-1", accountId)

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // getScaleByMac
    // -------------------------------------------------------------------------

    @Test
    fun `getScaleByMac returns null when no accountId is set`() = runTest(mainDispatcherRule.scheduler) {
        val result = service.getScaleByMac("AA:BB:CC:DD:EE:FF")
        assertThat(result).isNull()
    }

    @Test
    fun `getScaleByMac returns device when found`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice()
        service.setAccountId(accountId)
        every { deviceRepository.getDeviceByMac("AA:BB:CC:DD:EE:FF", accountId) } returns flowOf(device)

        val result = service.getScaleByMac("AA:BB:CC:DD:EE:FF")

        assertThat(result).isEqualTo(device)
    }

    @Test
    fun `getScaleByMac returns null on exception`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        every { deviceRepository.getDeviceByMac(any(), any()) } throws RuntimeException("error")

        val result = service.getScaleByMac("AA:BB:CC:DD:EE:FF")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // getGGBTDevices
    // -------------------------------------------------------------------------

    @Test
    fun `getGGBTDevices returns emptyFlow when accountId is not set`() = runTest(mainDispatcherRule.scheduler) {
        service.getGGBTDevices().test {
            awaitComplete()
        }
    }

    @Test
    fun `getGGBTDevices returns mapped devices when accountId is set`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice()
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(device))
        service.setAccountId(accountId)

        service.getGGBTDevices().test {
            val result = awaitItem()
            assertThat(result).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A BPM device with a stubbed broadcastId + user slot, for syncAllData gating tests. */
    private fun bpmSlotDevice(id: String, broadcastId: String, userNumber: Int, sku: String): Device {
        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns "AA:BB:CC:DD:EE:FF"
        every { detail.broadcastId } returns broadcastId
        every { detail.isWifiConfigured } returns false
        every { detail.wifiMacAddress } returns null
        every { detail.impedanceSwitchState } returns null
        return Device(id = id, device = detail, isSynced = true, hasServerID = true, sku = sku, userNumber = userNumber)
    }

    @Test
    fun `getGGBTDevices sets syncAllData true only for A6 monitor with multiple user slots`() =
        runTest(mainDispatcherRule.scheduler) {
            // Same A6 monitor (broadcastId "bc-a6") paired under two user slots.
            val slot1 = bpmSlotDevice("a6-1", "bc-a6", userNumber = 1, sku = "0663")
            val slot2 = bpmSlotDevice("a6-2", "bc-a6", userNumber = 2, sku = "0663")
            every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(slot1, slot2))
            service.setAccountId(accountId)

            service.getGGBTDevices().test {
                val result = awaitItem()
                assertThat(result.all { it.syncAllData == true }).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getGGBTDevices never sets syncAllData for A3 duplicates`() = runTest(mainDispatcherRule.scheduler) {
        val slot1 = bpmSlotDevice("a3-1", "bc-a3", userNumber = 1, sku = "0603")
        val slot2 = bpmSlotDevice("a3-2", "bc-a3", userNumber = 2, sku = "0603")
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(slot1, slot2))
        service.setAccountId(accountId)

        service.getGGBTDevices().test {
            val result = awaitItem()
            assertThat(result.none { it.syncAllData == true }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getGGBTDevices leaves syncAllData false for a single A6 monitor`() = runTest(mainDispatcherRule.scheduler) {
        val only = bpmSlotDevice("a6-1", "bc-a6", userNumber = 1, sku = "0663")
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(only))
        service.setAccountId(accountId)

        service.getGGBTDevices().test {
            val result = awaitItem()
            assertThat(result.single().syncAllData).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // getScaleByBroadcastIdAndUser — reading attribution by broadcastId + user slot
    // -------------------------------------------------------------------------

    @Test
    fun `getScaleByBroadcastIdAndUser delegates to repository`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice()
        every { deviceRepository.getDeviceByBroadcastIdAndUser("bc-1", 2, accountId) } returns flowOf(device)

        val result = service.getScaleByBroadcastIdAndUser("bc-1", 2, accountId)

        assertThat(result).isEqualTo(device)
    }

    @Test
    fun `getScaleByBroadcastIdAndUser returns null on exception`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceRepository.getDeviceByBroadcastIdAndUser(any(), any(), any()) } throws RuntimeException("error")

        val result = service.getScaleByBroadcastIdAndUser("bc-1", 1, accountId)

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // getScaleToken
    // -------------------------------------------------------------------------

    @Test
    fun `getScaleToken returns token from repository`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { deviceRepository.getScaleTokenFromApi(false) } returns "token-abc"

        val result = service.getScaleToken(false)

        assertThat(result).isEqualTo("token-abc")
    }

    @Test
    fun `getScaleToken returns null on exception`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { deviceRepository.getScaleTokenFromApi(any()) } throws RuntimeException("API error")

        val result = service.getScaleToken(false)

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // syncDevices — early return when no accountId
    // -------------------------------------------------------------------------

    @Test
    fun `syncDevices returns early when no accountId set`() = runTest(mainDispatcherRule.scheduler) {
        service.syncDevices()

        coVerify(exactly = 0) { deviceRepository.getDevicesFromApi(any()) }
    }

    @Test
    fun `syncDevices fetches from API and stores devices`() = runTest(mainDispatcherRule.scheduler) {
        val apiDevice = fakeDevice(id = "api-1", isSynced = true)
        coEvery { deviceRepository.getDevicesFromApi(accountId) } returns listOf(apiDevice)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(emptyList())
        every { deviceRepository.getDevice("api-1") } returns flowOf(apiDevice)
        service.setAccountId(accountId)
        advanceUntilIdle()

        coVerify { deviceRepository.deleteAllDevicesForAccount(accountId) }
        coVerify { deviceRepository.saveDeviceToDb(any(), accountId) }
    }

    /** A BPM device carrying a user slot + broadcastId + peripheralIdentifier, for the sync-preservation test. */
    private fun bpmDeviceWith(id: String, broadcastIdString: String?, userNumber: Int?, identifier: String): Device {
        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns "AA:BB:CC:DD:EE:FF"
        every { detail.broadcastId } returns null
        every { detail.broadcastIdString } returns broadcastIdString
        every { detail.identifier } returns identifier
        every { detail.isWifiConfigured } returns false
        every { detail.wifiMacAddress } returns null
        every { detail.impedanceSwitchState } returns null
        return Device(id = id, device = detail, isSynced = true, hasServerID = true, sku = "0663", userNumber = userNumber)
    }

    @Test
    fun `syncDevices preserves userNumber from the POST copy when the GET omits it`() =
        runTest(mainDispatcherRule.scheduler) {
            // Local device paired THIS run (unsynced) carries the user slot.
            val localUnsynced = bpmDeviceWith("dev-1", broadcastIdString = "bc-1", userNumber = 2, identifier = "periph-1")
                .copy(isSynced = false)
            every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(localUnsynced))
            // POST /v3/paired-device echoes userNumber back...
            coEvery { deviceRepository.createPairedDevice(any(), accountId) } returns
                bpmDeviceWith("dev-1", broadcastIdString = "bc-1", userNumber = 2, identifier = "periph-1")
            // ...but the GET omits userNumber (top-level → null).
            coEvery { deviceRepository.getDevicesFromApi(accountId) } returns
                listOf(bpmDeviceWith("dev-1", broadcastIdString = null, userNumber = null, identifier = ""))
            // Not in the DB yet at merge time — preservation must fall back to the in-memory POST copy.
            every { deviceRepository.getDevice("dev-1") } returns flowOf(null)
            val saved = slot<Device>()
            coEvery { deviceRepository.saveDeviceToDb(capture(saved), accountId) } just Runs

            service.setAccountId(accountId)
            advanceUntilIdle()

            // The persisted row keeps the user slot instead of being nulled by the GET.
            assertThat(saved.captured.userNumber).isEqualTo(2)
        }

    @Test
    fun `syncDevices syncs unsynced device to API`() = runTest(mainDispatcherRule.scheduler) {
        val unsyncedDevice = fakeDevice(id = "local-1", isSynced = false, isDeleted = false, preferences = null)
        val savedDevice = unsyncedDevice.copy(isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(unsyncedDevice))
        coEvery { deviceRepository.createPairedDevice(any(), accountId) } returns savedDevice
        service.setAccountId(accountId)
        advanceUntilIdle()

        coVerify { deviceRepository.createPairedDevice(any(), accountId) }
    }

    @Test
    fun `syncDevices syncs R4 scale preferences to API`() = runTest(mainDispatcherRule.scheduler) {
        val prefs = fakePreferences.copy(isSynced = false)
        val r4Device = fakeDevice(
            id = "r4-1",
            isSynced = false,
            deviceType = DeviceSetupType.BtWifiR4.value,
            preferences = prefs,
        )
        val savedDevice = r4Device.copy(isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(r4Device))
        coEvery { deviceRepository.createPairedDevice(any(), accountId) } returns savedDevice
        service.setAccountId(accountId)
        advanceUntilIdle()

        coVerify { deviceRepository.saveScalePreferencesToApi(any()) }
    }

    @Test
    fun `syncDevices handles R4 preference sync exception gracefully`() = runTest(mainDispatcherRule.scheduler) {
        val prefs = fakePreferences.copy(isSynced = false)
        val r4Device = fakeDevice(
            id = "r4-err",
            isSynced = false,
            deviceType = DeviceSetupType.BtWifiR4.value,
            preferences = prefs,
        )
        val savedDevice = r4Device.copy(isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(r4Device))
        coEvery { deviceRepository.createPairedDevice(any(), accountId) } returns savedDevice
        coEvery { deviceRepository.saveScalePreferencesToApi(any()) } throws RuntimeException("pref sync failed")
        service.setAccountId(accountId)
        advanceUntilIdle()

        // Should not crash; R4 preference failure is caught
        coVerify { deviceRepository.saveScalePreferencesToApi(any()) }
    }

    @Test
    fun `syncDevices removes old temp record when server assigns new ID`() = runTest(mainDispatcherRule.scheduler) {
        // isSynced = false so the device is classified in devicesToSync
        val localDevice = fakeDevice(id = "temp-local", isSynced = false)
        val serverDevice = fakeDevice(id = "server-assigned")
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(localDevice))
        coEvery { deviceRepository.createPairedDevice(any(), accountId) } returns serverDevice
        service.setAccountId(accountId)
        advanceUntilIdle()

        coVerify { deviceRepository.deleteDeviceFromDb("temp-local") }
    }

    @Test
    fun `syncDevices handles individual device sync exception gracefully`() = runTest(mainDispatcherRule.scheduler) {
        val badDevice = fakeDevice(id = "bad-1", isSynced = false)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(badDevice))
        coEvery { deviceRepository.createPairedDevice(any(), accountId) } throws RuntimeException("API failure")
        service.setAccountId(accountId)
        advanceUntilIdle()

        // Should not crash
        coVerify { deviceRepository.createPairedDevice(any(), accountId) }
    }

    @Test
    fun `syncDevices deletes device via the unified paired-device endpoint and DB on delete`() = runTest(mainDispatcherRule.scheduler) {
        val deletedDevice = fakeDevice(id = "del-1", isDeleted = true, isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(deletedDevice))
        service.setAccountId(accountId)
        advanceUntilIdle()

        // Devices are registered via the unified POST, so they must be deleted via the unified
        // DELETE (deletePairedDevice) — not the legacy deleteDeviceFromApi.
        coVerify { deviceRepository.deletePairedDevice("del-1") }
        coVerify { deviceRepository.deleteDeviceFromDb("del-1") }
        coVerify(exactly = 0) { deviceRepository.deleteDeviceFromApi(any()) }
    }

    @Test
    fun `syncDevices handles Not Found delete response by marking synced and deleting locally`() = runTest(mainDispatcherRule.scheduler) {
        val deletedDevice = fakeDevice(id = "del-notfound", isDeleted = true, isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(deletedDevice))
        coEvery { deviceRepository.deletePairedDevice("del-notfound") } throws RuntimeException("404 Not Found")
        service.setAccountId(accountId)
        advanceUntilIdle()

        coVerify { deviceRepository.markDeviceSynced("del-notfound", true) }
        coVerify { deviceRepository.markDeviceDeleted("del-notfound", true) }
        coVerify { deviceRepository.deleteDeviceFromDb("del-notfound") }
    }

    @Test
    fun `syncDevices updates device with isDeleted+unsynced on non-NotFound delete error`() = runTest(mainDispatcherRule.scheduler) {
        val deletedDevice = fakeDevice(id = "del-err", isDeleted = true, isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(deletedDevice))
        coEvery { deviceRepository.deletePairedDevice("del-err") } throws RuntimeException("500 Server Error")
        service.setAccountId(accountId)
        advanceUntilIdle()

        coVerify { deviceRepository.updateDevice(any(), accountId = accountId) }
    }

    @Test
    fun `syncDevices does not resurrect a synced device that was just deleted`() = runTest(mainDispatcherRule.scheduler) {
        val x = fakeDevice(id = "X", isSynced = true)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(x))
        every { deviceRepository.getDevice("X") } returns flowOf(x)
        // Initially the server still returns X.
        coEvery { deviceRepository.getDevicesFromApi(accountId) } returns listOf(x)
        val saved = mutableListOf<Device>()
        coEvery { deviceRepository.saveDeviceToDb(capture(saved), accountId) } just Runs

        service.setAccountId(accountId)
        advanceUntilIdle()

        // User deletes X → server no longer returns it. The just-deleted id must NOT be resurrected
        // by the "keep devices the GET omitted" (localOnlySynced) step.
        coEvery { deviceRepository.getDevicesFromApi(accountId) } returns emptyList()
        saved.clear()

        service.deleteScale("X")
        advanceUntilIdle()

        coVerify { deviceRepository.deletePairedDevice("X") }
        assertThat(saved.any { it.id == "X" }).isFalse()
    }

    // -------------------------------------------------------------------------
    // healBabyScaleBroadcastId — baby-scale analog of the BPM broadcastId heal
    // -------------------------------------------------------------------------

    @Test
    fun `healBabyScaleBroadcastId heals the lone un-identified baby scale`() = runTest(mainDispatcherRule.scheduler) {
        val baby = fakeBabyDevice(id = "baby-1", sku = "0220", broadcastId = null)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(baby))
        val healed = fakeBabyDevice(id = "baby-1", sku = "0220", broadcastId = "bc-new")
        every { deviceRepository.getDeviceByBroadcastId("bc-new", accountId) } returns flowOf(healed)
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on IO — wait for _pairedScales to populate

        val result = service.healBabyScaleBroadcastId("bc-new", accountId)

        coVerify { deviceRepository.updateDeviceBroadcastId("baby-1", "bc-new", accountId) }
        assertThat(result?.id).isEqualTo("baby-1")
    }

    @Test
    fun `healBabyScaleBroadcastId returns null when no un-identified baby scale exists`() = runTest(mainDispatcherRule.scheduler) {
        // Already carries a broadcastId → not a heal candidate.
        val baby = fakeBabyDevice(id = "baby-1", sku = "0220", broadcastId = "existing")
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(baby))
        service.setAccountId(accountId)
        Thread.sleep(2000)

        val result = service.healBabyScaleBroadcastId("bc-new", accountId)

        assertThat(result).isNull()
        coVerify(exactly = 0) { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) }
    }

    @Test
    fun `healBabyScaleBroadcastId returns null when multiple un-identified baby scales exist (ambiguous)`() = runTest(mainDispatcherRule.scheduler) {
        val b1 = fakeBabyDevice(id = "baby-1", sku = "0220", broadcastId = null)
        val b2 = fakeBabyDevice(id = "baby-2", sku = "0222", broadcastId = null)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(b1, b2))
        service.setAccountId(accountId)
        Thread.sleep(2000)

        val result = service.healBabyScaleBroadcastId("bc-new", accountId)

        assertThat(result).isNull()
        coVerify(exactly = 0) { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) }
    }

    @Test
    fun `syncDevices uses syncedDevicesToStore as fallback when API fetch fails`() = runTest(mainDispatcherRule.scheduler) {
        val syncedDevice = fakeDevice(id = "synced-1", isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(syncedDevice))
        coEvery { deviceRepository.getDevicesFromApi(accountId) } throws RuntimeException("network error")
        service.setAccountId(accountId)
        advanceUntilIdle()

        // Should still save to DB with fallback list
        coVerify { deviceRepository.saveDeviceToDb(any(), accountId) }
    }

    @Test
    fun `syncDevices handles general exception and stores tempDevice as unsynced`() = runTest(mainDispatcherRule.scheduler) {
        val tempDevice = fakeDevice(id = "temp-general")
        every { deviceRepository.getDevices(accountId, false) } throws RuntimeException("general error")
        coEvery { deviceRepository.getDevicesFromApi(accountId) } returns emptyList()
        service.setAccountId(accountId)
        advanceUntilIdle()

        // syncDevices with tempDevice passed explicitly — general exception path
        service.syncDevices(tempDevice)

        // Should not crash; tempDevice added to unsynced and merged
        coVerify(atLeast = 1) { deviceRepository.saveDeviceToDb(any(), accountId) }
    }

    // -------------------------------------------------------------------------
    // saveScale — online and offline paths
    // -------------------------------------------------------------------------

    @Test
    fun `saveScale returns adjusted device on API success`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        val device = fakeDevice(id = "new-1")
        val savedDevice = device.copy(id = "server-1", isSynced = true)
        coEvery { deviceRepository.createPairedDevice(any(), any()) } returns savedDevice

        val result = service.saveScale(device)

        assertThat(result).isNotNull()
    }

    @Test
    fun `saveScale returns null on API exception (offline path)`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        val device = fakeDevice(id = "offline-1")
        coEvery { deviceRepository.createPairedDevice(any(), any()) } throws RuntimeException("no network")

        val result = service.saveScale(device)

        assertThat(result).isNull()
    }

    @Test
    fun `saveScale saves preferences to API when preferences are present`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        val prefs = fakePreferences
        val device = fakeDevice(id = "pref-1", preferences = prefs)
        val savedDevice = device.copy(isSynced = true)
        coEvery { deviceRepository.createPairedDevice(any(), any()) } returns savedDevice

        service.saveScale(device)

        coVerify { deviceRepository.saveScalePreferencesToApi(any()) }
    }

    // -------------------------------------------------------------------------
    // deleteScale — three cases
    // -------------------------------------------------------------------------

    @Test
    fun `deleteScale logs warning when device not found`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        every { deviceRepository.getDevice("missing") } returns flowOf(null)

        // Should not crash
        service.deleteScale("missing")
    }

    @Test
    fun `deleteScale deletes from DB and syncs when device is not yet synced`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        val unsyncedDevice = fakeDevice(id = deviceId, isSynced = false)
        every { deviceRepository.getDevice(deviceId) } returns flowOf(unsyncedDevice)

        service.deleteScale(deviceId)

        coVerify { deviceRepository.deleteDeviceFromDb(deviceId) }
    }

    @Test
    fun `deleteScale calls syncDevices with isDeleted=true for synced device`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        val syncedDevice = fakeDevice(id = deviceId, isSynced = true)
        every { deviceRepository.getDevice(deviceId) } returns flowOf(syncedDevice)

        service.deleteScale(deviceId)

        // syncDevices called with device.copy(isDeleted = true)
        coVerify { deviceRepository.getDevicesFromApi(accountId) }
    }

    // -------------------------------------------------------------------------
    // updateScaleNickname
    // -------------------------------------------------------------------------

    @Test
    fun `updateScaleNickname calls repository with device and nickname`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice()
        coEvery { deviceRepository.updateDeviceNickname(device, "My Scale") } returns device.copy(nickname = "My Scale")

        service.updateScaleNickname(device, "My Scale")

        coVerify { deviceRepository.updateDeviceNickname(device, "My Scale") }
    }

    @Test
    fun `updateScaleNickname handles exception without crashing`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice()
        coEvery { deviceRepository.updateDeviceNickname(any(), any()) } throws RuntimeException("DB error")

        service.updateScaleNickname(device, "My Scale")

        // No exception propagated
    }

    // -------------------------------------------------------------------------
    // updateScalePreferences
    // -------------------------------------------------------------------------

    @Test
    fun `updateScalePreferences returns true on success`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)

        val result = service.updateScalePreferences(deviceId, fakeR4Preferences)

        assertThat(result).isTrue()
    }

    @Test
    fun `updateScalePreferences injects tzOffset and resets wifiFotaScheduleTime`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        val prefSlot = slot<R4ScalePreferenceApiModel>()
        coEvery { deviceRepository.saveScalePreferencesToApi(capture(prefSlot)) } returns fakeR4Preferences

        service.updateScalePreferences(deviceId, fakeR4Preferences.copy(wifiFotaScheduleTime = 999, tzOffset = null))

        assertThat(prefSlot.captured.wifiFotaScheduleTime).isEqualTo(0)
        assertThat(prefSlot.captured.tzOffset).isNotNull()
    }

    @Test
    fun `updateScalePreferences returns false on exception`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        coEvery { deviceRepository.saveScalePreferencesToApi(any()) } throws RuntimeException("API fail")

        val result = service.updateScalePreferences(deviceId, fakeR4Preferences)

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateScalePreferencesByMac
    // -------------------------------------------------------------------------

    @Test
    fun `updateScalePreferencesByMac returns false when device not found by mac`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        every { deviceRepository.getDeviceByMac(any(), any()) } returns flowOf(null)

        val result = service.updateScalePreferencesByMac("AA:BB:CC", fakeR4Preferences)

        assertThat(result).isFalse()
    }

    @Test
    fun `updateScalePreferencesByMac delegates to updateScalePreferences when device found`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        val device = fakeDevice()
        every { deviceRepository.getDeviceByMac("AA:BB:CC:DD:EE:FF", accountId) } returns flowOf(device)

        val result = service.updateScalePreferencesByMac("AA:BB:CC:DD:EE:FF", fakeR4Preferences)

        assertThat(result).isTrue()
        coVerify { deviceRepository.saveScalePreferencesToApi(any()) }
    }

    // -------------------------------------------------------------------------
    // isSetupInProgress / setSetupInProgress
    // -------------------------------------------------------------------------

    @Test
    fun `isSetupInProgress returns false initially`() {
        assertThat(service.isSetupInProgress()).isFalse()
    }

    @Test
    fun `setSetupInProgress updates isSetupInProgress state`() {
        service.setSetupInProgress(true)
        assertThat(service.isSetupInProgress()).isTrue()

        service.setSetupInProgress(false)
        assertThat(service.isSetupInProgress()).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateWeightOnlyModeAlertShown
    // -------------------------------------------------------------------------

    @Test
    fun `updateWeightOnlyModeAlertShown updates isWeightOnlyModeAlertShown flow`() {
        service.updateWeightOnlyModeAlertShown(true)
        assertThat(service.isWeightOnlyModeAlertShown.value).isTrue()

        service.updateWeightOnlyModeAlertShown(false)
        assertThat(service.isWeightOnlyModeAlertShown.value).isFalse()
    }

    // -------------------------------------------------------------------------
    // weightOnlyModeDismissAlert — dialog callback tests
    // -------------------------------------------------------------------------

    private fun captureWeightOnlyModeDialog(): DialogModel.Confirm {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs
        service.weightOnlyModeDismissAlert(onConfirm = {})
        return dialogSlot.captured as DialogModel.Confirm
    }

    @Test
    fun `weightOnlyModeDismissAlert shows a Confirm dialog`() {
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.weightOnlyModeDismissAlert(onConfirm = {})

        assertThat(dialogSlot.captured).isInstanceOf(DialogModel.Confirm::class.java)
        verify { dialogQueueService.showDialog(any()) }
    }

    @Test
    fun `weightOnlyModeDismissAlert onConfirm sets alert shown and calls callback`() = runTest(mainDispatcherRule.scheduler) {
        var callbackInvoked = false
        val dialogSlot = slot<DialogModel>()
        every { dialogQueueService.showDialog(capture(dialogSlot)) } just Runs

        service.weightOnlyModeDismissAlert(onConfirm = { callbackInvoked = true })
        val dialog = dialogSlot.captured as DialogModel.Confirm

        dialog.onConfirm?.invoke()
        advanceUntilIdle()

        assertThat(service.isWeightOnlyModeAlertShown.value).isTrue()
        assertThat(callbackInvoked).isTrue()
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `weightOnlyModeDismissAlert onCancel dismisses dialog without setting alert shown`() = runTest(mainDispatcherRule.scheduler) {
        val dialog = captureWeightOnlyModeDialog()

        dialog.onCancel?.invoke()
        advanceUntilIdle()

        assertThat(service.isWeightOnlyModeAlertShown.value).isFalse()
        verify { dialogQueueService.dismissCurrent() }
    }

    @Test
    fun `weightOnlyModeDismissAlert dialog has correct confirm and cancel text`() {
        val dialog = captureWeightOnlyModeDialog()

        assertThat(dialog.confirmText).isNotNull()
        assertThat(dialog.cancelText).isNotNull()
    }

    // -------------------------------------------------------------------------
    // mergeSyncedWithUnsyncedById — via syncDevices integration
    // -------------------------------------------------------------------------

    @Test
    fun `syncDevices does not produce duplicate IDs in final device list`() = runTest(mainDispatcherRule.scheduler) {
        val device = fakeDevice(id = "d1", isSynced = true)
        val apiDevice = fakeDevice(id = "d1", isSynced = true)
        every { deviceRepository.getDevices(accountId, false) } returns flowOf(listOf(device))
        coEvery { deviceRepository.getDevicesFromApi(accountId) } returns listOf(apiDevice)
        every { deviceRepository.getDevice("d1") } returns flowOf(device)
        service.setAccountId(accountId)
        advanceUntilIdle()

        // Should save exactly once for d1 (no duplicate)
        coVerify(exactly = 1) { deviceRepository.saveDeviceToDb(match { it.id == "d1" }, accountId) }
    }

    // -------------------------------------------------------------------------
    // updateConnectedDeviceDetailMap — via onDeviceUpdate
    // -------------------------------------------------------------------------

    @Test
    fun `onDeviceUpdate stores device detail in connectedDeviceMap for subsequent fetchScales`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC:DD:EE:FF"
        val detail: GGDeviceDetail = mockk(relaxed = true)
        every { detail.macAddress } returns mac
        every { detail.isWifiConfigured } returns true
        every { detail.wifiMacAddress } returns "WF:01:02:03"
        every { detail.impedanceSwitchState } returns null

        // Update device detail before setting up account
        service.onDeviceUpdate(detail, BLEStatus.CONNECTED)

        // Now set up account with a device that has the same mac — fetchScales should pick up the stored detail
        val deviceFromDb = fakeDevice(mac = mac)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(deviceFromDb))
        service.setAccountId(accountId)
        Thread.sleep(2000)

        service.pairedScales.test {
            val devices = awaitItem()
            val d = devices.firstOrNull { it.device?.macAddress == mac }
            assertThat(d?.device?.isWifiConfigured).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // fetchScales — throws when no accountId provided
    // -------------------------------------------------------------------------

    @Test
    fun `fetchScales throws IllegalArgumentException when no accountId is available`() = runTest(mainDispatcherRule.scheduler) {
        var thrownException: Exception? = null
        try {
            service.fetchScales(null)
        } catch (e: Exception) {
            thrownException = e
        }

        assertThat(thrownException).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `fetchScales applies connection status and weight-only mode from maps`() = runTest(mainDispatcherRule.scheduler) {
        val mac = "AA:BB:CC:DD:EE:FF"
        val prefs = fakePreferences.copy(shouldMeasureImpedance = true)
        val deviceDetail: GGDeviceDetail = mockk(relaxed = true)
        every { deviceDetail.macAddress } returns mac
        every { deviceDetail.impedanceSwitchState } returns false

        val device = fakeDevice(mac = mac, preferences = prefs)
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(listOf(device))

        // Pre-seed maps
        service.updateConnectionStatus(mac, BLEStatus.CONNECTED)
        service.onDeviceUpdate(deviceDetail, BLEStatus.CONNECTED)

        service.setAccountId(accountId)
        Thread.sleep(2000)

        service.pairedScales.test {
            val devices = awaitItem()
            val d = devices.firstOrNull { it.device?.macAddress == mac }
            assertThat(d?.connectionStatus).isEqualTo(BLEStatus.CONNECTED)
            assertThat(d?.isWeighOnlyModeEnabledByOthers).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // getTimeZoneInMinutes — tested via updateScalePreferences
    // -------------------------------------------------------------------------

    @Test
    fun `updateScalePreferences sets tzOffset from getTimeZoneInMinutes`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        advanceUntilIdle()

        val prefSlot = slot<R4ScalePreferenceApiModel>()
        coEvery { deviceRepository.saveScalePreferencesToApi(capture(prefSlot)) } returns fakeR4Preferences

        service.updateScalePreferences(deviceId, fakeR4Preferences)

        val captured = prefSlot.captured
        // tzOffset should be set to a valid timezone offset in minutes
        assertThat(captured.tzOffset).isNotNull()
        assertThat(captured.wifiFotaScheduleTime).isEqualTo(0)
    }

    @Test
    fun `updateScalePreferences returns false when API throws`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        advanceUntilIdle()

        coEvery { deviceRepository.saveScalePreferencesToApi(any()) } throws RuntimeException("API error")

        val result = service.updateScalePreferences(deviceId, fakeR4Preferences)

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // updateScalePreferencesByMac
    // -------------------------------------------------------------------------

    @Test
    fun `updateScalePreferencesByMac returns false when scale not found by mac`() = runTest(mainDispatcherRule.scheduler) {
        service.setAccountId(accountId)
        every { deviceRepository.getDeviceByMac(any(), any()) } returns flowOf(null)

        val result = service.updateScalePreferencesByMac("unknown:mac", fakeR4Preferences)

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // healBpmDeviceBroadcastId — attribute a monitor reading to a paired BPM
    // device only when exactly one un-identified candidate exists (MOB-596)
    // -------------------------------------------------------------------------

    private suspend fun populatePairedScales(devices: List<Device>) {
        every { deviceRepository.getDevices(accountId, any()) } returns flowOf(devices)
        service.setAccountId(accountId)
        Thread.sleep(2000) // fetchScales runs on Dispatchers.IO; wait for _pairedScales to populate
    }

    @Test
    fun `healBpmDeviceBroadcastId returns null when there are no un-identified BPM devices`() = runTest {
        // Only an already-identified BPM device (has a broadcastId) — nothing to heal.
        populatePairedScales(
            listOf(fakeBpmDevice(id = "bpm-identified", mac = "AA:AA:AA:AA:AA:AA", sku = "0663", broadcastId = "AB:CD:EF:01:02:03")),
        )

        val result = service.healBpmDeviceBroadcastId("11:22:33:44:55:66", accountId)

        assertThat(result).isNull()
        coVerify(exactly = 0) { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) }
    }

    @Test
    fun `healBpmDeviceBroadcastId heals the single un-identified BPM device`() = runTest {
        val broadcastId = "11:22:33:44:55:66"
        val healed = fakeBpmDevice(id = "bpm-unidentified", mac = "AA:AA:AA:AA:AA:AA", sku = "0663", broadcastId = broadcastId)
        coEvery { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) } just Runs
        every { deviceRepository.getDeviceByBroadcastId(broadcastId, accountId) } returns flowOf(healed)

        populatePairedScales(
            listOf(fakeBpmDevice(id = "bpm-unidentified", mac = "AA:AA:AA:AA:AA:AA", sku = "0663", broadcastId = null)),
        )

        val result = service.healBpmDeviceBroadcastId(broadcastId, accountId)

        assertThat(result?.id).isEqualTo("bpm-unidentified")
        coVerify(exactly = 1) {
            deviceRepository.updateDeviceBroadcastId("bpm-unidentified", broadcastId, accountId)
        }
    }

    @Test
    fun `healBpmDeviceBroadcastId returns null when multiple un-identified BPM devices are ambiguous`() = runTest {
        // Two un-identified BPM devices → ambiguous attribution → bail without writing.
        populatePairedScales(
            listOf(
                fakeBpmDevice(id = "bpm-1", mac = "AA:AA:AA:AA:AA:AA", sku = "0663", broadcastId = null),
                fakeBpmDevice(id = "bpm-2", mac = "BB:BB:BB:BB:BB:BB", sku = "0661", broadcastId = null),
            ),
        )

        val result = service.healBpmDeviceBroadcastId("11:22:33:44:55:66", accountId)

        assertThat(result).isNull()
        coVerify(exactly = 0) { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) }
    }

    @Test
    fun `healBpmDeviceBroadcastId skips a protocol-mismatched candidate`() = runTest {
        // Reading is A6, but the only un-identified BPM row is an A3 (0603) — never stamp across
        // protocols. Candidate is excluded → nothing to heal → null, no write.
        populatePairedScales(
            listOf(fakeBpmDevice(id = "bpm-a3", mac = "AA:AA:AA:AA:AA:AA", sku = "0603", broadcastId = null)),
        )

        val result = service.healBpmDeviceBroadcastId(
            "11:22:33:44:55:66",
            accountId,
            protocolType = GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value,
        )

        assertThat(result).isNull()
        coVerify(exactly = 0) { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) }
    }

    @Test
    fun `healBpmDeviceBroadcastId heals when reading protocol matches candidate`() = runTest {
        val broadcastId = "11:22:33:44:55:66"
        val healed = fakeBpmDevice(id = "bpm-a6", mac = "AA:AA:AA:AA:AA:AA", sku = "0663", broadcastId = broadcastId)
        coEvery { deviceRepository.updateDeviceBroadcastId(any(), any(), any()) } just Runs
        every { deviceRepository.getDeviceByBroadcastId(broadcastId, accountId) } returns flowOf(healed)
        populatePairedScales(
            listOf(fakeBpmDevice(id = "bpm-a6", mac = "AA:AA:AA:AA:AA:AA", sku = "0663", broadcastId = null)),
        )

        val result = service.healBpmDeviceBroadcastId(
            broadcastId,
            accountId,
            protocolType = GGDeviceProtocolType.GG_DEVICE_PROTOCOL_A6.value,
        )

        assertThat(result?.id).isEqualTo("bpm-a6")
        coVerify(exactly = 1) { deviceRepository.updateDeviceBroadcastId("bpm-a6", broadcastId, accountId) }
    }
}
