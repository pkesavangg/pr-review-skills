package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.core.shared.utilities.FcmTokenUtil
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.PartialAccount
import com.dmdbrands.gurus.weight.domain.model.common.DeviceInfo
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.repository.IAppRepository
import com.dmdbrands.gurus.weight.domain.repository.IDeviceInfoRepository
import com.dmdbrands.gurus.weight.domain.repository.IHealthConnectRepository
import com.dmdbrands.gurus.weight.domain.repository.IIntegrationRepository
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IOfflineHandlerService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceInfoServiceTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  // --- Mocks ---
  private val context: Context = mockk()
  private val deviceInfoRepository: IDeviceInfoRepository = mockk()
  private val connectivityObserver: IConnectivityObserver = mockk()
  private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
  private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
  private val offlineHandlerService: IOfflineHandlerService = mockk(relaxed = true)
  private val appRepository: IAppRepository = mockk()
  private val accountRepository: IAccountRepository = mockk()
  private val healthConnectRepository: IHealthConnectRepository = mockk(relaxed = true)
  private val integrationRepository: IIntegrationRepository = mockk(relaxed = true)
  private val entryService: IEntryService = mockk(relaxed = true)

  // Controlled flow for network state changes
  private val networkFlow = MutableSharedFlow<NetworkState>()

  // --- Test fixtures ---
  private val onlineState = NetworkState(available = true, unAvailable = false)
  private val offlineState = NetworkState(available = false, unAvailable = true)

  // Lifecycle mocks for isAppInForeground
  private val mockLifecycleOwner: LifecycleOwner = mockk()
  private val mockLifecycle: Lifecycle = mockk()

  @Before
  fun setUp() {
    // Static mocks
    mockkObject(DeviceInfoUtil)
    mockkObject(FcmTokenUtil)
    mockkObject(AppLog)
    // DeviceInfoUtil stubs
    every { DeviceInfoUtil.getAppVersion() } returns "1.0.0"
    every { DeviceInfoUtil.getManufacturer() } returns "Google"
    every { DeviceInfoUtil.getOSName() } returns "Android"
    every { DeviceInfoUtil.getOSVersion() } returns "14"
    every { DeviceInfoUtil.getDeviceUUID(any()) } returns "test-uuid-123"
    every { DeviceInfoUtil.getModel() } returns "Pixel 8"

    // AppLog stubs (prevent Timber calls)
    every { AppLog.d(any(), any(), any<String>()) } just Runs
    every { AppLog.i(any(), any(), any<String>()) } just Runs
    every { AppLog.w(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<Throwable>()) } just Runs

    // Default: online, with controlled flow
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    every { connectivityObserver.observe() } returns networkFlow
  }

  private fun setupForegroundMock(state: Lifecycle.State = Lifecycle.State.RESUMED) {
    mockkObject(ProcessLifecycleOwner)
    every { ProcessLifecycleOwner.get() } returns mockLifecycleOwner
    every { mockLifecycleOwner.lifecycle } returns mockLifecycle
    every { mockLifecycle.currentState } returns state
  }

  private fun createService(): DeviceInfoService = DeviceInfoService(
    context = context,
    deviceInfoRepository = deviceInfoRepository,
    connectivityObserver = connectivityObserver,
    dialogQueueService = dialogQueueService,
    appNavigationService = appNavigationService,
    offlineHandlerService = offlineHandlerService,
    appRepository = appRepository,
    accountRepository = accountRepository,
    healthConnectRepository = healthConnectRepository,
    integrationRepository = integrationRepository,
    entryService = entryService,
  )

  @After
  fun tearDown() {
    unmockkAll()
  }

  // -------------------------------------------------------------------------
  // getDeviceInfo
  // -------------------------------------------------------------------------

  @Test
  fun `getDeviceInfo returns DeviceInfo with all device util values`() {
    val service = createService()

    val deviceInfo = service.getDeviceInfo()

    assertThat(deviceInfo.appVersion).isEqualTo("1.0.0")
    assertThat(deviceInfo.deviceManufacturer).isEqualTo("Google")
    assertThat(deviceInfo.deviceOSName).isEqualTo("Android")
    assertThat(deviceInfo.deviceOSVersion).isEqualTo("14")
    assertThat(deviceInfo.deviceUUID).isEqualTo("test-uuid-123")
    assertThat(deviceInfo.deviceModel).isEqualTo("Pixel 8")
  }

  @Test
  fun `getDeviceInfo returns empty fcmToken initially`() {
    val service = createService()

    val deviceInfo = service.getDeviceInfo()

    assertThat(deviceInfo.fcmToken).isEmpty()
  }

  @Test
  fun `getDeviceInfo returns updated fcmToken after updateDeviceInfo`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "test-fcm-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()
    val deviceInfo = service.getDeviceInfo()

    assertThat(deviceInfo.fcmToken).isEqualTo("test-fcm-token")
  }

  @Test
  fun `getDeviceInfo uses context for getDeviceUUID`() {
    val service = createService()

    service.getDeviceInfo()

    verify { DeviceInfoUtil.getDeviceUUID(context) }
  }

  // -------------------------------------------------------------------------
  // getFcmToken
  // -------------------------------------------------------------------------

  @Test
  fun `getFcmToken returns token from appRepository`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "stored-token"
    val service = createService()

    val token = service.getFcmToken()

    assertThat(token).isEqualTo("stored-token")
  }

  @Test
  fun `getFcmToken returns empty string on exception`() = runTest {
    coEvery { appRepository.getFcmToken() } throws RuntimeException("DataStore error")
    val service = createService()

    val token = service.getFcmToken()

    assertThat(token).isEmpty()
  }

  @Test
  fun `getFcmToken returns empty string when repository returns empty`() = runTest {
    coEvery { appRepository.getFcmToken() } returns ""
    val service = createService()

    val token = service.getFcmToken()

    assertThat(token).isEmpty()
  }

  // -------------------------------------------------------------------------
  // updateDeviceInfo — happy path
  // -------------------------------------------------------------------------

  @Test
  fun `updateDeviceInfo fetches token and updates device info via repository`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "my-fcm-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    val slot = slot<DeviceInfo>()
    coVerify { deviceInfoRepository.updateDeviceInfo(capture(slot)) }
    assertThat(slot.captured.fcmToken).isEqualTo("my-fcm-token")
    assertThat(slot.captured.appVersion).isEqualTo("1.0.0")
  }

  @Test
  fun `updateDeviceInfo updates active account FCM token when account exists`() = runTest {
    val fakeAccount = mockk<Account> { every { id } returns "acc-123" }
    coEvery { appRepository.getFcmToken() } returns "my-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(fakeAccount)
    coEvery { accountRepository.updateAccount(any(), any()) } just Runs
    val service = createService()

    service.updateDeviceInfo()

    val partialSlot = slot<PartialAccount>()
    coVerify { accountRepository.updateAccount("acc-123", capture(partialSlot)) }
    assertThat(partialSlot.captured.fcmToken).isEqualTo("my-token")
  }

  @Test
  fun `updateDeviceInfo skips account update when no active account`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "my-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    coVerify(exactly = 0) { accountRepository.updateAccount(any(), any()) }
  }

  @Test
  fun `updateDeviceInfo constructs DeviceInfo with correct fields`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "token-abc"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    val slot = slot<DeviceInfo>()
    coVerify { deviceInfoRepository.updateDeviceInfo(capture(slot)) }
    with(slot.captured) {
      assertThat(appVersion).isEqualTo("1.0.0")
      assertThat(deviceManufacturer).isEqualTo("Google")
      assertThat(deviceOSName).isEqualTo("Android")
      assertThat(deviceOSVersion).isEqualTo("14")
      assertThat(deviceUUID).isEqualTo("test-uuid-123")
      assertThat(deviceModel).isEqualTo("Pixel 8")
      assertThat(fcmToken).isEqualTo("token-abc")
    }
  }

  // -------------------------------------------------------------------------
  // updateDeviceInfo — FCM token fallback
  // -------------------------------------------------------------------------

  @Test
  fun `updateDeviceInfo fetches from Firebase when DataStore token is blank`() = runTest {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { FcmTokenUtil.getCurrentToken() } returns "firebase-token"
    coEvery { appRepository.setFcmToken(any()) } just Runs
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    coVerify { FcmTokenUtil.getCurrentToken() }
    coVerify { appRepository.setFcmToken("firebase-token") }
  }

  @Test
  fun `updateDeviceInfo uses Firebase token in DeviceInfo after fallback`() = runTest {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { FcmTokenUtil.getCurrentToken() } returns "firebase-token"
    coEvery { appRepository.setFcmToken(any()) } just Runs
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    val slot = slot<DeviceInfo>()
    coVerify { deviceInfoRepository.updateDeviceInfo(capture(slot)) }
    assertThat(slot.captured.fcmToken).isEqualTo("firebase-token")
  }

  @Test
  fun `updateDeviceInfo handles Firebase fetch failure gracefully`() = runTest {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { FcmTokenUtil.getCurrentToken() } throws RuntimeException("Firebase error")
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    // Should still call updateDeviceInfo with empty token
    val slot = slot<DeviceInfo>()
    coVerify { deviceInfoRepository.updateDeviceInfo(capture(slot)) }
    assertThat(slot.captured.fcmToken).isEmpty()
  }

  @Test
  fun `updateDeviceInfo skips Firebase fetch when DataStore token is non-blank`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "existing-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    coVerify(exactly = 0) { FcmTokenUtil.getCurrentToken() }
  }

  @Test
  fun `updateDeviceInfo skips setFcmToken when Firebase returns blank token`() = runTest {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { FcmTokenUtil.getCurrentToken() } returns ""
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    coVerify(exactly = 0) { appRepository.setFcmToken(any()) }
  }

  // -------------------------------------------------------------------------
  // updateDeviceInfo — error handling
  // -------------------------------------------------------------------------

  @Test
  fun `updateDeviceInfo catches exception from deviceInfoRepository`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } throws RuntimeException("API error")
    val service = createService()

    // Should not throw
    service.updateDeviceInfo()
  }

  @Test
  fun `updateDeviceInfo catches exception from accountRepository`() = runTest {
    coEvery { appRepository.getFcmToken() } returns "token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } throws RuntimeException("DB error")
    val service = createService()

    // Should not throw
    service.updateDeviceInfo()
  }

  // -------------------------------------------------------------------------
  // updateLocalIntegrationInfo
  // -------------------------------------------------------------------------

  @Test
  fun `updateLocalIntegrationInfo calls integrationRepository updateLocalAccount`() = runTest {
    val service = createService()

    service.updateLocalIntegrationInfo()

    coVerify { integrationRepository.updateLocalAccount() }
  }

  // -------------------------------------------------------------------------
  // startNetworkMonitoring — initial state
  // -------------------------------------------------------------------------

  @Test
  fun `init shows network error when app starts offline`() {
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState

    createService()

    verify { dialogQueueService.showToast(any<Toast>()) }
  }

  @Test
  fun `init does not show network error when app starts online`() {
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState

    createService()

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  // -------------------------------------------------------------------------
  // startNetworkMonitoring — network becomes available triggers sync
  // -------------------------------------------------------------------------

  @Test
  fun `network available triggers all four sync steps`() = runTest {
    val service = createService()
    Thread.sleep(100) // Let collector start on IO

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `network available does not show network error toast`() = runTest {
    val service = createService()
    Thread.sleep(100)

    networkFlow.emit(onlineState)
    Thread.sleep(500)

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  // -------------------------------------------------------------------------
  // startNetworkMonitoring — debounce behavior
  // -------------------------------------------------------------------------

  @Test
  fun `network unavailable shows error after debounce when still offline and foreground`() = runTest {
    setupForegroundMock(Lifecycle.State.RESUMED)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    val service = createService()
    Thread.sleep(100)

    // After construction, switch re-check to return offline
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    networkFlow.emit(offlineState)

    // Wait for debounce (2000ms) + processing
    Thread.sleep(2500)

    verify(atLeast = 1) { dialogQueueService.showToast(any<Toast>()) }
  }

  @Test
  fun `network unavailable does not show error when network recovers during debounce`() = runTest {
    setupForegroundMock(Lifecycle.State.RESUMED)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    val service = createService()
    Thread.sleep(100)

    // Emit unavailable
    networkFlow.emit(offlineState)

    // Before debounce ends, network recovers
    Thread.sleep(500)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState

    // Wait for debounce to complete
    Thread.sleep(2500)

    // No toast should be shown — getCurrentNetworkState returned available after debounce
    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  @Test
  fun `network unavailable does not show error when app is in background`() = runTest {
    setupForegroundMock(Lifecycle.State.CREATED)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    val service = createService()
    Thread.sleep(100)

    // Still unavailable after debounce but in background
    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    networkFlow.emit(offlineState)

    Thread.sleep(2500)

    // No toast — app is in background
    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  // -------------------------------------------------------------------------
  // runOnlineSyncOnce — error isolation
  // -------------------------------------------------------------------------

  @Test
  fun `online sync continues when offlineSync fails`() = runTest {
    coEvery { offlineHandlerService.handleOfflineSync() } throws RuntimeException("offline sync error")
    val service = createService()
    Thread.sleep(100)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `online sync continues when entrySync fails`() = runTest {
    coEvery { entryService.syncOperations() } throws RuntimeException("entry sync error")
    val service = createService()
    Thread.sleep(100)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `online sync continues when healthConnect sync fails`() = runTest {
    coEvery { healthConnectRepository.syncIntegration() } throws RuntimeException("HC sync error")
    val service = createService()
    Thread.sleep(100)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `online sync continues when integration update fails`() = runTest {
    coEvery { integrationRepository.updateLocalAccount() } throws RuntimeException("integration error")
    val service = createService()
    Thread.sleep(100)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
  }

  // -------------------------------------------------------------------------
  // runOnlineSyncOnce — guard behavior
  // -------------------------------------------------------------------------

  @Test
  fun `sync guard prevents concurrent execution`() = runTest {
    // Make offlineSync slow so the guard is held during second emission
    coEvery { offlineHandlerService.handleOfflineSync() } coAnswers {
      Thread.sleep(1000)
    }
    val service = createService()
    Thread.sleep(100)

    // Emit available twice rapidly — second should be skipped
    networkFlow.emit(onlineState)
    Thread.sleep(50)
    networkFlow.emit(onlineState)
    Thread.sleep(2000)

    // offlineHandlerService should only be called once due to guard
    coVerify(exactly = 1) { offlineHandlerService.handleOfflineSync() }
  }

  @Test
  fun `sync guard is released after completion allowing subsequent sync`() = runTest {
    val service = createService()
    Thread.sleep(100)

    // First sync
    networkFlow.emit(onlineState)
    Thread.sleep(500)

    // Wait for first sync to complete and guard to release
    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }

    // Emit unavailable then available again to trigger a new distinct emission
    networkFlow.emit(offlineState)
    Thread.sleep(100)
    networkFlow.emit(onlineState)
    Thread.sleep(500)

    // Should have been called twice total — guard was released
    coVerify(timeout = 3000, atLeast = 2) { offlineHandlerService.handleOfflineSync() }
  }
}
