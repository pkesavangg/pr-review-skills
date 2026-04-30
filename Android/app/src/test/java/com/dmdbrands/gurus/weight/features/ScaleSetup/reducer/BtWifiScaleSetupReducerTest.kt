package com.dmdbrands.gurus.weight.features.ScaleSetup.reducer

import com.greatergoods.ggbluetoothsdk.external.models.GGWifiInfo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BtWifiScaleSetupReducerTest {

    private lateinit var reducer: BtWifiScaleSetupReducer
    private val initialState = BtWifiScaleSetupState()

    @BeforeEach
    fun setUp() {
        reducer = BtWifiScaleSetupReducer()
    }

    private fun aWifi(ssid: String, mac: String = ""): GGWifiInfo = GGWifiInfo().apply {
        this.ssid = ssid
        this.macAddress = mac
    }

    // -------------------------------------------------------------------------
    // SetWifiList — dedupe behavior (guards the LazyColumn duplicate-key crash)
    // -------------------------------------------------------------------------

    @Test
    fun `SetWifiList keeps both entries when SSID matches but MAC differs`() {
        val result = reducer.reduce(
            initialState,
            BtWifiScaleSetupIntent.SetWifiList(
                listOf(
                    aWifi(ssid = "SW_2nd Floor", mac = "AA:BB:CC:00:00:01"),
                    aWifi(ssid = "SW_2nd Floor", mac = "AA:BB:CC:00:00:02"),
                ),
            ),
        )

        assertThat(result?.wifiList).hasSize(2)
        assertThat(result?.wifiList?.map { it.listKey() })
            .containsExactly("AA:BB:CC:00:00:01|SW_2nd Floor", "AA:BB:CC:00:00:02|SW_2nd Floor")
            .inOrder()
    }

    @Test
    fun `SetWifiList drops duplicate scan entries with identical SSID and MAC`() {
        val result = reducer.reduce(
            initialState,
            BtWifiScaleSetupIntent.SetWifiList(
                listOf(
                    aWifi(ssid = "Home", mac = "AA:BB:CC:00:00:01"),
                    aWifi(ssid = "Home", mac = "AA:BB:CC:00:00:01"),
                ),
            ),
        )

        assertThat(result?.wifiList).hasSize(1)
    }

    @Test
    fun `SetWifiList collapses entries sharing SSID when MAC is blank`() {
        val result = reducer.reduce(
            initialState,
            BtWifiScaleSetupIntent.SetWifiList(
                listOf(
                    aWifi(ssid = "Open-AP", mac = ""),
                    aWifi(ssid = "Open-AP", mac = ""),
                ),
            ),
        )

        assertThat(result?.wifiList).hasSize(1)
        assertThat(result?.wifiList?.first()?.listKey()).isEqualTo("|Open-AP")
    }

    @Test
    fun `SetWifiList preserves order of first occurrence after dedupe`() {
        val result = reducer.reduce(
            initialState,
            BtWifiScaleSetupIntent.SetWifiList(
                listOf(
                    aWifi(ssid = "A", mac = "AA:00:00:00:00:01"),
                    aWifi(ssid = "B", mac = "AA:00:00:00:00:02"),
                    aWifi(ssid = "A", mac = "AA:00:00:00:00:01"),
                ),
            ),
        )

        assertThat(result?.wifiList?.map { it.ssid }).containsExactly("A", "B").inOrder()
    }

    @Test
    fun `listKey uses pipe separator so composite keys cannot collide`() {
        // "aa_bb" + "cc" and "aa" + "bb_cc" would collide under an underscore separator.
        val a = aWifi(ssid = "cc", mac = "aa_bb")
        val b = aWifi(ssid = "bb_cc", mac = "aa")

        assertThat(a.listKey()).isNotEqualTo(b.listKey())
    }

    @Test
    fun `listKey treats null SSID and null MAC as empty strings`() {
        // GGWifiInfo fields come from Java and are platform-typed, so they can be
        // null at runtime even if Kotlin doesn't flag it. listKey() must not
        // produce a literal "null" substring.
        val wifi = GGWifiInfo() // default constructor leaves fields null

        assertThat(wifi.listKey()).isEqualTo("|")
    }

    @Test
    fun `SetWifiList dedupes two entries with null SSID and same MAC`() {
        val nullSsidA = GGWifiInfo().apply { macAddress = "AA:BB:CC:00:00:01" }
        val nullSsidB = GGWifiInfo().apply { macAddress = "AA:BB:CC:00:00:01" }

        val result = reducer.reduce(
            initialState,
            BtWifiScaleSetupIntent.SetWifiList(listOf(nullSsidA, nullSsidB)),
        )

        assertThat(result?.wifiList).hasSize(1)
    }

    @Test
    fun `repeated SetWifiList with overlapping inputs stays dedupe-stable across refreshes`() {
        // Mirrors the ticket symptom: user taps Refresh repeatedly and each scan
        // returns overlapping duplicates. State after each reduce must remain a
        // unique-key list so LazyColumn never sees the same key twice.
        val firstScan = listOf(
            aWifi(ssid = "Home-2.4G", mac = "AA:BB:CC:00:00:01"),
            aWifi(ssid = "Home-5G", mac = "AA:BB:CC:00:00:02"),
            aWifi(ssid = "Home-2.4G", mac = "AA:BB:CC:00:00:01"), // duplicate
        )
        val secondScan = listOf(
            aWifi(ssid = "Home-2.4G", mac = "AA:BB:CC:00:00:01"),
            aWifi(ssid = "Home-2.4G", mac = "AA:BB:CC:00:00:01"), // duplicate
            aWifi(ssid = "Home-5G", mac = "AA:BB:CC:00:00:02"),
            aWifi(ssid = "Cafe", mac = "AA:BB:CC:00:00:03"), // newly visible
        )

        val afterFirst = reducer.reduce(initialState, BtWifiScaleSetupIntent.SetWifiList(firstScan))
        val afterSecond = reducer.reduce(afterFirst ?: initialState, BtWifiScaleSetupIntent.SetWifiList(secondScan))

        assertThat(afterFirst?.wifiList?.map { it.listKey() })
            .containsExactly("AA:BB:CC:00:00:01|Home-2.4G", "AA:BB:CC:00:00:02|Home-5G")
            .inOrder()
        assertThat(afterSecond?.wifiList?.map { it.listKey() })
            .containsExactly(
                "AA:BB:CC:00:00:01|Home-2.4G",
                "AA:BB:CC:00:00:02|Home-5G",
                "AA:BB:CC:00:00:03|Cafe",
            )
            .inOrder()
        assertThat(afterSecond?.wifiList?.map { it.listKey() }?.toSet()?.size)
            .isEqualTo(afterSecond?.wifiList?.size)
    }
}
