package com.dmdbrands.gurus.weight.domain.model.api.device

import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class DeviceApiMapperTest {

    private fun ggDetail(
        mac: String = "AA:BB:CC:DD",
        broadcastId: String? = "01000000",
        password: String? = "02000000",
        name: String = "Scale",
        identifier: String = "pid-1",
    ) = mockk<GGDeviceDetail> {
        every { macAddress } returns mac
        every { this@mockk.broadcastId } returns broadcastId
        every { this@mockk.password } returns password
        every { deviceName } returns name
        every { this@mockk.identifier } returns identifier
    }

    // ── toPairedDeviceRequest ─────────────────────────────────────────────────

    @Test
    fun `toPairedDeviceRequest inverts deviceType and type and maps fields`() {
        val device = Device(
            device = ggDetail(),
            deviceType = "btWifiR4",
            productType = "weight_scale",
            nickname = "My Scale",
            sku = "9395",
            userNumber = 1,
            token = "tok-123",
        )

        val req = device.toPairedDeviceRequest()

        // The unified API's deviceType is the product category, type is the connection protocol.
        assertThat(req.deviceType).isEqualTo("weight_scale") // = device.productType
        assertThat(req.type).isEqualTo("btWifiR4")           // = device.deviceType
        assertThat(req.nickname).isEqualTo("My Scale")
        assertThat(req.sku).isEqualTo("9395")
        assertThat(req.mac).isEqualTo("AA:BB:CC:DD")
        assertThat(req.name).isEqualTo("Scale")
        assertThat(req.peripheralIdentifier).isEqualTo("pid-1")
        assertThat(req.userNumber).isEqualTo(1)
        assertThat(req.scaleToken).isEqualTo("tok-123")
        // broadcastId/password are hex strings converted to little-endian Long.
        assertThat(req.broadcastId).isEqualTo(1L) // convertHexToInt("01000000")
        assertThat(req.password).isEqualTo(2L)    // convertHexToInt("02000000")
    }

    @Test
    fun `toPairedDeviceRequest applies fallbacks when product fields are absent`() {
        val device = Device(
            device = null,
            deviceType = null,
            productType = null,
            nickname = "n",
            sku = null,
        )

        val req = device.toPairedDeviceRequest()

        assertThat(req.deviceType).isEqualTo("weight_scale") // productType ?: "weight_scale"
        assertThat(req.type).isEqualTo("")                   // deviceType ?: ""
        assertThat(req.sku).isEqualTo("")                    // sku ?: ""
        assertThat(req.mac).isNull()
        assertThat(req.broadcastId).isNull()
        assertThat(req.password).isNull()
    }

    // ── convertHexToInt ───────────────────────────────────────────────────────

    @Test
    fun `convertHexToInt reverses byte order and parses hex`() {
        assertThat(convertHexToInt("01000000")).isEqualTo(1L)
        assertThat(convertHexToInt("FF000000")).isEqualTo(255L)
    }

    @Test
    fun `convertHexToInt returns null for null or blank`() {
        assertThat(convertHexToInt(null)).isNull()
        assertThat(convertHexToInt("")).isNull()
        assertThat(convertHexToInt("   ")).isNull()
    }

    // ── convertIntToHex — MAC-width (12-char) padding ─────────────────────────

    @Test
    fun `convertIntToHex round-trips a baby-scale MAC with a trailing zero byte`() {
        // A baby-scale broadcastId is the 6-byte MAC. This one ends in a zero byte, so the
        // reversed int drops leading zeros — only 12-char padding recovers the full MAC.
        val macHex = "F88FC8F50000"
        val asInt = convertHexToInt(macHex)

        assertThat(convertIntToHex(asInt, "babyScale")).isEqualTo(macHex)   // local deviceType
        assertThat(convertIntToHex(asInt, "baby_scale")).isEqualTo(macHex)  // unified API category
        assertThat(convertIntToHex(asInt, "btWifiR4")).isEqualTo(macHex)    // R4 unchanged
    }

    @Test
    fun `convertIntToHex keeps legacy 8-char behavior for non-MAC devices`() {
        // A generic bluetooth (A6) device must NOT get 12-char padding — behavior unchanged.
        val asInt = convertHexToInt("F88FC8F50000")
        assertThat(convertIntToHex(asInt, "bluetooth")).isEqualTo("F88FC8F5")
    }
}
