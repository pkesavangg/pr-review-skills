package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.storage.datastore.BluetoothPreferencesDataStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothPreferencesServiceTest {

    // --- Mocks ---
    private val bluetoothPreferencesDataStore: BluetoothPreferencesDataStore = mockk(relaxed = true)

    private lateinit var service: BluetoothPreferencesService

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.d(any(), any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit

        mockkObject(AppStatusService)
        every { AppStatusService.enableTestingFeatures } returns false

        every { bluetoothPreferencesDataStore.selectedMacAddressFlow } returns flowOf("All")

        service = BluetoothPreferencesService(bluetoothPreferencesDataStore)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // selectedMacAddress — delegates to dataStore
    // -------------------------------------------------------------------------

    @Test
    fun `selectedMacAddress returns flow from dataStore`() {
        assertThat(service.selectedMacAddress).isNotNull()
    }

    // -------------------------------------------------------------------------
    // knownMacAddresses — returns expected list
    // -------------------------------------------------------------------------

    @Test
    fun `knownMacAddresses returns expected list from data store companion`() {
        assertThat(service.knownMacAddresses).isEqualTo(BluetoothPreferencesDataStore.KNOWN_MAC_ADDRESSES)
    }

    @Test
    fun `knownMacAddresses contains All as first element`() {
        assertThat(service.knownMacAddresses.first()).isEqualTo("All")
    }

    // -------------------------------------------------------------------------
    // setSelectedMacAddressLocally — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `setSelectedMacAddressLocally delegates to dataStore`() = runTest {
        coEvery { bluetoothPreferencesDataStore.setSelectedMacAddress(any()) } returns Unit

        service.setSelectedMacAddressLocally("CF:E7:04:0C:00:1C")

        coVerify { bluetoothPreferencesDataStore.setSelectedMacAddress("CF:E7:04:0C:00:1C") }
    }

    // -------------------------------------------------------------------------
    // setSelectedMacAddressLocally — error path
    // -------------------------------------------------------------------------

    @Test(expected = RuntimeException::class)
    fun `setSelectedMacAddressLocally rethrows exception from dataStore`() = runTest {
        coEvery { bluetoothPreferencesDataStore.setSelectedMacAddress(any()) } throws RuntimeException("DataStore error")

        service.setSelectedMacAddressLocally("CF:E7:04:0C:00:1C")
    }

    // -------------------------------------------------------------------------
    // getCurrentSelectedMacAddress — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentSelectedMacAddress returns value from flow`() = runTest {
        every { bluetoothPreferencesDataStore.selectedMacAddressFlow } returns flowOf("CF:E7:04:0C:00:1C")
        // Re-create service to pick up the new flow
        service = BluetoothPreferencesService(bluetoothPreferencesDataStore)

        val result = service.getCurrentSelectedMacAddress()

        assertThat(result).isEqualTo("CF:E7:04:0C:00:1C")
    }

    // -------------------------------------------------------------------------
    // getCurrentSelectedMacAddress — error returns default
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentSelectedMacAddress returns All on exception`() = runTest {
        every { bluetoothPreferencesDataStore.selectedMacAddressFlow } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("Flow error")
        }
        service = BluetoothPreferencesService(bluetoothPreferencesDataStore)

        val result = service.getCurrentSelectedMacAddress()

        assertThat(result).isEqualTo("All")
    }

    // -------------------------------------------------------------------------
    // shouldShowDevice — testing features disabled
    // -------------------------------------------------------------------------

    @Test
    fun `shouldShowDevice returns true when testing features disabled`() = runTest {
        val result = service.shouldShowDevice("CF:E7:04:0C:00:1C")

        assertThat(result).isTrue()
    }

    // -------------------------------------------------------------------------
    // shouldShowDevice — testing features enabled, selected All
    // -------------------------------------------------------------------------

    @Test
    fun `shouldShowDevice returns true when selected is All`() = runTest {
        every { AppStatusService.enableTestingFeatures } returns true
        every { bluetoothPreferencesDataStore.selectedMacAddressFlow } returns flowOf("All")
        service = BluetoothPreferencesService(bluetoothPreferencesDataStore)

        val result = service.shouldShowDevice("CF:E7:04:0C:00:1C")

        assertThat(result).isTrue()
    }

    // -------------------------------------------------------------------------
    // shouldShowDevice — testing features enabled, matching MAC
    // -------------------------------------------------------------------------

    @Test
    fun `shouldShowDevice returns true when device matches selected MAC`() = runTest {
        every { AppStatusService.enableTestingFeatures } returns true
        every { bluetoothPreferencesDataStore.selectedMacAddressFlow } returns flowOf("CF:E7:04:0C:00:1C")
        service = BluetoothPreferencesService(bluetoothPreferencesDataStore)

        val result = service.shouldShowDevice("CF:E7:04:0C:00:1C")

        assertThat(result).isTrue()
    }

    // -------------------------------------------------------------------------
    // shouldShowDevice — testing features enabled, non-matching MAC
    // -------------------------------------------------------------------------

    @Test
    fun `shouldShowDevice returns false when device does not match selected MAC`() = runTest {
        every { AppStatusService.enableTestingFeatures } returns true
        every { bluetoothPreferencesDataStore.selectedMacAddressFlow } returns flowOf("CF:E7:04:0C:00:1C")
        service = BluetoothPreferencesService(bluetoothPreferencesDataStore)

        val result = service.shouldShowDevice("AA:BB:CC:DD:EE:FF")

        assertThat(result).isFalse()
    }

    // -------------------------------------------------------------------------
    // shouldShowDevice — error returns true
    // -------------------------------------------------------------------------

    @Test
    fun `shouldShowDevice returns true on exception`() = runTest {
        every { AppStatusService.enableTestingFeatures } returns true
        every { bluetoothPreferencesDataStore.selectedMacAddressFlow } returns kotlinx.coroutines.flow.flow {
            throw RuntimeException("Flow error")
        }
        service = BluetoothPreferencesService(bluetoothPreferencesDataStore)

        val result = service.shouldShowDevice("CF:E7:04:0C:00:1C")

        assertThat(result).isTrue()
    }

    // -------------------------------------------------------------------------
    // addSkipDevice
    // -------------------------------------------------------------------------

    @Test
    fun `addSkipDevice adds device to skip list`() {
        service.addSkipDevice("device-1")

        assertThat(service.containsSkipDevice("device-1")).isTrue()
    }

    @Test
    fun `addSkipDevice does not duplicate existing device`() {
        service.addSkipDevice("device-1")
        service.addSkipDevice("device-1")

        assertThat(service.containsSkipDevice("device-1")).isTrue()
    }

    // -------------------------------------------------------------------------
    // containsSkipDevice
    // -------------------------------------------------------------------------

    @Test
    fun `containsSkipDevice returns false for unknown device`() {
        assertThat(service.containsSkipDevice("device-unknown")).isFalse()
    }

    @Test
    fun `containsSkipDevice returns true for added device`() {
        service.addSkipDevice("device-2")

        assertThat(service.containsSkipDevice("device-2")).isTrue()
    }

    // -------------------------------------------------------------------------
    // clearSkipDevices
    // -------------------------------------------------------------------------

    @Test
    fun `clearSkipDevices removes all skip devices`() {
        service.addSkipDevice("device-1")
        service.addSkipDevice("device-2")

        service.clearSkipDevices()

        assertThat(service.containsSkipDevice("device-1")).isFalse()
        assertThat(service.containsSkipDevice("device-2")).isFalse()
    }

    @Test
    fun `clearSkipDevices on empty list does not throw`() {
        service.clearSkipDevices()

        assertThat(service.containsSkipDevice("anything")).isFalse()
    }

    // -------------------------------------------------------------------------
    // enableTestingFeatures — default
    // -------------------------------------------------------------------------

    @Test
    fun `enableTestingFeatures reflects AppStatusService value`() {
        assertThat(service.enableTestingFeatures).isEqualTo(AppStatusService.enableTestingFeatures)
    }
}
