package com.dmdbrands.gurus.weight.core.service

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
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
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceInfoServiceTest {

  @JvmField
  @RegisterExtension
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

  /** Standard wait for IO collector to start after service construction. */
  private val collectorStartDelayMs = 100L

  @BeforeEach
  fun setUp() {
    mockkObject(DeviceInfoUtil)
    mockkObject(FcmTokenUtil)
    mockkObject(AppLog)

    every { DeviceInfoUtil.getAppVersion() } returns "1.0.0"
    every { DeviceInfoUtil.getManufacturer() } returns "Google"
    every { DeviceInfoUtil.getOSName() } returns "Android"
    every { DeviceInfoUtil.getOSVersion() } returns "14"
    every { DeviceInfoUtil.getDeviceUUID(any()) } returns "test-uuid-123"
    every { DeviceInfoUtil.getModel() } returns "Pixel 8"

    every { AppLog.d(any(), any(), any<String>()) } just Runs
    every { AppLog.i(any(), any(), any<String>()) } just Runs
    every { AppLog.w(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<String>()) } just Runs
    every { AppLog.e(any(), any(), any<Throwable>()) } just Runs

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
    appScope = TestScope(mainDispatcherRule.dispatcher),
  )

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  // -------------------------------------------------------------------------
  // getDeviceInfo — returns DeviceInfo populated from DeviceInfoUtil
  // -------------------------------------------------------------------------

  @Test
  fun `getDeviceInfo returns DeviceInfo with all correct fields`() {
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
    assertThat(service.getDeviceInfo().fcmToken).isEmpty()
  }

  @Test
  fun `getDeviceInfo uses context for getDeviceUUID`() {
    val service = createService()
    service.getDeviceInfo()
    verify { DeviceInfoUtil.getDeviceUUID(context) }
  }

  @Test
  fun `getDeviceInfo returns updated fcmToken after updateDeviceInfo`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "test-fcm-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    assertThat(service.getDeviceInfo().fcmToken).isEqualTo("test-fcm-token")
  }

  // -------------------------------------------------------------------------
  // getFcmToken — reads from AppRepository DataStore
  // -------------------------------------------------------------------------

  @Test
  fun `getFcmToken returns token from appRepository`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "stored-token"
    val service = createService()

    assertThat(service.getFcmToken()).isEqualTo("stored-token")
  }

  @Test
  fun `getFcmToken returns empty string on exception`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } throws RuntimeException("DataStore error")
    val service = createService()

    assertThat(service.getFcmToken()).isEmpty()
  }

  @Test
  fun `getFcmToken returns empty string when repository returns empty`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns ""
    val service = createService()

    assertThat(service.getFcmToken()).isEmpty()
  }

  // -------------------------------------------------------------------------
  // updateDeviceInfo — happy path
  // -------------------------------------------------------------------------

  @Test
  fun `updateDeviceInfo constructs DeviceInfo with all correct fields and token`() = runTest(mainDispatcherRule.scheduler) {
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

  @Test
  fun `updateDeviceInfo updates active account FCM token when account exists`() = runTest(mainDispatcherRule.scheduler) {
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
  fun `updateDeviceInfo skips account update when no active account`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "my-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    coVerify(exactly = 0) { accountRepository.updateAccount(any(), any()) }
  }

  // -------------------------------------------------------------------------
  // updateDeviceInfo — FCM token fallback from Firebase
  // -------------------------------------------------------------------------

  @Test
  fun `updateDeviceInfo fetches from Firebase and persists when DataStore token is blank`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { FcmTokenUtil.getCurrentToken() } returns "firebase-token"
    coEvery { appRepository.setFcmToken(any()) } just Runs
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    coVerify { FcmTokenUtil.getCurrentToken() }
    coVerify { appRepository.setFcmToken("firebase-token") }
    val slot = slot<DeviceInfo>()
    coVerify { deviceInfoRepository.updateDeviceInfo(capture(slot)) }
    assertThat(slot.captured.fcmToken).isEqualTo("firebase-token")
  }

  @Test
  fun `updateDeviceInfo handles Firebase fetch failure gracefully`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns ""
    coEvery { FcmTokenUtil.getCurrentToken() } throws RuntimeException("Firebase error")
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    val slot = slot<DeviceInfo>()
    coVerify { deviceInfoRepository.updateDeviceInfo(capture(slot)) }
    assertThat(slot.captured.fcmToken).isEmpty()
  }

  @Test
  fun `updateDeviceInfo skips Firebase fetch when DataStore token is non-blank`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "existing-token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    val service = createService()

    service.updateDeviceInfo()

    coVerify(exactly = 0) { FcmTokenUtil.getCurrentToken() }
  }

  @Test
  fun `updateDeviceInfo skips setFcmToken when Firebase returns blank token`() = runTest(mainDispatcherRule.scheduler) {
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
  fun `updateDeviceInfo catches exception from deviceInfoRepository`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } throws RuntimeException("API error")
    val service = createService()

    service.updateDeviceInfo()
  }

  @Test
  fun `updateDeviceInfo catches exception from accountRepository`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { appRepository.getFcmToken() } returns "token"
    coEvery { deviceInfoRepository.updateDeviceInfo(any()) } returns Response.success(Unit)
    coEvery { accountRepository.getActiveAccount() } throws RuntimeException("DB error")
    val service = createService()

    service.updateDeviceInfo()
  }

  // -------------------------------------------------------------------------
  // updateLocalIntegrationInfo — delegates to integrationRepository
  // -------------------------------------------------------------------------

  @Test
  fun `updateLocalIntegrationInfo calls integrationRepository updateLocalAccount`() = runTest(mainDispatcherRule.scheduler) {
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
    createService()

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  // -------------------------------------------------------------------------
  // startNetworkMonitoring — network becomes available triggers sync
  // -------------------------------------------------------------------------

  @Test
  fun `network available triggers all four sync steps`() = runTest(mainDispatcherRule.scheduler) {
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `network available does not show network error toast`() = runTest(mainDispatcherRule.scheduler) {
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)
    Thread.sleep(500)

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  // -------------------------------------------------------------------------
  // startNetworkMonitoring — debounce behavior
  // -------------------------------------------------------------------------

  @Test
  fun `network unavailable shows error after debounce when still offline and foreground`() = runTest(mainDispatcherRule.scheduler) {
    setupForegroundMock(Lifecycle.State.RESUMED)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    createService()
    Thread.sleep(collectorStartDelayMs)

    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    networkFlow.emit(offlineState)

    // Advance virtual time past the NETWORK_UNAVAILABLE_DEBOUNCE_MS (2000ms)
    mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(2500)
    Thread.sleep(200) // Allow coroutine to complete after virtual time advancement

    verify(atLeast = 1) { dialogQueueService.showToast(any<Toast>()) }
  }

  @Test
  fun `network unavailable does not show error when network recovers during debounce`() = runTest(mainDispatcherRule.scheduler) {
    setupForegroundMock(Lifecycle.State.RESUMED)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(offlineState)
    Thread.sleep(500)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    Thread.sleep(2500)

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  @Test
  fun `network unavailable does not show error when app is in background`() = runTest(mainDispatcherRule.scheduler) {
    setupForegroundMock(Lifecycle.State.CREATED)
    every { connectivityObserver.getCurrentNetworkState() } returns onlineState
    createService()
    Thread.sleep(collectorStartDelayMs)

    every { connectivityObserver.getCurrentNetworkState() } returns offlineState
    networkFlow.emit(offlineState)
    Thread.sleep(2500)

    verify(exactly = 0) { dialogQueueService.showToast(any<Toast>()) }
  }

  // -------------------------------------------------------------------------
  // runOnlineSyncOnce — error isolation
  // -------------------------------------------------------------------------

  @Test
  fun `online sync continues when offlineSync fails`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { offlineHandlerService.handleOfflineSync() } throws RuntimeException("offline sync error")
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `online sync continues when entrySync fails`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { entryService.syncOperations() } throws RuntimeException("entry sync error")
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `online sync continues when healthConnect sync fails`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { healthConnectRepository.syncIntegration() } throws RuntimeException("HC sync error")
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `online sync continues when integration update fails`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { integrationRepository.updateLocalAccount() } throws RuntimeException("integration error")
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
  }

  // -------------------------------------------------------------------------
  // runOnlineSyncOnce — guard behavior (AtomicBoolean prevents re-entry)
  // -------------------------------------------------------------------------

  @Test
  fun `sync guard prevents concurrent execution`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { offlineHandlerService.handleOfflineSync() } coAnswers {
      Thread.sleep(1000)
    }
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)
    Thread.sleep(50)
    networkFlow.emit(onlineState)
    Thread.sleep(2000)

    coVerify(exactly = 1) { offlineHandlerService.handleOfflineSync() }
  }

  @Test
  fun `sync guard is released after completion allowing subsequent sync`() = runTest(mainDispatcherRule.scheduler) {
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)
    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }

    // Emit unavailable then available to trigger a new distinct emission
    networkFlow.emit(offlineState)
    Thread.sleep(collectorStartDelayMs)
    networkFlow.emit(onlineState)

    coVerify(timeout = 3000, atLeast = 2) { offlineHandlerService.handleOfflineSync() }
  }

  // -------------------------------------------------------------------------
  // isAppInForeground — requires ProcessLifecycleOwner (Android framework)
  // Cannot be unit tested directly because ProcessLifecycleOwner.get()
  // requires an actual Android process lifecycle. It is tested indirectly
  // via the startNetworkMonitoring debounce tests above that mock
  // ProcessLifecycleOwner and verify foreground/background gating.
  // -------------------------------------------------------------------------

  // -------------------------------------------------------------------------
  // startNetworkMonitoring — additional initial state tests
  // -------------------------------------------------------------------------

  @Test
  fun `startNetworkMonitoring called during init observes connectivity flow`() {
    createService()

    // observe() should have been called during init
    verify { connectivityObserver.observe() }
  }

  @Test
  fun `startNetworkMonitoring checks initial network state during init`() {
    createService()

    verify { connectivityObserver.getCurrentNetworkState() }
  }

  // -------------------------------------------------------------------------
  // runOnlineSyncOnce — all four sync steps called in order
  // -------------------------------------------------------------------------

  @Test
  fun `runOnlineSyncOnce completes successfully when all steps succeed`() = runTest(mainDispatcherRule.scheduler) {
    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)

    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }
    coVerify(timeout = 3000) { entryService.syncOperations() }
    coVerify(timeout = 3000) { healthConnectRepository.syncIntegration() }
    coVerify(timeout = 3000) { integrationRepository.updateLocalAccount() }
  }

  @Test
  fun `runOnlineSyncOnce releases guard even when all steps fail`() = runTest(mainDispatcherRule.scheduler) {
    coEvery { offlineHandlerService.handleOfflineSync() } throws RuntimeException("err1")
    coEvery { entryService.syncOperations() } throws RuntimeException("err2")
    coEvery { healthConnectRepository.syncIntegration() } throws RuntimeException("err3")
    coEvery { integrationRepository.updateLocalAccount() } throws RuntimeException("err4")

    createService()
    Thread.sleep(collectorStartDelayMs)

    networkFlow.emit(onlineState)
    coVerify(timeout = 3000) { offlineHandlerService.handleOfflineSync() }

    // Wait for first sync to complete (guard released in finally)
    Thread.sleep(1000)

    // Second emission should trigger another sync (guard released)
    networkFlow.emit(offlineState)
    Thread.sleep(collectorStartDelayMs)
    networkFlow.emit(onlineState)

    coVerify(timeout = 3000, atLeast = 2) { offlineHandlerService.handleOfflineSync() }
  }
}
