package com.dmdbrands.gurus.weight.features.debugMenu.viewmodel

import android.app.Activity
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.core.shared.utilities.IAppReviewManager
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.LogManager
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.domain.services.IExportService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.debugMenu.model.DebugMenuIntent
import com.dmdbrands.gurus.weight.features.debugMenu.strings.DebugMenuStrings
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DebugMenuViewModelTest {

    companion object {
        private const val TEST_BROADCAST_ID = "AA:BB:CC:DD:EE:FF"
        private const val TEST_SCALE_ID = "scale-1"
        /** SKU that maps to BtWifiR4 setup type — required for init block filter. */
        private const val BT_WIFI_R4_SKU = "0412"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var accountService: IAccountService

    @MockK(relaxed = true)
    lateinit var deviceService: IDeviceService

    @MockK(relaxed = true)
    lateinit var entryService: IEntryService

    @MockK(relaxed = true)
    lateinit var exportService: IExportService

    @MockK(relaxed = true)
    lateinit var logManager: LogManager

    @MockK(relaxed = true)
    lateinit var appReviewManager: IAppReviewManager

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var pairedScalesFlow: MutableStateFlow<List<Device>>
    private lateinit var viewModel: DebugMenuViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        pairedScalesFlow = MutableStateFlow(emptyList())
        every { deviceService.pairedScales } returns pairedScalesFlow
        coEvery { accountService.activeAccountFlow } returns flowOf(TestFixtures.activeAccount)

        viewModel = DebugMenuViewModel(
            accountService = accountService,
            deviceService = deviceService,
            entryService = entryService,
            exportService = exportService,
            logManager = logManager,
            appReviewManager = appReviewManager,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasScales).isFalse()
        assertThat(state.isSendScaleLogEnabled).isFalse()
        assertThat(state.scaleList).isEmpty()
        assertThat(state.scaleListScaleInfo).isEmpty()
        assertThat(state.scaleLogsPickerScales).isEmpty()
        assertThat(state.isNative).isTrue()
        assertThat(state.isAndroid).isTrue()
    }

    // -------------------------------------------------------------------------
    // OnBack
    // -------------------------------------------------------------------------

    @Test
    fun `OnBack navigates back`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.OnBack)
        advanceUntilIdle()
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `OnBack sets isLoading to false via reducer`() {
        viewModel.handleIntent(DebugMenuIntent.OnBack)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // SendLogs
    // -------------------------------------------------------------------------

    @Test
    fun `SendLogs shows loader then sends logs and shows toast on success`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.SendLogs)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = DebugMenuStrings.Loading.SendLogs) }
        coVerify { logManager.sendLogs() }
        val toastSlot = slot<Toast>()
        verify { dialogQueueService.showToast(capture(toastSlot)) }
        assertThat(toastSlot.captured.message).isEqualTo(DebugMenuStrings.Success.LogSent)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `SendLogs shows error alert when no active account`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.activeAccountFlow } returns flowOf(null)
        viewModel = DebugMenuViewModel(
            accountService = accountService,
            deviceService = deviceService,
            entryService = entryService,
            exportService = exportService,
            logManager = logManager,
            appReviewManager = appReviewManager,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

        viewModel.handleIntent(DebugMenuIntent.SendLogs)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        assertThat(alert.message).isEqualTo(DebugMenuStrings.Alerts.ErrorMessage)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `SendLogs shows error alert when logManager throws`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { logManager.sendLogs() } throws RuntimeException("log failure")

        viewModel.handleIntent(DebugMenuIntent.SendLogs)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `SendLogs resets isLoading to false after completion`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.SendLogs)
        advanceUntilIdle()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // ResyncEntries
    // -------------------------------------------------------------------------

    @Test
    fun `ResyncEntries shows loader then syncs and shows toast on success`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.ResyncEntries)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = DebugMenuStrings.Loading.Resync) }
        coVerify { accountService.clearSyncTimestampForResync() }
        coVerify { entryService.syncOperations() }
        val toastSlot = slot<Toast>()
        verify { dialogQueueService.showToast(capture(toastSlot)) }
        assertThat(toastSlot.captured.message).isEqualTo(DebugMenuStrings.Success.Synced)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `ResyncEntries shows error alert on exception`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.clearSyncTimestampForResync() } throws RuntimeException("sync fail")

        viewModel.handleIntent(DebugMenuIntent.ResyncEntries)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `ResyncEntries resets isLoading to false after completion`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.ResyncEntries)
        advanceUntilIdle()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // ClearAllData
    // -------------------------------------------------------------------------

    @Test
    fun `ClearAllData shows loader then resets account and shows restart alert`() = runTest(mainDispatcherRule.scheduler) {
        val onDismiss = mockk<() -> Unit>(relaxed = true)

        viewModel.handleIntent(DebugMenuIntent.ClearAllData(onDismiss))
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = DebugMenuStrings.Loading.PleaseWait) }
        coVerify { accountService.reset() }
        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.DataHeader)
        assertThat(alert.message).isEqualTo(DebugMenuStrings.Alerts.DataMessage)
        assertThat(alert.dismissText).isEqualTo(DebugMenuStrings.Ok)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `ClearAllData restart alert onDismiss emits LoggedOut and invokes callback`() = runTest(mainDispatcherRule.scheduler) {
        val onDismiss = mockk<() -> Unit>(relaxed = true)

        viewModel.handleIntent(DebugMenuIntent.ClearAllData(onDismiss))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        alert.onDismiss?.invoke()
        advanceUntilIdle()

        coVerify { navigationService.emitAuthEvent(AuthState.LoggedOut(true)) }
        verify { dialogQueueService.dismissCurrent() }
        verify { onDismiss.invoke() }
    }

    @Test
    fun `ClearAllData skips reset when no active account`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.activeAccountFlow } returns flowOf(null)
        viewModel = DebugMenuViewModel(
            accountService = accountService,
            deviceService = deviceService,
            entryService = entryService,
            exportService = exportService,
            logManager = logManager,
            appReviewManager = appReviewManager,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        val onDismiss = mockk<() -> Unit>(relaxed = true)

        viewModel.handleIntent(DebugMenuIntent.ClearAllData(onDismiss))
        advanceUntilIdle()

        coVerify(exactly = 0) { accountService.reset() }
        // Still shows restart alert
        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        assertThat(dialogSlot.captured).isInstanceOf(DialogModel.Alert::class.java)
    }

    @Test
    fun `ClearAllData shows error alert on exception`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { accountService.reset() } throws RuntimeException("reset fail")
        val onDismiss = mockk<() -> Unit>(relaxed = true)

        viewModel.handleIntent(DebugMenuIntent.ClearAllData(onDismiss))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        assertThat(alert.message).isEqualTo(DebugMenuStrings.Alerts.ErrorMessage)
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // SendScaleLogs — singular scale
    // -------------------------------------------------------------------------

    @Test
    fun `SendScaleLogs with singular connected scale sends log and shows toast`() = runTest(mainDispatcherRule.scheduler) {
        val device = Device(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.CONNECTED,
            sku = BT_WIFI_R4_SKU,
        )
        pairedScalesFlow.value = listOf(device)
        advanceUntilIdle()

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogs)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = DebugMenuStrings.Loading.SendScaleLogs) }
        coVerify { exportService.sendScaleLog(any()) }
        val toastSlot = slot<Toast>()
        verify { dialogQueueService.showToast(capture(toastSlot)) }
        assertThat(toastSlot.captured.message).isEqualTo(DebugMenuStrings.Success.LogSent)
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `SendScaleLogs with singular scale shows restart alert on non-network error`() = runTest(mainDispatcherRule.scheduler) {
        val device = Device(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.CONNECTED,
            sku = BT_WIFI_R4_SKU,
        )
        pairedScalesFlow.value = listOf(device)
        advanceUntilIdle()

        coEvery { exportService.sendScaleLog(any()) } throws RuntimeException("scale fail")

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogs)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        assertThat(alert.message).isEqualTo(DebugMenuStrings.Alerts.ErrorMessage)
    }

    @Test
    fun `SendScaleLogs with singular scale does not show restart alert on network error`() = runTest(mainDispatcherRule.scheduler) {
        val device = Device(
            id = TEST_SCALE_ID,
            connectionStatus = BLEStatus.CONNECTED,
            sku = BT_WIFI_R4_SKU,
        )
        pairedScalesFlow.value = listOf(device)
        advanceUntilIdle()

        coEvery { exportService.sendScaleLog(any()) } throws IOException("no network")

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogs)
        advanceUntilIdle()

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // SendScaleLogs — multiple scales
    // -------------------------------------------------------------------------

    @Test
    fun `SendScaleLogs with multiple scales navigates to ScaleLogsPicker`() = runTest(mainDispatcherRule.scheduler) {
        val device1 = Device(
            id = "scale-1",
            sku = BT_WIFI_R4_SKU,
        )
        val device2 = Device(
            id = "scale-2",
            sku = BT_WIFI_R4_SKU,
        )
        pairedScalesFlow.value = listOf(device1, device2)
        advanceUntilIdle()

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogs)
        advanceUntilIdle()

        coVerify { navigationService.navigateTo(AppRoute.AccountSettings.ScaleLogsPicker) }
    }

    // -------------------------------------------------------------------------
    // SendScaleLogs — no scales
    // -------------------------------------------------------------------------

    @Test
    fun `SendScaleLogs with no scales shows error alert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.SendScaleLogs)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        assertThat(alert.message).isEqualTo(DebugMenuStrings.Alerts.ErrorMessage)
    }

    // -------------------------------------------------------------------------
    // SendScaleLogForScale
    // -------------------------------------------------------------------------

    @Test
    fun `SendScaleLogForScale with connected device sends log and navigates back`() = runTest(mainDispatcherRule.scheduler) {
        val device = mockk<Device>(relaxed = true)
        every { device.connectionStatus } returns BLEStatus.CONNECTED
        every { device.getBroadcastIdString() } returns TEST_BROADCAST_ID

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogForScale(device))
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = DebugMenuStrings.Loading.SendScaleLogs) }
        coVerify { exportService.sendScaleLog(TEST_BROADCAST_ID) }
        val toastSlot = slot<Toast>()
        verify { dialogQueueService.showToast(capture(toastSlot)) }
        assertThat(toastSlot.captured.message).isEqualTo(DebugMenuStrings.Success.LogSent)
        coVerify { navigationService.navigateBack() }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `SendScaleLogForScale with disconnected device skips send and clears state`() = runTest(mainDispatcherRule.scheduler) {
        val device = mockk<Device>(relaxed = true)
        every { device.connectionStatus } returns BLEStatus.DISCONNECTED

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogForScale(device))
        advanceUntilIdle()

        coVerify(exactly = 0) { exportService.sendScaleLog(any()) }
        assertThat(viewModel.state.value.scaleLogsPickerScales).isEmpty()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `SendScaleLogForScale shows restart alert on non-network error`() = runTest(mainDispatcherRule.scheduler) {
        val device = mockk<Device>(relaxed = true)
        every { device.connectionStatus } returns BLEStatus.CONNECTED
        every { device.getBroadcastIdString() } returns TEST_BROADCAST_ID
        coEvery { exportService.sendScaleLog(any()) } throws RuntimeException("export fail")

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogForScale(device))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        assertThat(alert.message).isEqualTo(DebugMenuStrings.Alerts.ErrorMessage)
        assertThat(viewModel.state.value.scaleLogsPickerScales).isEmpty()
    }

    @Test
    fun `SendScaleLogForScale does not show restart alert on network error`() = runTest(mainDispatcherRule.scheduler) {
        val device = mockk<Device>(relaxed = true)
        every { device.connectionStatus } returns BLEStatus.CONNECTED
        every { device.getBroadcastIdString() } returns TEST_BROADCAST_ID
        coEvery { exportService.sendScaleLog(any()) } throws IOException("no network")

        viewModel.handleIntent(DebugMenuIntent.SendScaleLogForScale(device))
        advanceUntilIdle()

        verify(exactly = 0) { dialogQueueService.enqueue(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // ShowAppReview — with activity
    // -------------------------------------------------------------------------

    @Test
    fun `ShowAppReviewWithActivity launches in-app review`() = runTest(mainDispatcherRule.scheduler) {
        val activity = mockk<Activity>(relaxed = true)

        viewModel.handleIntent(DebugMenuIntent.ShowAppReviewWithActivity(activity))
        advanceUntilIdle()

        coVerify { appReviewManager.launchInAppReview(activity) }
    }

    @Test
    fun `ShowAppReviewWithActivity shows error alert on exception`() = runTest(mainDispatcherRule.scheduler) {
        val activity = mockk<Activity>(relaxed = true)
        coEvery { appReviewManager.launchInAppReview(any()) } throws RuntimeException("review fail")

        viewModel.handleIntent(DebugMenuIntent.ShowAppReviewWithActivity(activity))
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
    }

    // -------------------------------------------------------------------------
    // ShowAppReview — without activity (null)
    // -------------------------------------------------------------------------

    @Test
    fun `ShowAppReview without activity shows error alert`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.ShowAppReview)
        advanceUntilIdle()

        val dialogSlot = slot<DialogModel>()
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        val alert = dialogSlot.captured as DialogModel.Alert
        assertThat(alert.title).isEqualTo(DebugMenuStrings.Alerts.ErrorHeader)
        assertThat(alert.message).isEqualTo(DebugMenuStrings.Alerts.ErrorMessage)
    }

    @Test
    fun `ShowAppReview does not call launchInAppReview when activity is null`() = runTest(mainDispatcherRule.scheduler) {
        viewModel.handleIntent(DebugMenuIntent.ShowAppReview)
        advanceUntilIdle()

        coVerify(exactly = 0) { appReviewManager.launchInAppReview(any()) }
    }

    // -------------------------------------------------------------------------
    // Reducer — SetScaleList
    // -------------------------------------------------------------------------

    @Test
    fun `SetScaleList updates hasScales to true when scales present`() {
        val device = mockk<Device>(relaxed = true)
        every { device.getSKU() } returns "unknown-sku"
        viewModel.handleIntent(DebugMenuIntent.SetScaleList(listOf(device)))
        assertThat(viewModel.state.value.hasScales).isTrue()
    }

    @Test
    fun `SetScaleList updates hasScales to false when empty`() {
        viewModel.handleIntent(DebugMenuIntent.SetScaleList(emptyList()))
        assertThat(viewModel.state.value.hasScales).isFalse()
    }

    // -------------------------------------------------------------------------
    // init block — scale with unknown SKU
    // -------------------------------------------------------------------------

    @Test
    fun `init with device having unknown SKU filters it out`() = runTest(mainDispatcherRule.scheduler) {
        val unknownDevice = Device(sku = "9999")
        pairedScalesFlow.value = listOf(unknownDevice)
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasScales).isFalse()
    }
}
