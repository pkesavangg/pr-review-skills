package com.dmdbrands.gurus.weight.domain.model.storage

import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceDetails
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.DeviceEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.device.R4ScalePreferenceEntity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for the entity <-> domain device mappers and R4 preference mapping (MOB-966).
 * Pure functions — no mocks needed.
 */
class DeviceMappersTest {

    private fun deviceEntity(
        id: String = "dev-1",
        nickname: String? = null,
        deviceName: String? = "Smart Scale",
        deviceType: String? = "0375",
        userNumber: String? = "3",
    ) = DeviceEntity(
        id = id,
        accountId = "acc-1",
        peripheralIdentifier = "periph-1",
        nickname = nickname,
        sku = "0375",
        mac = "AA:BB:CC:DD:EE:FF",
        password = 200L,
        deviceName = deviceName,
        deviceType = deviceType,
        broadcastId = 100L,
        broadcastIdString = "0064",
        userNumber = userNumber,
        protocolType = "R4",
        createdAt = "2026-01-01",
        token = "tok-1",
        productType = "weight",
    )

    private fun r4Entity(id: String = "dev-1") = R4ScalePreferenceEntity(
        id = id,
        displayName = "Timmy",
        displayMetrics = listOf("weight", "bmi"),
        shouldFactoryReset = true,
        shouldMeasureImpedance = true,
        shouldMeasurePulse = false,
        timeFormat = "24h",
        tzOffset = -300,
        wifiFotaScheduleTime = 120,
        isSynced = true,
    )

    // -------------------------------------------------------------------------
    // DeviceEntity -> Device
    // -------------------------------------------------------------------------

    @Test
    fun `DeviceEntity toDeviceDomainModel maps core fields`() {
        val device = deviceEntity().toDeviceDomainModel()

        assertThat(device.id).isEqualTo("dev-1")
        assertThat(device.sku).isEqualTo("0375")
        assertThat(device.token).isEqualTo("tok-1")
        assertThat(device.deviceType).isEqualTo("0375")
        assertThat(device.userNumber).isEqualTo(3)
        assertThat(device.productType).isEqualTo("weight")
        // No R4 preference relation on the flat entity.
        assertThat(device.preferences).isNull()
    }

    @Test
    fun `DeviceEntity toDeviceDomainModel falls back to deviceName when nickname is null`() {
        val device = deviceEntity(nickname = null, deviceName = "Fallback Name").toDeviceDomainModel()
        assertThat(device.nickname).isEqualTo("Fallback Name")
    }

    @Test
    fun `DeviceEntity toDeviceDomainModel prefers nickname when present`() {
        val device = deviceEntity(nickname = "My Scale").toDeviceDomainModel()
        assertThat(device.nickname).isEqualTo("My Scale")
    }

    @Test
    fun `DeviceEntity toDeviceDomainModel handles non-numeric userNumber as null`() {
        val device = deviceEntity(userNumber = "abc").toDeviceDomainModel()
        assertThat(device.userNumber).isNull()
    }

    // -------------------------------------------------------------------------
    // DeviceDetails -> Device (with R4 preference relation)
    // -------------------------------------------------------------------------

    @Test
    fun `DeviceDetails toDeviceDomainModel maps r4 preference into preferences`() {
        val details = DeviceDetails(device = deviceEntity(), r4Preference = r4Entity())
        val device = details.toDeviceDomainModel()

        assertThat(device.preferences).isNotNull()
        assertThat(device.preferences?.displayName).isEqualTo("Timmy")
        assertThat(device.preferences?.displayMetrics).containsExactly("weight", "bmi")
        assertThat(device.preferences?.shouldFactoryReset).isTrue()
    }

    @Test
    fun `DeviceDetails toDeviceDomainModel leaves preferences null when no r4 relation`() {
        val details = DeviceDetails(device = deviceEntity(), r4Preference = null)
        assertThat(details.toDeviceDomainModel().preferences).isNull()
    }

