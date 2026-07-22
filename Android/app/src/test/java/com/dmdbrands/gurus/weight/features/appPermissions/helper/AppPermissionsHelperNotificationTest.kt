package com.dmdbrands.gurus.weight.features.appPermissions.helper

import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AppPermissionsHelper.hasNotificationCapableScales], the pure gate that decides
 * whether a paired-scale set justifies the POST_NOTIFICATIONS prompt (MOB-1579).
 *
 * Notification-capable = WiFi-capable scales whose readings arrive as push notifications:
 * Wifi (0385/0396), EspTouchWifi (0384/0397), BtWifiR4 (0412). Lcbt (0378/0383) is
 * Bluetooth-only (MOB-774) and is deliberately excluded. Plain Bluetooth scales, BPMs and
 * baby scales sync over BLE and do not warrant the prompt.
 */
class AppPermissionsHelperNotificationTest {

    private fun deviceWithSku(sku: String): Device = Device(sku = sku)

    @Test
    fun `returns false for an empty scale list`() {
        assertThat(AppPermissionsHelper.hasNotificationCapableScales(emptyList())).isFalse()
    }

    @Test
    fun `returns true for a WiFi scale`() {
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("0385"))),
        ).isTrue()
    }

    @Test
    fun `returns true for an EspTouchWifi scale`() {
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("0384"))),
        ).isTrue()
    }

    @Test
    fun `returns true for a BtWifiR4 scale`() {
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("0412"))),
        ).isTrue()
    }

    @Test
    fun `returns false for an Lcbt (Bluetooth-only) scale (MOB-774)`() {
        // Lcbt syncs readings over BLE, not push notifications, so it must not trigger the prompt.
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("0383"))),
        ).isFalse()
    }

    @Test
    fun `returns false for a plain Bluetooth scale`() {
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("0375"))),
        ).isFalse()
    }

    @Test
    fun `returns false for a Bluetooth blood pressure monitor`() {
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("0603"))),
        ).isFalse()
    }

    @Test
    fun `returns false for a baby scale`() {
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("0220"))),
        ).isFalse()
    }

    @Test
    fun `returns false for an unknown sku`() {
        assertThat(
            AppPermissionsHelper.hasNotificationCapableScales(listOf(deviceWithSku("9999"))),
        ).isFalse()
    }

    @Test
    fun `returns true when at least one scale in a mixed set is notification-capable`() {
        val scales = listOf(deviceWithSku("0375"), deviceWithSku("0385"))
        assertThat(AppPermissionsHelper.hasNotificationCapableScales(scales)).isTrue()
    }

    @Test
    fun `returns false when no scale in the set is notification-capable`() {
        val scales = listOf(deviceWithSku("0375"), deviceWithSku("0603"), deviceWithSku("0220"))
        assertThat(AppPermissionsHelper.hasNotificationCapableScales(scales)).isFalse()
    }
}
