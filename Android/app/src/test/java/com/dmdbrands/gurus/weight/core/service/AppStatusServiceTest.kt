package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.BuildConfig
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.TimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AppStatusServiceTest {

    private lateinit var originalTimeZone: TimeZone

    @BeforeEach
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @AfterEach
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    // -------------------------------------------------------------------------
    // Constant properties — BuildConfig mirrors
    // -------------------------------------------------------------------------

    @Test
    fun `isDev returns BuildConfig DEBUG`() {
        assertThat(AppStatusService.isDev).isEqualTo(BuildConfig.DEBUG)
    }

    @Test
    fun `version returns BuildConfig VERSION_NAME`() {
        assertThat(AppStatusService.version).isEqualTo(BuildConfig.VERSION_NAME)
    }

    @Test
    fun `apiUrl returns BuildConfig BASE_URL`() {
        assertThat(AppStatusService.apiUrl).isEqualTo(BuildConfig.BASE_URL)
    }

    @Test
    fun `enableTestingFeatures returns BuildConfig DEBUG`() {
        assertThat(AppStatusService.enableTestingFeatures).isEqualTo(BuildConfig.DEBUG)
    }

    @Test
    fun `showDownloadLogOption returns BuildConfig DEBUG`() {
        assertThat(AppStatusService.showDownloadLogOption).isEqualTo(BuildConfig.DEBUG)
    }

    // -------------------------------------------------------------------------
    // Constant properties — hardcoded values
    // -------------------------------------------------------------------------

    @Test
    fun `isNative returns true`() {
        assertThat(AppStatusService.isNative).isTrue()
    }

    @Test
    fun `isAndroid returns true`() {
        assertThat(AppStatusService.isAndroid).isTrue()
    }

    @Test
    fun `isMetric returns false`() {
        assertThat(AppStatusService.isMetric).isFalse()
    }

    @Test
    fun `canShowRateAppItem returns true`() {
        assertThat(AppStatusService.canShowRateAppItem).isTrue()
    }

    // -------------------------------------------------------------------------
    // getCurrentDateTime — formatted date string "MMM dd, h:mm a"
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentDateTime returns non-empty string`() {
        assertThat(AppStatusService.getCurrentDateTime()).isNotEmpty()
    }

    @Test
    fun `getCurrentDateTime matches expected format pattern`() {
        val result = AppStatusService.getCurrentDateTime()
        // Format: "MMM dd, h:mm a" e.g. "Mar 12, 3:45 PM"
        assertThat(result).matches("[A-Z][a-z]{2} \\d{2}, \\d{1,2}:\\d{2} [AaPp][Mm]")
    }

    @Test
    fun `getCurrentDateTime produces consistent format across timezones`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val result = AppStatusService.getCurrentDateTime()
        assertThat(result).matches("[A-Z][a-z]{2} \\d{2}, \\d{1,2}:\\d{2} [AaPp][Mm]")
    }

    // -------------------------------------------------------------------------
    // getUserTimezone — system default timezone ID
    // -------------------------------------------------------------------------

    @Test
    fun `getUserTimezone returns system default zone ID`() {
        assertThat(AppStatusService.getUserTimezone()).isEqualTo(ZoneId.systemDefault().id)
    }

    @Test
    fun `getUserTimezone reflects changed timezone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertThat(AppStatusService.getUserTimezone()).isEqualTo("Asia/Tokyo")
    }

    // -------------------------------------------------------------------------
    // getUserTimezoneOffset — positive, negative, zero, and fractional offsets
    // -------------------------------------------------------------------------

    @Test
    fun `getUserTimezoneOffset returns positive offset with plus prefix`() {
        // UTC+9 (Asia/Tokyo, no DST)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertThat(AppStatusService.getUserTimezoneOffset()).isEqualTo("+540")
    }

    @Test
    fun `getUserTimezoneOffset returns negative offset without plus prefix`() {
        // Etc/GMT+5 = UTC-5 (no DST)
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+5"))
        assertThat(AppStatusService.getUserTimezoneOffset()).isEqualTo("-300")
    }

    @Test
    fun `getUserTimezoneOffset returns plus zero for UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertThat(AppStatusService.getUserTimezoneOffset()).isEqualTo("+0")
    }

    @Test
    fun `getUserTimezoneOffset handles half-hour offset timezone`() {
        // Asia/Kolkata = UTC+5:30 → 330 minutes
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        assertThat(AppStatusService.getUserTimezoneOffset()).isEqualTo("+330")
    }
}