    // -------------------------------------------------------------------------
    // R4 preference <-> Preferences round trips
    // -------------------------------------------------------------------------

    @Test
    fun `R4ScalePreferenceEntity toPreferences maps all fields including Int to Long fota time`() {
        val prefs = r4Entity().toPreferences()

        assertThat(prefs.displayName).isEqualTo("Timmy")
        assertThat(prefs.tzOffset).isEqualTo(-300)
        assertThat(prefs.shouldMeasureImpedance).isTrue()
        assertThat(prefs.shouldMeasurePulse).isFalse()
        assertThat(prefs.wifiFotaScheduleTime).isEqualTo(120L)
        assertThat(prefs.isSynced).isTrue()
    }

    @Test
    fun `Preferences toR4ScalePreferenceEntity maps back including Long to Int fota time`() {
        val prefs = Preferences(
            id = "77",
            tzOffset = 60,
            timeFormat = "12h",
            displayName = "Ana",
            displayMetrics = listOf("weight"),
            shouldMeasurePulse = true,
            shouldMeasureImpedance = false,
            shouldFactoryReset = true,
            wifiFotaScheduleTime = 3600L,
            isSynced = true,
        )
        val entity = prefs.toR4ScalePreferenceEntity()

        assertThat(entity.id).isEqualTo("77")
        assertThat(entity.displayName).isEqualTo("Ana")
        assertThat(entity.shouldMeasurePulse).isTrue()
        assertThat(entity.shouldFactoryReset).isTrue()
        assertThat(entity.wifiFotaScheduleTime).isEqualTo(3600)
    }

    @Test
    fun `Preferences toR4ScalePreferenceEntity defaults null booleans to false`() {
        val prefs = Preferences(
            id = "1",
            shouldMeasurePulse = null,
            shouldMeasureImpedance = null,
            shouldFactoryReset = null,
            wifiFotaScheduleTime = null,
        )
        val entity = prefs.toR4ScalePreferenceEntity()

        assertThat(entity.shouldMeasurePulse).isFalse()
        assertThat(entity.shouldMeasureImpedance).isFalse()
        assertThat(entity.shouldFactoryReset).isFalse()
        assertThat(entity.wifiFotaScheduleTime).isNull()
    }

    // -------------------------------------------------------------------------
    // Device -> DeviceDetails / GGBTDevice / GGDevicePreference
    // -------------------------------------------------------------------------

    @Test
    fun `Device toDeviceDetails sets accountId and maps r4 preference`() {
        val device = deviceEntity().toDeviceDomainModel().copy(preferences = r4Entity().toPreferences())
        val details = device.toDeviceDetails("acc-99")

        assertThat(details.device.accountId).isEqualTo("acc-99")
        assertThat(details.device.id).isEqualTo("dev-1")
        assertThat(details.r4Preference).isNotNull()
        assertThat(details.r4Preference?.displayName).isEqualTo("Timmy")
    }

    @Test
    fun `Device toDeviceDetails leaves r4 preference null when no preferences`() {
        val device = deviceEntity().toDeviceDomainModel()
        assertThat(device.toDeviceDetails("acc-1").r4Preference).isNull()
    }

    @Test
    fun `Preferences toGGDevicePreference maps fields`() {
        val gg = r4Entity().toPreferences().toGGDevicePreference()
        assertThat(gg.displayName).isEqualTo("Timmy")
        assertThat(gg.tzOffset).isEqualTo(-300)
    }

    @Test
    fun `Device toGGBTDevice falls back to nickname when deviceName absent and carries syncAllData`() {
        val device = Device(nickname = "Nick", token = "t1", userNumber = 2)
        val gg = device.toGGBTDevice(syncAllData = true)

        assertThat(gg.name).isEqualTo("Nick")
        assertThat(gg.token).isEqualTo("t1")
        assertThat(gg.syncAllData).isTrue()
    }
}
