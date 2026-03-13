package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.activity.ComponentActivity
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.greatergoods.lib.wificonnect.WifiSmartConnectManager
import com.greatergoods.lib.wificonnect.model.ApConnectParams
import com.greatergoods.lib.wificonnect.model.ApConnectResult
import com.greatergoods.lib.wificonnect.model.EsptouchParams
import com.greatergoods.lib.wificonnect.model.EsptouchResult
import com.greatergoods.lib.wificonnect.model.SmartConfigParams
import com.greatergoods.lib.wificonnect.model.SmartConfigResult
import com.greatergoods.lib.wificonnect.model.WifiConnectRequest
import com.greatergoods.lib.wificonnect.model.WifiConnectResult
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiScaleServiceTest {

    // --- Mocks ---
    private val wifiSmartConnectManager: WifiSmartConnectManager = mockk(relaxed = true)
    private val deviceService: IDeviceService = mockk()
    private val context: Context = mockk(relaxed = true)
    private val activity: ComponentActivity = mockk(relaxed = true)
    private val wifiManager: WifiManager = mockk(relaxed = true)

    private lateinit var service: WifiScaleService

    // --- Test fixtures ---
    private val testSsid = "MyWiFi"
    private val testBssid = "AA:BB:CC:DD:EE:FF"
    private val testPassword = "password123"
    private val testToken = "abc123token"
    private val testUserNumber = 5

    private val validEsptouchInfo = WifiSetupInfo(
        ssid = testSsid,
        bssid = testBssid,
        password = testPassword,
        userNumber = testUserNumber,
        token = testToken,
    )

    private val validFirstInfo = WifiSetupInfo(
        ssid = testSsid,
        password = testPassword,
        userNumber = testUserNumber,
        token = testToken,
    )

    private val validJoinInfo = WifiSetupInfo(
        userNumber = testUserNumber,
        token = testToken,
    )

    private val validChangeInfo = WifiSetupInfo(
        ssid = testSsid,
        password = testPassword,
        userNumber = testUserNumber,
        token = testToken,
    )

    @Before
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.w(any(), any()) } returns Unit

        every { activity.getSystemService(Context.WIFI_SERVICE) } returns wifiManager

        service = WifiScaleService(wifiSmartConnectManager, deviceService, context)
        service.initialise(activity)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // initialise — activity setup
    // -------------------------------------------------------------------------

    @Test
    fun `initialise stores activity and calls manager initialise`() {
        val newActivity: ComponentActivity = mockk(relaxed = true)

        service.initialise(newActivity)

        verify(exactly = 1) { wifiSmartConnectManager.initialise(newActivity) }
    }

    // -------------------------------------------------------------------------
    // connect — ESP_TOUCH_WIFI request building
    // -------------------------------------------------------------------------

    @Test
    fun `connect ESP_TOUCH_WIFI builds correct EsptouchRequest`() {
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.Esptouch(EsptouchResult.Success("device", "mac"))

        service.connect(validEsptouchInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.Esptouch
        assertThat(captured.params.ssid).isEqualTo(testSsid)
        assertThat(captured.params.bssid).isEqualTo(testBssid)
        assertThat(captured.params.password).isEqualTo(testPassword)
        assertThat(captured.params.userNumber).isEqualTo(testUserNumber)
        assertThat(captured.params.token).isEqualTo(testToken)
    }

    // -------------------------------------------------------------------------
    // connect — FIRST request building
    // -------------------------------------------------------------------------

    @Test
    fun `connect FIRST builds correct SmartConfigRequest`() {
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Success)

        service.connect(validFirstInfo, WifiSetupType.FIRST, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.SmartConfig
        assertThat(captured.params.ssid).isEqualTo(testSsid)
        assertThat(captured.params.password).isEqualTo(testPassword)
        assertThat(captured.params.userNumber).isEqualTo(testUserNumber)
        assertThat(captured.params.tokenHexString).isEqualTo(testToken)
    }

    // -------------------------------------------------------------------------
    // connect — JOIN request building
    // -------------------------------------------------------------------------

    @Test
    fun `connect JOIN builds SmartConfigRequest with empty ssid when null`() {
        val joinInfo = WifiSetupInfo(userNumber = 3, token = "tok")
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Success)

        service.connect(joinInfo, WifiSetupType.JOIN, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.SmartConfig
        assertThat(captured.params.ssid).isEqualTo("")
        assertThat(captured.params.userNumber).isEqualTo(3)
        assertThat(captured.params.tokenHexString).isEqualTo("tok")
    }

    // -------------------------------------------------------------------------
    // connect — CHANGE request building (AP Mode)
    // -------------------------------------------------------------------------

    @Test
    fun `connect CHANGE builds correct ApModeRequest`() {
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.ApMode(ApConnectResult.Success(byteArrayOf()))

        service.connect(validChangeInfo, WifiSetupType.CHANGE, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.ApMode
        assertThat(captured.params.ssid).isEqualTo(testSsid)
        assertThat(captured.params.password).isEqualTo(testPassword)
        assertThat(captured.params.userNumber).isEqualTo(testUserNumber)
        assertThat(captured.params.tokenHexString).isEqualTo(testToken)
    }

    @Test
    fun `connect CHANGE uses default userNumber 1 when null`() {
        val changeInfo = WifiSetupInfo(ssid = "net", password = "pw", token = "t")
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.ApMode(ApConnectResult.Success(byteArrayOf()))

        service.connect(changeInfo, WifiSetupType.CHANGE, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.ApMode
        assertThat(captured.params.userNumber).isEqualTo(1)
    }

    @Test
    fun `connect ESP_TOUCH_WIFI with null userNumber fails validation`() {
        // userNumber is null → validateSetupData requires it non-null for ESP_TOUCH_WIFI
        val info = WifiSetupInfo(ssid = "s", bssid = "b", password = "p", token = "t")

        var errorMsg: String? = null
        service.connect(info, WifiSetupType.ESP_TOUCH_WIFI, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    @Test
    fun `connect FIRST with null userNumber fails validation`() {
        // userNumber is null → validateSetupData requires it non-null for FIRST
        val info = WifiSetupInfo(ssid = "s", password = "p", token = "t")

        var errorMsg: String? = null
        service.connect(info, WifiSetupType.FIRST, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    // -------------------------------------------------------------------------
    // connect — success paths
    // -------------------------------------------------------------------------

    @Test
    fun `connect Esptouch success invokes onSuccess`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.Esptouch(EsptouchResult.Success("dev", "mac"))

        var successCalled = false
        service.connect(validEsptouchInfo, WifiSetupType.ESP_TOUCH_WIFI, { successCalled = true }, {})
        Thread.sleep(300)

        assertThat(successCalled).isTrue()
    }

    @Test
    fun `connect SmartConfig success invokes onSuccess`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Success)

        var successCalled = false
        service.connect(validFirstInfo, WifiSetupType.FIRST, { successCalled = true }, {})
        Thread.sleep(300)

        assertThat(successCalled).isTrue()
    }

    @Test
    fun `connect ApMode success invokes onSuccess`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.ApMode(ApConnectResult.Success(byteArrayOf(1, 2)))

        var successCalled = false
        service.connect(validChangeInfo, WifiSetupType.CHANGE, { successCalled = true }, {})
        Thread.sleep(300)

        assertThat(successCalled).isTrue()
    }

    // -------------------------------------------------------------------------
    // connect — failure paths
    // -------------------------------------------------------------------------

    @Test
    fun `connect Esptouch failure invokes onError with message`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.Esptouch(EsptouchResult.Failure("timeout"))

        var errorMsg: String? = null
        service.connect(validEsptouchInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Esptouch connection failed")
        assertThat(errorMsg).contains("timeout")
    }

    @Test
    fun `connect SmartConfig failure invokes onError with message`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Failure("no response"))

        var errorMsg: String? = null
        service.connect(validFirstInfo, WifiSetupType.FIRST, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("SmartConfig connection failed")
        assertThat(errorMsg).contains("no response")
    }

    // -------------------------------------------------------------------------
    // connect — validation failure paths
    // -------------------------------------------------------------------------

    @Test
    fun `connect FIRST with missing ssid calls onError with validation message`() {
        val invalidInfo = WifiSetupInfo(userNumber = 1, token = "t")

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.FIRST, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
        assertThat(errorMsg).contains("FIRST")
    }

    @Test
    fun `connect JOIN with missing token calls onError`() {
        val invalidInfo = WifiSetupInfo(userNumber = 1)

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.JOIN, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
        assertThat(errorMsg).contains("JOIN")
    }

    @Test
    fun `connect JOIN with missing userNumber calls onError`() {
        val invalidInfo = WifiSetupInfo(token = "t")

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.JOIN, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    @Test
    fun `connect CHANGE with missing ssid calls onError`() {
        val invalidInfo = WifiSetupInfo(password = "p")

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.CHANGE, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
        assertThat(errorMsg).contains("CHANGE")
    }

    @Test
    fun `connect ESP_TOUCH_WIFI with missing bssid calls onError`() {
        val invalidInfo = WifiSetupInfo(ssid = "s", userNumber = 1, token = "t")

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
        assertThat(errorMsg).contains("ESP_TOUCH_WIFI")
    }

    @Test
    fun `connect ESP_TOUCH_WIFI with missing token calls onError`() {
        val invalidInfo = WifiSetupInfo(ssid = "s", bssid = "b", userNumber = 1)

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    // -------------------------------------------------------------------------
    // connect — exception path
    // -------------------------------------------------------------------------

    @Test
    fun `connect catches exception and calls onError`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } throws RuntimeException("Connection crashed")

        var errorMsg: String? = null
        service.connect(validEsptouchInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
        assertThat(errorMsg).contains("Connection crashed")
    }

    // -------------------------------------------------------------------------
    // connect — null result (else branch)
    // -------------------------------------------------------------------------

    @Test
    fun `connect with null result does not call onSuccess or onError`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns null

        var successCalled = false
        var errorCalled = false
        service.connect(validEsptouchInfo, WifiSetupType.ESP_TOUCH_WIFI, { successCalled = true }, { errorCalled = true })
        Thread.sleep(300)

        assertThat(successCalled).isFalse()
        assertThat(errorCalled).isFalse()
    }

    // -------------------------------------------------------------------------
    // getScaleToken — success / failure
    // -------------------------------------------------------------------------

    @Test
    fun `getScaleToken returns token on success`() = runTest {
        coEvery { deviceService.getScaleToken(false) } returns "scale_token_123"

        val result = service.getScaleToken()

        assertThat(result).isEqualTo("scale_token_123")
    }

    @Test
    fun `getScaleToken returns null on exception`() = runTest {
        coEvery { deviceService.getScaleToken(false) } throws RuntimeException("API error")

        val result = service.getScaleToken()

        assertThat(result).isNull()
    }

    @Test
    fun `getScaleToken passes correct parameter to deviceService`() = runTest {
        coEvery { deviceService.getScaleToken(false) } returns "tok"

        service.getScaleToken()

        coVerify { deviceService.getScaleToken(false) }
    }

    // -------------------------------------------------------------------------
    // stop — delegate to manager
    // -------------------------------------------------------------------------

    @Test
    fun `stop calls wifiSmartConnectManager stopAll`() {
        service.stop()

        verify(exactly = 1) { wifiSmartConnectManager.stopAll() }
    }

    // -------------------------------------------------------------------------
    // getConnectedSsid — success / exception
    // -------------------------------------------------------------------------

    @Test
    fun `getConnectedSsid returns ssid from manager and passes current activity`() {
        every { wifiSmartConnectManager.getConnectedSsid(activity) } returns "HomeWiFi"

        val result = service.getConnectedSsid()

        assertThat(result).isEqualTo("HomeWiFi")
        verify { wifiSmartConnectManager.getConnectedSsid(activity) }
    }

    @Test
    fun `getConnectedSsid returns empty string on exception`() {
        every { wifiSmartConnectManager.getConnectedSsid(any()) } throws RuntimeException("fail")

        val result = service.getConnectedSsid()

        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getConnectedBssid — success / exception
    // -------------------------------------------------------------------------

    @Test
    fun `getConnectedBssid returns bssid from manager and passes current activity`() {
        every { wifiSmartConnectManager.getConnectedBssid(activity) } returns testBssid

        val result = service.getConnectedBssid()

        assertThat(result).isEqualTo(testBssid)
        verify { wifiSmartConnectManager.getConnectedBssid(activity) }
    }

    @Test
    fun `getConnectedBssid returns empty string on exception`() {
        every { wifiSmartConnectManager.getConnectedBssid(any()) } throws RuntimeException("fail")

        val result = service.getConnectedBssid()

        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // getConnectedWifiInfo — various status paths
    // -------------------------------------------------------------------------

    @Test
    fun `getConnectedWifiInfo returns DISABLED when wifi not enabled`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns false

        val result = service.getConnectedWifiInfo(hasLocationPermission = true)

        assertThat(result.status).isEqualTo(WifiConnectionStatus.DISABLED)
        assertThat(result.ssid).isEmpty()
        assertThat(result.bssid).isEmpty()
    }

    @Test
    fun `getConnectedWifiInfo returns ENABLED with empty ssid when no location permission`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns true

        val result = service.getConnectedWifiInfo(hasLocationPermission = false)

        assertThat(result.status).isEqualTo(WifiConnectionStatus.ENABLED)
        assertThat(result.ssid).isEmpty()
        assertThat(result.bssid).isEmpty()
    }

    @Test
    fun `getConnectedWifiInfo returns ENABLED when ssid is unknown`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns true
        every { wifiSmartConnectManager.getConnectedSsid(any()) } returns "<unknown ssid>"
        every { wifiSmartConnectManager.getConnectedBssid(any()) } returns testBssid

        val result = service.getConnectedWifiInfo(hasLocationPermission = true)

        assertThat(result.status).isEqualTo(WifiConnectionStatus.ENABLED)
        assertThat(result.ssid).isEmpty()
    }

    @Test
    fun `getConnectedWifiInfo returns ENABLED when ssid is empty`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns true
        every { wifiSmartConnectManager.getConnectedSsid(any()) } returns ""
        every { wifiSmartConnectManager.getConnectedBssid(any()) } returns ""

        val result = service.getConnectedWifiInfo(hasLocationPermission = true)

        assertThat(result.status).isEqualTo(WifiConnectionStatus.ENABLED)
        assertThat(result.ssid).isEmpty()
    }

    @Test
    fun `getConnectedWifiInfo returns CONNECTED when ssid is non-empty`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns true
        every { wifiSmartConnectManager.getConnectedSsid(any()) } returns "MyNetwork"
        every { wifiSmartConnectManager.getConnectedBssid(any()) } returns testBssid

        val result = service.getConnectedWifiInfo(hasLocationPermission = true)

        assertThat(result.status).isEqualTo(WifiConnectionStatus.CONNECTED)
        assertThat(result.ssid).isEqualTo("MyNetwork")
        assertThat(result.bssid).isEqualTo(testBssid)
    }

    @Test
    fun `getConnectedWifiInfo returns UNKNOWN on exception`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } throws RuntimeException("crash")

        val result = service.getConnectedWifiInfo(hasLocationPermission = true)

        assertThat(result.status).isEqualTo(WifiConnectionStatus.UNKNOWN)
        assertThat(result.ssid).isEmpty()
        assertThat(result.bssid).isEmpty()
    }

    @Test
    fun `getConnectedWifiInfo default hasLocationPermission is false`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns true

        val result = service.getConnectedWifiInfo()

        assertThat(result.status).isEqualTo(WifiConnectionStatus.ENABLED)
        assertThat(result.ssid).isEmpty()
    }

    @Test
    fun `getConnectedWifiInfo locationStatus defaults to GGPermissionState NOT_DETERMINED`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns true

        val result = service.getConnectedWifiInfo(hasLocationPermission = false)

        assertThat(result.locationStatus).isEqualTo(GGPermissionState.NOT_DETERMINED)
    }

    // -------------------------------------------------------------------------
    // openWifiSettings — success / exception
    // -------------------------------------------------------------------------

    @Test
    fun `openWifiSettings starts activity with correct intent`() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().addFlags(any()) } returns mockk(relaxed = true)

        service.openWifiSettings()

        verify { context.startActivity(any<Intent>()) }
    }

    @Test
    fun `openWifiSettings does not crash on exception`() {
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().addFlags(any()) } returns mockk(relaxed = true)
        every { context.startActivity(any<Intent>()) } throws RuntimeException("No activity")

        // Should not throw
        service.openWifiSettings()
    }

    @Test
    fun `openWifiSettings handles Intent creation failure gracefully`() {
        // Without mockkConstructor, Intent constructor may fail in unit tests
        // The try/catch in openWifiSettings handles this
        service.openWifiSettings()
    }

    // -------------------------------------------------------------------------
    // getScanResults — permission check + results
    // -------------------------------------------------------------------------

    @Test
    fun `getScanResults returns empty list when location permission not granted`() = runTest {
        every {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        val result = service.getScanResults()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getScanResults returns scan results when permission granted`() = runTest {
        every {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        val scanResult1: ScanResult = mockk()
        val scanResult2: ScanResult = mockk()
        every { wifiManager.scanResults } returns listOf(scanResult1, scanResult2)

        val result = service.getScanResults()

        assertThat(result).hasSize(2)
    }

    @Test
    fun `getScanResults returns empty list when wifiManager returns null`() = runTest {
        every {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { wifiManager.scanResults } returns null

        val result = service.getScanResults()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getScanResults returns empty list on exception`() = runTest {
        every {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_GRANTED
        every { wifiManager.scanResults } throws RuntimeException("fail")

        val result = service.getScanResults()

        assertThat(result).isEmpty()
    }

    // -------------------------------------------------------------------------
    // connect — null field defaults in request params
    // -------------------------------------------------------------------------

    @Test
    fun `connect ESP_TOUCH_WIFI uses empty string defaults for null optional fields`() {
        val info = WifiSetupInfo(ssid = "s", bssid = "b", userNumber = 1, token = "t")
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.Esptouch(EsptouchResult.Success("d", "m"))

        service.connect(info, WifiSetupType.ESP_TOUCH_WIFI, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.Esptouch
        assertThat(captured.params.password).isEqualTo("")
    }

    @Test
    fun `connect FIRST uses empty string defaults for null password`() {
        val info = WifiSetupInfo(ssid = "s", userNumber = 1, token = "t")
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Success)

        service.connect(info, WifiSetupType.FIRST, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.SmartConfig
        assertThat(captured.params.password).isEqualTo("")
    }

    @Test
    fun `connect CHANGE uses empty string for null password`() {
        val info = WifiSetupInfo(ssid = "s", userNumber = 2, token = "t")
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.ApMode(ApConnectResult.Success(byteArrayOf()))

        service.connect(info, WifiSetupType.CHANGE, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.ApMode
        assertThat(captured.params.password).isEqualTo("")
    }

    // -------------------------------------------------------------------------
    // connect — ApMode failure path
    // -------------------------------------------------------------------------

    @Test
    fun `connect ApMode always calls onSuccess because result handling is delegated to caller via buffer`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.ApMode(ApConnectResult.Failure("ap fail"))

        var successCalled = false
        service.connect(validChangeInfo, WifiSetupType.CHANGE, { successCalled = true }, {})
        Thread.sleep(300)

        // ApMode branch calls onSuccess unconditionally — caller inspects the buffer/result
        assertThat(successCalled).isTrue()
    }

    // -------------------------------------------------------------------------
    // Validation — additional edge cases per setup type
    // -------------------------------------------------------------------------

    @Test
    fun `connect FIRST with missing token fails validation`() {
        val invalidInfo = WifiSetupInfo(ssid = "s", userNumber = 1)

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.FIRST, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
        assertThat(errorMsg).contains("FIRST")
    }

    @Test
    fun `connect ESP_TOUCH_WIFI with missing ssid fails validation`() {
        val invalidInfo = WifiSetupInfo(bssid = "b", userNumber = 1, token = "t")

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    @Test
    fun `connect ESP_TOUCH_WIFI with all null fields fails validation`() {
        val invalidInfo = WifiSetupInfo()

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    @Test
    fun `connect FIRST with all null fields fails validation`() {
        val invalidInfo = WifiSetupInfo()

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.FIRST, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    @Test
    fun `connect JOIN with all null fields fails validation`() {
        val invalidInfo = WifiSetupInfo()

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.JOIN, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    @Test
    fun `connect CHANGE with all null fields fails validation`() {
        val invalidInfo = WifiSetupInfo()

        var errorMsg: String? = null
        service.connect(invalidInfo, WifiSetupType.CHANGE, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("Connect failed")
    }

    // -------------------------------------------------------------------------
    // Validation — valid data passes for all types
    // -------------------------------------------------------------------------

    @Test
    fun `connect JOIN with valid data passes validation and connects`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Success)

        var successCalled = false
        service.connect(validJoinInfo, WifiSetupType.JOIN, { successCalled = true }, {})
        Thread.sleep(300)

        assertThat(successCalled).isTrue()
    }

    @Test
    fun `connect CHANGE with valid data passes validation and connects`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.ApMode(ApConnectResult.Success(byteArrayOf()))

        var successCalled = false
        service.connect(validChangeInfo, WifiSetupType.CHANGE, { successCalled = true }, {})
        Thread.sleep(300)

        assertThat(successCalled).isTrue()
    }

    // -------------------------------------------------------------------------
    // connect — JOIN request building with ssid provided
    // -------------------------------------------------------------------------

    @Test
    fun `connect JOIN with ssid provided includes ssid in request`() {
        val joinWithSsid = WifiSetupInfo(ssid = "JoinNet", userNumber = 2, token = "tok2")
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Success)

        service.connect(joinWithSsid, WifiSetupType.JOIN, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.SmartConfig
        assertThat(captured.params.ssid).isEqualTo("JoinNet")
    }

    // -------------------------------------------------------------------------
    // connect — SmartConfig failure via JOIN type
    // -------------------------------------------------------------------------

    @Test
    fun `connect JOIN SmartConfig failure invokes onError`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), any())
        } returns WifiConnectResult.SmartConfig(SmartConfigResult.Failure("join failed"))

        var errorMsg: String? = null
        service.connect(validJoinInfo, WifiSetupType.JOIN, {}, { errorMsg = it })
        Thread.sleep(300)

        assertThat(errorMsg).contains("SmartConfig connection failed")
        assertThat(errorMsg).contains("join failed")
    }

    // -------------------------------------------------------------------------
    // getScaleToken — default parameter
    // -------------------------------------------------------------------------

    @Test
    fun `getScaleToken with r parameter still calls deviceService with false`() = runTest {
        coEvery { deviceService.getScaleToken(false) } returns "token_r"

        val result = service.getScaleToken("someParam")

        assertThat(result).isEqualTo("token_r")
        coVerify { deviceService.getScaleToken(false) }
    }

    @Test
    fun `getScaleToken returns null when deviceService returns null`() = runTest {
        coEvery { deviceService.getScaleToken(false) } returns null

        val result = service.getScaleToken()

        assertThat(result).isNull()
    }

    @Test
    fun `getScaleToken with r parameter returns null on exception`() = runTest {
        coEvery { deviceService.getScaleToken(false) } throws RuntimeException("API error")

        val result = service.getScaleToken("someParam")

        assertThat(result).isNull()
    }

    // -------------------------------------------------------------------------
    // getConnectedWifiInfo — exception after partial state
    // -------------------------------------------------------------------------

    @Test
    fun `getConnectedWifiInfo returns ENABLED on getBssid exception after getSsid succeeds`() = runTest {
        every { wifiSmartConnectManager.isWifiEnabled(any()) } returns true
        every { wifiSmartConnectManager.getConnectedSsid(any()) } returns "ValidSSID"
        every { wifiSmartConnectManager.getConnectedBssid(any()) } throws RuntimeException("bssid fail")

        val result = service.getConnectedWifiInfo(hasLocationPermission = true)

        // Exception caught — returns last-known state (ENABLED was set before exception)
        assertThat(result.status).isEqualTo(WifiConnectionStatus.ENABLED)
    }

    // -------------------------------------------------------------------------
    // getScanResults — does not access WifiManager when permission denied
    // -------------------------------------------------------------------------

    @Test
    fun `getScanResults does not access WifiManager when permission denied`() = runTest {
        every {
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } returns PackageManager.PERMISSION_DENIED

        service.getScanResults()

        verify(exactly = 0) { wifiManager.scanResults }
    }

    // -------------------------------------------------------------------------
    // connect — passes currentActivity to manager
    // -------------------------------------------------------------------------

    @Test
    fun `connect passes current activity to wifiSmartConnectManager`() {
        coEvery {
            wifiSmartConnectManager.connect(any(), activity)
        } returns WifiConnectResult.Esptouch(EsptouchResult.Success("d", "m"))

        service.connect(validEsptouchInfo, WifiSetupType.ESP_TOUCH_WIFI, {}, {})
        Thread.sleep(300)

        coVerify { wifiSmartConnectManager.connect(any(), activity) }
    }

    // -------------------------------------------------------------------------
    // connect — CHANGE uses empty token default when null
    // -------------------------------------------------------------------------

    @Test
    fun `connect CHANGE uses empty string for null token`() {
        val info = WifiSetupInfo(ssid = "s", userNumber = 1)
        val requestSlot = slot<WifiConnectRequest>()
        coEvery {
            wifiSmartConnectManager.connect(capture(requestSlot), any())
        } returns WifiConnectResult.ApMode(ApConnectResult.Success(byteArrayOf()))

        service.connect(info, WifiSetupType.CHANGE, {}, {})
        Thread.sleep(300)

        val captured = requestSlot.captured as WifiConnectRequest.ApMode
        assertThat(captured.params.tokenHexString).isEqualTo("")
    }

    // -------------------------------------------------------------------------
    // initialise — updates activity for subsequent calls
    // -------------------------------------------------------------------------

    @Test
    fun `initialise with new activity updates activity used by getConnectedSsid`() {
        val newActivity: ComponentActivity = mockk(relaxed = true)
        every { wifiSmartConnectManager.getConnectedSsid(newActivity) } returns "NewActivityNet"

        service.initialise(newActivity)
        val result = service.getConnectedSsid()

        assertThat(result).isEqualTo("NewActivityNet")
    }

    // -------------------------------------------------------------------------
    // stop — can be called multiple times
    // -------------------------------------------------------------------------

    @Test
    fun `stop can be called multiple times without error`() {
        service.stop()
        service.stop()

        verify(exactly = 2) { wifiSmartConnectManager.stopAll() }
    }
}
