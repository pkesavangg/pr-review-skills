package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.BuildConfig
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

class AppStatusServiceTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    // -------------------------------------------------------------------------
    // isDev — mirrors BuildConfig.DEBUG
    // -------------------------------------------------------------------------

    @Test
    fun `isDev matches BuildConfig DEBUG`() {
        assertThat(AppStatusService.isDev).isEqualTo(BuildConfig.DEBUG)
    }

    // -------------------------------------------------------------------------
    // version — mirrors BuildConfig.VERSION_NAME
    // -------------------------------------------------------------------------

    @Test
    fun `version matches BuildConfig VERSION_NAME`() {
        assertThat(AppStatusService.version).isEqualTo(BuildConfig.VERSION_NAME)
    }

    @Test
    fun `version is not empty`() {
        assertThat(AppStatusService.version).isNotEmpty()
    }

    // -------------------------------------------------------------------------
    // apiUrl — mirrors BuildConfig.BASE_URL
    // -------------------------------------------------------------------------

    @Test
    fun `apiUrl matches BuildConfig BASE_URL`() {
        assertThat(AppStatusService.apiUrl).isEqualTo(BuildConfig.BASE_URL)
    }

    @Test
    fun `apiUrl is not empty`() {
        assertThat(AppStatusService.apiUrl).isNotEmpty()
    }

    // -------------------------------------------------------------------------
    // isNative — always true on Android
    // -------------------------------------------------------------------------

    @Test
    fun `isNative is true`() {
        assertThat(AppStatusService.isNative).isTrue()
    }

    // -------------------------------------------------------------------------
    // isAndroid — always true
    // -------------------------------------------------------------------------

    @Test
    fun `isAndroid is true`() {
        assertThat(AppStatusService.isAndroid).isTrue()
    }

    // -------------------------------------------------------------------------
    // isMetric — default false
    // -------------------------------------------------------------------------

    @Test
    fun `isMetric is false by default`() {
        assertThat(AppStatusService.isMetric).isFalse()
    }

    // -------------------------------------------------------------------------
    // enableTestingFeatures — mirrors BuildConfig.DEBUG
    // -------------------------------------------------------------------------

    @Test
    fun `enableTestingFeatures matches BuildConfig DEBUG`() {
        assertThat(AppStatusService.enableTestingFeatures).isEqualTo(BuildConfig.DEBUG)
    }

    // -------------------------------------------------------------------------
    // showDownloadLogOption — mirrors BuildConfig.DEBUG
    // -------------------------------------------------------------------------

    @Test
    fun `showDownloadLogOption matches BuildConfig DEBUG`() {
        assertThat(AppStatusService.showDownloadLogOption).isEqualTo(BuildConfig.DEBUG)
    }

    // -------------------------------------------------------------------------
    // canShowRateAppItem — always true
    // -------------------------------------------------------------------------

    @Test
    fun `canShowRateAppItem is true`() {
        assertThat(AppStatusService.canShowRateAppItem).isTrue()
    }

    // -------------------------------------------------------------------------
    // getCurrentDateTime — formatted date string
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

    // -------------------------------------------------------------------------
    // getUserTimezone — system default timezone ID
    // -------------------------------------------------------------------------

    @Test
    fun `getUserTimezone returns non-empty string`() {
        assertThat(AppStatusService.getUserTimezone()).isNotEmpty()
    }

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
    // getUserTimezoneOffset — positive and negative offset branches
    // -------------------------------------------------------------------------

    @Test
    fun `getUserTimezoneOffset returns non-empty string`() {
        assertThat(AppStatusService.getUserTimezoneOffset()).isNotEmpty()
    }

    @Test
    fun `getUserTimezoneOffset returns positive offset with plus prefix`() {
        // UTC+9 (Asia/Tokyo, no DST)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        val result = AppStatusService.getUserTimezoneOffset()
        assertThat(result).isEqualTo("+540")
    }

    @Test
    fun `getUserTimezoneOffset returns negative offset without plus prefix`() {
        // Etc/GMT+5 = UTC-5 (no DST)
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/GMT+5"))
        val result = AppStatusService.getUserTimezoneOffset()
        assertThat(result).isEqualTo("-300")
    }

    @Test
    fun `getUserTimezoneOffset returns plus zero for UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val result = AppStatusService.getUserTimezoneOffset()
        assertThat(result).isEqualTo("+0")
    }
}
