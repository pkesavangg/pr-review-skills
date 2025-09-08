package com.dmdbrands.gurus.weight.data.storage.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.dmdbrands.gurus.weight.proto.BluetoothPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension property to provide BluetoothPreferences DataStore instance from Context.
 */
val Context.bluetoothPreferencesDataStore: DataStore<BluetoothPreferences> by dataStore(
  fileName = "bluetooth_preferences.pb",
  serializer = BluetoothPreferencesSerializer,
)

/**
 * DataStore for managing Bluetooth preferences.
 * Handles MAC address selection for 0412 scale setup filtering.
 */
@Singleton
class BluetoothPreferencesDataStore @Inject constructor(
  @ApplicationContext private val context: Context,
) : BaseProtoDataStore<BluetoothPreferences>(
  dataStore = context.bluetoothPreferencesDataStore,
) {

  companion object {
    private const val DEFAULT_MAC_ADDRESS = "All"

    // Known MAC addresses from Angular implementation (app-strings.ts)
    val KNOWN_MAC_ADDRESSES = listOf(
      "All",
      "CF:E7:04:0C:00:1C",
      "CF:E6:0C:0C:80:02",
      "CF:E6:0C:0C:80:06",
      "CF:E6:08:04:00:1C",
      "CF:E6:08:04:00:2E",
      "CF:E7:04:0C:00:09",
      "CF:E7:04:0C:00:1F",
      "CF:E7:04:0C:00:08",
      "CF:E7:04:0C:00:33"
    )
  }

  /**
   * Emits a Flow of the selected MAC address.
   */
  val selectedMacAddressFlow: Flow<String> = dataFlow.map {
    it.selectedMacAddress.ifEmpty { DEFAULT_MAC_ADDRESS }
  }

  /**
   * Sets the selected MAC address.
   * @param macAddress The MAC address to set as selected
   */
  suspend fun setSelectedMacAddress(macAddress: String) {
    updateData { current ->
      current.toBuilder()
        .setSelectedMacAddress(macAddress)
        .build()
    }
  }

  /**
   * Gets the currently selected MAC address.
   * @return The selected MAC address, or default if not set
   */
  suspend fun getSelectedMacAddress(): String {
    val current = getData()
    return current.selectedMacAddress.ifEmpty { DEFAULT_MAC_ADDRESS }
  }



  /**
   * Resets the selected MAC address to default.
   */
  suspend fun resetSelectedMacAddress() {
    setSelectedMacAddress(DEFAULT_MAC_ADDRESS)
  }

  override fun getDefaultInstance(): BluetoothPreferences =
    BluetoothPreferences.newBuilder()
      .setSelectedMacAddress(DEFAULT_MAC_ADDRESS)
      .build()

  /**
   * Clears all Bluetooth preferences data.
   */
  override suspend fun clearData() {
    updateData { getDefaultInstance() }
  }
}

/**
 * Serializer for BluetoothPreferences proto.
 */
object BluetoothPreferencesSerializer : Serializer<BluetoothPreferences> {
  override val defaultValue: BluetoothPreferences =
    BluetoothPreferences.newBuilder()
      .setSelectedMacAddress("All")
      .build()

  override suspend fun readFrom(input: InputStream): BluetoothPreferences =
    BluetoothPreferences.parseFrom(input)

  override suspend fun writeTo(
    t: BluetoothPreferences,
    output: OutputStream,
  ) = t.writeTo(output)
}
