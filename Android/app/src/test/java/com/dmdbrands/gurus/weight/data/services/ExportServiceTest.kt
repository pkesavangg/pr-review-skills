package com.dmdbrands.gurus.weight.data.services

import com.dmdbrands.gurus.weight.core.config.HttpErrorConfig
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.data.api.IExportAPI
import com.dmdbrands.gurus.weight.domain.enums.AccountSettingsAction
import com.dmdbrands.gurus.weight.domain.enums.DashboardType
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.support.LogEntry
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.common.strings.ToastStrings
import com.dmdbrands.gurus.weight.features.export.strings.ExportStrings
import com.dmdbrands.library.ggbluetooth.model.GGBTDevice
import com.dmdbrands.library.ggbluetooth.model.GGDeviceLog
import com.dmdbrands.library.ggbluetooth.model.GGDeviceLogResponse
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ExportServiceTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val exportAPI: IExportAPI = mockk()
    private val accountService: IAccountService = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val deviceService: GGDeviceService = mockk()
    private val logRepository: ILogRepository = mockk()
    private val entryRepository: com.dmdbrands.gurus.weight.domain.repository.IEntryRepository = mockk(relaxed = true)
    private val context: android.content.Context = mockk(relaxed = true)

    private lateinit var service: ExportService

    // --- Test Fixtures ---
    private val fakeAccount = Account(
        id = "acc-1",
        firstName = "John",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "john@example.com",
        gender = "male",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 70,
        activityLevel = "normal",
        dashboardType = DashboardType.DASHBOARD_4_METRICS.value,
    )

    private val dashboard12Account = fakeAccount.copy(
        dashboardType = DashboardType.DASHBOARD_12_METRICS.value,
    )

    private val fakeDeviceLog = GGDeviceLog(
        macAddress = "AA:BB:CC:DD:EE:FF",
        log = "Line1\nLine2\nLine3",
    )

    private val fakeDeviceLog2 = GGDeviceLog(
        macAddress = "AA:BB:CC:DD:EE:FF",
        log = "LogA",
    )

    @BeforeEach
    fun setUp() {
        mockkObject(AppLog)
        every { AppLog.i(any(), any()) } returns Unit
        every { AppLog.w(any(), any()) } returns Unit
        every { AppLog.d(any(), any()) } returns Unit
        every { AppLog.e(any<String>(), any<String>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<Throwable>()) } returns Unit
        every { AppLog.e(any<String>(), any<String>(), any<String>()) } returns Unit

        service = createService()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun createService() = ExportService(
        exportAPI = exportAPI,
        accountService = accountService,
        dialogQueueService = dialogQueueService,
        deviceService = deviceService,
        logRepository = logRepository,
        entryRepository = entryRepository,
        context = context,
    )

    // -------------------------------------------------------------------------
    // Shared Helpers
    // -------------------------------------------------------------------------

    private fun httpException(code: Int): HttpException {
        val response = mockk<Response<*>> {
            every { code() } returns code
            every { message() } returns "Mock HTTP error"
            every { errorBody() } returns null
        }
        return HttpException(response)
    }

    private fun stubGetCurrentAccount(account: Account? = fakeAccount) {
        coEvery { accountService.getCurrentAccount() } returns account
    }

    /**
     * Stubs deviceService.getDeviceLogs to immediately invoke callback with Completed response.
     */
    private fun stubDeviceLogsCompleted(logs: List<GGDeviceLog>) {
        every { deviceService.getDeviceLogs(any(), any()) } answers {
            val callback = secondArg<(GGDeviceLogResponse) -> Unit>()
            callback(GGDeviceLogResponse.Completed(logs))
        }
    }

    private fun stubSendScaleLog() {
        coEvery { logRepository.sendScaleLog(any()) } returns Unit
    }

    private fun stubExportDashboard4() {
        coEvery { exportAPI.exportCsvDashboard4(any()) } returns Unit
    }

    private fun stubExportDashboard12() {
        coEvery { exportAPI.exportCsvDashboard12(any()) } returns Unit
    }

    // -------------------------------------------------------------------------
    // sendScaleLog — happy path
    // -------------------------------------------------------------------------

    @Test
    fun `sendScaleLog collects device logs and sends to repository`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(listOf(fakeDeviceLog))
        stubSendScaleLog()

        service.sendScaleLog("broadcast-123")

        coVerify { logRepository.sendScaleLog(any()) }
    }

    @Test
    fun `sendScaleLog creates GGBTDevice with correct broadcastId`() = runTest(mainDispatcherRule.scheduler) {
        val deviceSlot = slot<GGBTDevice>()
        every { deviceService.getDeviceLogs(capture(deviceSlot), any()) } answers {
            val callback = secondArg<(GGDeviceLogResponse) -> Unit>()
            callback(GGDeviceLogResponse.Completed(listOf(fakeDeviceLog)))
        }
        stubSendScaleLog()

        service.sendScaleLog("my-broadcast-id")

        assertThat(deviceSlot.captured.broadcastId).isEqualTo("my-broadcast-id")
        assertThat(deviceSlot.captured.name).isEmpty()
    }

    @Test
    fun `sendScaleLog builds log entries with mac address header`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(listOf(fakeDeviceLog))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        val entries = logSlot.captured
        assertThat(entries.first().data).isEqualTo("Mac Address: AA:BB:CC:DD:EE:FF")
        assertThat(entries.first().time).isEmpty()
    }

    @Test
    fun `sendScaleLog splits log text into individual lines`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(listOf(fakeDeviceLog))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        val entries = logSlot.captured
        // 1 mac header + 3 lines from "Line1\nLine2\nLine3"
        assertThat(entries).hasSize(4)
        assertThat(entries[1].data).isEqualTo("Line1")
        assertThat(entries[2].data).isEqualTo("Line2")
        assertThat(entries[3].data).isEqualTo("Line3")
    }

    @Test
    fun `sendScaleLog handles multiple device logs`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(listOf(fakeDeviceLog, fakeDeviceLog2))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        val entries = logSlot.captured
        // 1 mac header + 3 lines from fakeDeviceLog + 1 line from fakeDeviceLog2
        assertThat(entries).hasSize(5)
    }

    // -------------------------------------------------------------------------
    // sendScaleLog — empty logs
    // -------------------------------------------------------------------------

    @Test
    fun `sendScaleLog still sends mac header when device logs are empty`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(emptyList())
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        // buildScaleLogEntries always adds mac header, so list is never empty
        assertThat(logSlot.captured).hasSize(1)
        assertThat(logSlot.captured.first().data).isEqualTo("Mac Address: ")
        coVerify { logRepository.sendScaleLog(any()) }
    }

    // -------------------------------------------------------------------------
    // sendScaleLog — Fetching callback is ignored
    // -------------------------------------------------------------------------

    @Test
    fun `sendScaleLog ignores Fetching response and waits for Completed`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceService.getDeviceLogs(any(), any()) } answers {
            val callback = secondArg<(GGDeviceLogResponse) -> Unit>()
            // First emit Fetching, then Completed
            callback(GGDeviceLogResponse.Fetching(percentage = 50f))
            callback(GGDeviceLogResponse.Completed(listOf(fakeDeviceLog)))
        }
        stubSendScaleLog()

        service.sendScaleLog("broadcast-123")

        coVerify { logRepository.sendScaleLog(any()) }
    }

    // -------------------------------------------------------------------------
    // sendScaleLog — null log text
    // -------------------------------------------------------------------------

    @Test
    fun `sendScaleLog handles null log text as empty string`() = runTest(mainDispatcherRule.scheduler) {
        val logWithNull = GGDeviceLog(macAddress = "11:22:33:44:55:66", log = null)
        stubDeviceLogsCompleted(listOf(logWithNull))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        val entries = logSlot.captured
        // 1 mac header + 1 line (empty string from split)
        assertThat(entries).hasSize(2)
        assertThat(entries[1].data).isEmpty()
    }

    @Test
    fun `sendScaleLog handles null macAddress as empty in header`() = runTest(mainDispatcherRule.scheduler) {
        val logWithNullMac = GGDeviceLog(macAddress = null, log = "data")
        stubDeviceLogsCompleted(listOf(logWithNullMac))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        assertThat(logSlot.captured.first().data).isEqualTo("Mac Address: ")
    }

    // -------------------------------------------------------------------------
    // sendScaleLog — exception handling
    // -------------------------------------------------------------------------

    @Test
    fun `sendScaleLog rethrows exception from deviceService`() = runTest(mainDispatcherRule.scheduler) {
        every { deviceService.getDeviceLogs(any(), any()) } throws RuntimeException("BLE error")

        assertFailsWith<RuntimeException> { service.sendScaleLog("broadcast-123") }
    }

    @Test
    fun `sendScaleLog rethrows exception from logRepository`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(listOf(fakeDeviceLog))
        coEvery { logRepository.sendScaleLog(any()) } throws RuntimeException("Network error")

        assertFailsWith<RuntimeException> { service.sendScaleLog("broadcast-123") }
    }

    @Test
    fun `sendScaleLog logs error when exception occurs`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(listOf(fakeDeviceLog))
        coEvery { logRepository.sendScaleLog(any()) } throws RuntimeException("Upload failed")

        try {
            service.sendScaleLog("broadcast-123")
        } catch (_: Exception) { }

        verify { AppLog.e("ExportService", "Error sending scale logs", any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // exportCsvToEmail — Dashboard4 (default)
    // -------------------------------------------------------------------------

    @Test
    fun `exportCsvToEmail calls dashboard4 API for dashboard_4_metrics account`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        stubExportDashboard4()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard4(any()) }
        coVerify(exactly = 0) { exportAPI.exportCsvDashboard12(any()) }
    }

    @Test
    fun `exportCsvToEmail passes UTC offset to dashboard4 API`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        val offsetSlot = slot<Int>()
        coEvery { exportAPI.exportCsvDashboard4(capture(offsetSlot)) } returns Unit

        service.exportCsvToEmail()

        // UTC offset should be a reasonable value (within -720 to 840 minutes)
        assertThat(offsetSlot.captured).isAtLeast(-720)
        assertThat(offsetSlot.captured).isAtMost(840)
    }

    // -------------------------------------------------------------------------
    // exportCsvToEmail — Dashboard12
    // -------------------------------------------------------------------------

    @Test
    fun `exportCsvToEmail calls dashboard12 API for dashboard_12_metrics account`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(dashboard12Account)
        stubExportDashboard12()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard12(any()) }
        coVerify(exactly = 0) { exportAPI.exportCsvDashboard4(any()) }
    }

    @Test
    fun `exportCsvToEmail passes UTC offset to dashboard12 API`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(dashboard12Account)
        val offsetSlot = slot<Int>()
        coEvery { exportAPI.exportCsvDashboard12(capture(offsetSlot)) } returns Unit

        service.exportCsvToEmail()

        assertThat(offsetSlot.captured).isAtLeast(-720)
        assertThat(offsetSlot.captured).isAtMost(840)
    }

    // -------------------------------------------------------------------------
    // exportCsvToEmail — null/default dashboardType
    // -------------------------------------------------------------------------

    @Test
    fun `exportCsvToEmail uses dashboard4 when dashboardType is null`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount.copy(dashboardType = null))
        stubExportDashboard4()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard4(any()) }
        coVerify(exactly = 0) { exportAPI.exportCsvDashboard12(any()) }
    }

    @Test
    fun `exportCsvToEmail uses dashboard4 when dashboardType is unknown`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount.copy(dashboardType = "some_other_type"))
        stubExportDashboard4()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard4(any()) }
    }

    @Test
    fun `exportCsvToEmail matches dashboard12 case-insensitively`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount.copy(dashboardType = "DASHBOARD_12_METRICS"))
        stubExportDashboard12()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard12(any()) }
    }

    // -------------------------------------------------------------------------
    // exportCsvToEmail — no current account
    // -------------------------------------------------------------------------

    @Test
    fun `exportCsvToEmail throws when no current account`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(null)

        assertFailsWith<IllegalStateException> { service.exportCsvToEmail() }
    }

    @Test
    fun `exportCsvToEmail throws with descriptive message when no account`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(null)

        val exception = assertFailsWith<IllegalStateException> { service.exportCsvToEmail() }
        assertThat(exception.message).isEqualTo("No current account available")
    }

    // -------------------------------------------------------------------------
    // exportCsvToEmail — API exception
    // -------------------------------------------------------------------------

    @Test
    fun `exportCsvToEmail rethrows HttpException from API`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(500)

        assertFailsWith<HttpException> { service.exportCsvToEmail() }
    }

    @Test
    fun `exportCsvToEmail rethrows generic exception from API`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws RuntimeException("Network fail")

        assertFailsWith<RuntimeException> { service.exportCsvToEmail() }
    }

    @Test
    fun `exportCsvToEmail logs error on exception`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws RuntimeException("fail")

        try {
            service.exportCsvToEmail()
        } catch (_: Exception) { }

        verify { AppLog.e("ExportService", "Error sending CSV to email", any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // exportCsvWithPrompt — success
    // -------------------------------------------------------------------------

    @Test
    fun `exportCsvWithPrompt calls exportCsvToEmail then shows success toast`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        stubExportDashboard4()

        service.exportCsvWithPrompt()

        coVerify { exportAPI.exportCsvDashboard4(any()) }
        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ExportStrings.SuccessMessage
            })
        }
    }

    @Test
    fun `exportCsvWithPrompt logs success`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        stubExportDashboard4()

        service.exportCsvWithPrompt()

        verify { AppLog.i("ExportService", "exportCsvWithPrompt - CSV export completed successfully") }
    }

    // -------------------------------------------------------------------------
    // exportEntriesCsv — product-typed unified export (MOB-1230)
    // -------------------------------------------------------------------------

    @Test
    fun `exportEntriesCsv email mode forwards category and babyId and shows success toast`() =
        runTest(mainDispatcherRule.scheduler) {
            coEvery { entryRepository.exportEntriesCsv(any(), any(), any(), any()) } returns null

            service.exportEntriesCsv(category = "baby", babyId = "baby-1", download = false)

            coVerify {
                entryRepository.exportEntriesCsv(
                    category = "baby",
                    babyId = "baby-1",
                    download = false,
                    utcOffset = any(),
                )
            }
            verify {
                dialogQueueService.showToast(match<Toast> { it.message == ExportStrings.SuccessMessage })
            }
        }

    @Test
    fun `exportEntriesCsv shows error toast and rethrows on HttpException`() =
        runTest(mainDispatcherRule.scheduler) {
            coEvery { entryRepository.exportEntriesCsv(any(), any(), any(), any()) } throws
                httpException(HttpErrorConfig.ResponseCode.UNAUTHORIZED)

            assertFailsWith<HttpException> {
                service.exportEntriesCsv(category = "bp", babyId = null, download = false)
            }
            verify {
                dialogQueueService.showToast(match<Toast> { it.message == ToastStrings.Error.ExportCsv.Message })
            }
        }

    // -------------------------------------------------------------------------
    // exportCsvWithPrompt — HttpException error paths
    // -------------------------------------------------------------------------

    @Test
    fun `exportCsvWithPrompt shows no-connection toast on code 0`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(
            HttpErrorConfig.ResponseCode.NO_INTERNET_CONNECTION
        )

        try {
            service.exportCsvWithPrompt()
        } catch (_: HttpException) { }

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.LoginError.MessageNoConn
            })
        }
    }

    @Test
    fun `exportCsvWithPrompt shows server-error toast on code 500`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(
            HttpErrorConfig.ResponseCode.INTERNAL_SERVER_ERROR
        )

        try {
            service.exportCsvWithPrompt()
        } catch (_: HttpException) { }

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.LoginError.MessageServError
            })
        }
    }

    @Test
    fun `exportCsvWithPrompt shows unauthorized toast on code 401`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(
            HttpErrorConfig.ResponseCode.UNAUTHORIZED
        )

        try {
            service.exportCsvWithPrompt()
        } catch (_: HttpException) { }

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.ExportCsv.Message
            })
        }
    }

    @Test
    fun `exportCsvWithPrompt shows generic toast on unknown HTTP code`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(422)

        try {
            service.exportCsvWithPrompt()
        } catch (_: HttpException) { }

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.LoginError.MessageGeneric
            })
        }
    }

    @Test
    fun `exportCsvWithPrompt rethrows HttpException after showing toast`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(500)

        assertFailsWith<HttpException> { service.exportCsvWithPrompt() }
    }

    @Test
    fun `exportCsvWithPrompt logs error on HttpException`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(500)

        try {
            service.exportCsvWithPrompt()
        } catch (_: HttpException) { }

        verify { AppLog.e("ExportService", "Error during CSV export", any<Throwable>()) }
    }

    // -------------------------------------------------------------------------
    // showErrorToast — direct tests for all branches
    // -------------------------------------------------------------------------

    @Test
    fun `showErrorToast shows no-connection message for code 0`() {
        service.showErrorToast(AccountSettingsAction.EXPORT_CSV, httpException(0))

        verify {
            dialogQueueService.showToast(match<Toast.Simple> {
                it.message == ToastStrings.Error.LoginError.MessageNoConn &&
                    it.title == null && it.action == null
            })
        }
    }

    @Test
    fun `showErrorToast shows server error message for code 500`() {
        service.showErrorToast(AccountSettingsAction.EXPORT_CSV, httpException(500))

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.LoginError.MessageServError
            })
        }
    }

    @Test
    fun `showErrorToast shows unauthorized message for code 401`() {
        service.showErrorToast(AccountSettingsAction.EXPORT_CSV, httpException(401))

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.ExportCsv.Message
            })
        }
    }

    @Test
    fun `showErrorToast shows generic message for unknown code`() {
        service.showErrorToast(AccountSettingsAction.EXPORT_CSV, httpException(403))

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.LoginError.MessageGeneric
            })
        }
    }

    @Test
    fun `showErrorToast shows generic message when error is null`() {
        service.showErrorToast(AccountSettingsAction.EXPORT_CSV, null)

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ToastStrings.Error.LoginError.MessageGeneric
            })
        }
    }

    // -------------------------------------------------------------------------
    // buildScaleLogEntries — edge cases via sendScaleLog
    // -------------------------------------------------------------------------

    @Test
    fun `sendScaleLog uses first device log mac address for header`() = runTest(mainDispatcherRule.scheduler) {
        val log1 = GGDeviceLog(macAddress = "FIRST:MAC", log = "data1")
        val log2 = GGDeviceLog(macAddress = "SECOND:MAC", log = "data2")
        stubDeviceLogsCompleted(listOf(log1, log2))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        assertThat(logSlot.captured.first().data).isEqualTo("Mac Address: FIRST:MAC")
    }

    @Test
    fun `sendScaleLog all log entries have empty time field`() = runTest(mainDispatcherRule.scheduler) {
        stubDeviceLogsCompleted(listOf(fakeDeviceLog))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        logSlot.captured.forEach { entry ->
            assertThat(entry.time).isEmpty()
        }
    }

    // -------------------------------------------------------------------------
    // getUtcOffset — indirectly tested via exportCsvToEmail
    // -------------------------------------------------------------------------

    @Test
    fun `getUtcOffset returns value within valid UTC offset range`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        val offsetSlot = slot<Int>()
        coEvery { exportAPI.exportCsvDashboard4(capture(offsetSlot)) } returns Unit

        service.exportCsvToEmail()

        // UTC offset in minutes should be between -720 (-12h) and 840 (+14h)
        val offset = offsetSlot.captured
        assertThat(offset).isAtLeast(-720)
        assertThat(offset).isAtMost(840)
    }

    @Test
    fun `getUtcOffset is consistent across consecutive calls`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        val offsets = mutableListOf<Int>()
        coEvery { exportAPI.exportCsvDashboard4(capture(offsets)) } returns Unit

        service.exportCsvToEmail()
        service.exportCsvToEmail()

        assertThat(offsets).hasSize(2)
        assertThat(offsets[0]).isEqualTo(offsets[1])
    }

    // -------------------------------------------------------------------------
    // isDashboard12 — indirectly tested via exportCsvToEmail
    // -------------------------------------------------------------------------

    @Test
    fun `isDashboard12 returns false for null dashboardType`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount.copy(dashboardType = null))
        stubExportDashboard4()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard4(any()) }
        coVerify(exactly = 0) { exportAPI.exportCsvDashboard12(any()) }
    }

    @Test
    fun `isDashboard12 returns true for exact dashboard_12_metrics string`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount.copy(dashboardType = "dashboard_12_metrics"))
        stubExportDashboard12()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard12(any()) }
    }

    @Test
    fun `isDashboard12 matches case-insensitively for mixed case`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount.copy(dashboardType = "Dashboard_12_Metrics"))
        stubExportDashboard12()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard12(any()) }
    }

    @Test
    fun `isDashboard12 returns false for empty dashboardType`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount.copy(dashboardType = ""))
        stubExportDashboard4()

        service.exportCsvToEmail()

        coVerify { exportAPI.exportCsvDashboard4(any()) }
    }

    // -------------------------------------------------------------------------
    // buildScaleLogEntries — additional edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `sendScaleLog with single device log having single line produces two entries`() = runTest(mainDispatcherRule.scheduler) {
        val singleLineLog = GGDeviceLog(macAddress = "AB:CD:EF", log = "SingleLine")
        stubDeviceLogsCompleted(listOf(singleLineLog))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        val entries = logSlot.captured
        // 1 mac header + 1 line
        assertThat(entries).hasSize(2)
        assertThat(entries[0].data).isEqualTo("Mac Address: AB:CD:EF")
        assertThat(entries[1].data).isEqualTo("SingleLine")
    }

    @Test
    fun `sendScaleLog with device log having trailing newline includes empty last line`() = runTest(mainDispatcherRule.scheduler) {
        val trailingNewline = GGDeviceLog(macAddress = "AB:CD:EF", log = "Line1\n")
        stubDeviceLogsCompleted(listOf(trailingNewline))
        val logSlot = slot<List<LogEntry>>()
        coEvery { logRepository.sendScaleLog(capture(logSlot)) } returns Unit

        service.sendScaleLog("broadcast-123")

        val entries = logSlot.captured
        // 1 mac header + 2 lines ("Line1" and "")
        assertThat(entries).hasSize(3)
        assertThat(entries[1].data).isEqualTo("Line1")
        assertThat(entries[2].data).isEmpty()
    }

    // -------------------------------------------------------------------------
    // showExportSuccessToast — indirectly tested via exportCsvWithPrompt
    // -------------------------------------------------------------------------

    @Test
    fun `showExportSuccessToast shows toast with ExportStrings SuccessMessage`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        stubExportDashboard4()

        service.exportCsvWithPrompt()

        verify {
            dialogQueueService.showToast(match<Toast> {
                it.message == ExportStrings.SuccessMessage
            })
        }
    }

    @Test
    fun `showExportSuccessToast is not called when export throws`() = runTest(mainDispatcherRule.scheduler) {
        stubGetCurrentAccount(fakeAccount)
        coEvery { exportAPI.exportCsvDashboard4(any()) } throws httpException(500)

        try {
            service.exportCsvWithPrompt()
        } catch (_: HttpException) { }

        // Verify the success toast was NOT shown, only the error toast
        verify(exactly = 0) {
            dialogQueueService.showToast(match<Toast> {
                it.message == ExportStrings.SuccessMessage
            })
        }
    }
}
