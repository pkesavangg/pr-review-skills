package com.dmdbrands.gurus.weight.data.storage.datastore

import android.content.Context
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.proto.BluetoothPreferences
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
class BluetoothPreferencesDataStoreTest {

    private lateinit var fakeDataStore: FakeDataStore<BluetoothPreferences>
    private lateinit var bluetoothDataStore: BluetoothPreferencesDataStore

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.i(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Exception>()) } returns Unit

        mockkStatic("com.dmdbrands.gurus.weight.data.storage.datastore.BluetoothPreferencesDataStoreKt")
        val mockContext = mockk<Context>(relaxed = true)
        fakeDataStore = FakeDataStore(BluetoothPreferences.getDefaultInstance())
        every { mockContext.bluetoothPreferencesDataStore } returns fakeDataStore
        bluetoothDataStore = BluetoothPreferencesDataStore(mockContext)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // selectedMacAddressFlow
    // -------------------------------------------------------------------------

    @Test
    fun `selectedMacAddressFlow emits All as default`() = runTest {
        bluetoothDataStore.selectedMacAddressFlow.test {
            assertThat(awaitItem()).isEqualTo("All")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectedMacAddressFlow emits stored mac address`() = runTest {
        bluetoothDataStore.setSelectedMacAddress("CF:E7:04:0C:00:1C")

        bluetoothDataStore.selectedMacAddressFlow.test {
            assertThat(awaitItem()).isEqualTo("CF:E7:04:0C:00:1C")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setSelectedMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `setSelectedMacAddress stores the value`() = runTest {
        bluetoothDataStore.setSelectedMacAddress("CF:E6:0C:0C:80:02")

        val result = bluetoothDataStore.getSelectedMacAddress()
        assertThat(result).isEqualTo("CF:E6:0C:0C:80:02")
    }

    @Test
    fun `setSelectedMacAddress overwrites previous value`() = runTest {
        bluetoothDataStore.setSelectedMacAddress("CF:E7:04:0C:00:1C")
        bluetoothDataStore.setSelectedMacAddress("CF:E6:08:04:00:1C")

        val result = bluetoothDataStore.getSelectedMacAddress()
        assertThat(result).isEqualTo("CF:E6:08:04:00:1C")
    }

    // -------------------------------------------------------------------------
    // getSelectedMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `getSelectedMacAddress returns All as default when empty`() = runTest {
        val result = bluetoothDataStore.getSelectedMacAddress()
        assertThat(result).isEqualTo("All")
    }

    @Test
    fun `getSelectedMacAddress returns stored value`() = runTest {
        bluetoothDataStore.setSelectedMacAddress("CF:E8:06:14:08:12")

        val result = bluetoothDataStore.getSelectedMacAddress()
        assertThat(result).isEqualTo("CF:E8:06:14:08:12")
    }

    // -------------------------------------------------------------------------
    // resetSelectedMacAddress
    // -------------------------------------------------------------------------

    @Test
    fun `resetSelectedMacAddress sets to All default`() = runTest {
        bluetoothDataStore.setSelectedMacAddress("CF:E7:04:0C:00:1C")

        bluetoothDataStore.resetSelectedMacAddress()

        val result = bluetoothDataStore.getSelectedMacAddress()
        assertThat(result).isEqualTo("All")
    }

    // -------------------------------------------------------------------------
    // clearData
    // -------------------------------------------------------------------------

    @Test
    fun `clearData resets to default with All mac address`() = runTest {
        bluetoothDataStore.setSelectedMacAddress("CF:E7:04:0C:00:1C")

        bluetoothDataStore.clearData()

        val result = bluetoothDataStore.getSelectedMacAddress()
        assertThat(result).isEqualTo("All")
    }

    // -------------------------------------------------------------------------
    // KNOWN_MAC_ADDRESSES companion
    // -------------------------------------------------------------------------

    @Test
    fun `KNOWN_MAC_ADDRESSES contains expected entries`() {
        assertThat(BluetoothPreferencesDataStore.KNOWN_MAC_ADDRESSES).hasSize(11)
        assertThat(BluetoothPreferencesDataStore.KNOWN_MAC_ADDRESSES.first()).isEqualTo("All")
    }
}
