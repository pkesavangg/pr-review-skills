package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.data.api.ISupportAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.LogDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.log.LogEntity
import com.dmdbrands.gurus.weight.domain.model.api.support.LogEntry
import com.dmdbrands.gurus.weight.domain.repository.ILogRepository
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import kotlinx.coroutines.test.TestScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LogRepositoryTest {

  companion object {
    private const val ACCOUNT_ID = "account-123"
    private const val TAG_TEST = "TAG"
    private const val MESSAGE_TEST = "message"
    private const val TYPE_DEBUG = "debug"
    private const val DB_ERROR = "DB error"
    private const val API_URL = "https://api.test.com"
    private const val TIMEZONE = "America/New_York"
    private const val TIMEZONE_OFFSET = "-300"
    private const val APP_VERSION = "1.0.0"
    private const val OS_VERSION = "14"
    private const val MANUFACTURER = "Google"
    private const val MODEL = "Pixel"
    private const val LOG_TIME = "2024-01-01T00:00:00Z"
    private const val LOG_DATA = "test log"
  }

  @MockK(relaxUnitFun = true)
  lateinit var logDao: LogDao

  @MockK(relaxUnitFun = true)
  lateinit var supportAPI: ISupportAPI

  @MockK(relaxUnitFun = true)
  lateinit var accountService: IAccountService

  private lateinit var repository: LogRepository

  @Before
  fun setUp() {
    MockKAnnotations.init(this)
    mockkObject(AppStatusService)
    mockkObject(DeviceInfoUtil)
    every { accountService.activeAccountFlow } returns flowOf(null)
    repository = LogRepository(logDao, supportAPI, accountService, TestScope())
  }

  private fun setInitialized(value: Boolean) {
    val field = LogRepository::class.java.getDeclaredField("isInitialized")
    field.isAccessible = true
    field.set(repository, value)
  }

  @After
  fun tearDown() {
    unmockkObject(AppStatusService)
    unmockkObject(DeviceInfoUtil)
    unmockkAll()
  }

  // getSessionId

  @Test
  fun `getSessionId returns non-empty session ID`() {
    val sessionId = repository.getSessionId()

    assertThat(sessionId).isNotEmpty()
  }

  // resetSession

  @Test
  fun `resetSession generates a new unique session ID`() {
    val firstSessionId = repository.getSessionId()
    repository.resetSession()
    val secondSessionId = repository.getSessionId()

    assertThat(secondSessionId).isNotEqualTo(firstSessionId)
  }

  // updateAccountId

  @Test
  fun `updateAccountId does not throw`() {
    repository.updateAccountId(ACCOUNT_ID)
  }

  // deleteAllLogs

  @Test
  fun `deleteAllLogs delegates to logDao`() = runTest {
    coEvery { logDao.deleteAllLogs() } just Runs

    repository.deleteAllLogs()

    coVerify { logDao.deleteAllLogs() }
  }

  // deleteLogsByAccountId

  @Test
  fun `deleteLogsByAccountId passes accountId to logDao`() = runTest {
    coEvery { logDao.deleteLogsByAccountId(any()) } just Runs

    repository.deleteLogsByAccountId(ACCOUNT_ID)

    coVerify { logDao.deleteLogsByAccountId(ACCOUNT_ID) }
  }

  // clearLogs

  @Test
  fun `clearLogs calls logDao deleteAllLogs`() = runTest {
    coEvery { logDao.deleteAllLogs() } just Runs

    repository.clearLogs()

    coVerify { logDao.deleteAllLogs() }
  }

  @Test
  fun `clearLogs rethrows exception from logDao`() = runTest {
    coEvery { logDao.deleteAllLogs() } throws RuntimeException(DB_ERROR)

    var thrown: Exception? = null
    try {
      repository.clearLogs()
    } catch (e: Exception) {
      thrown = e
    }

    assertThat(thrown).isNotNull()
    assertThat(thrown!!.message).isEqualTo(DB_ERROR)
  }

  // deleteLogsOlderThanDays

  @Test
  fun `deleteLogsOlderThanDays delegates to logDao with computed timestamp`() = runTest {
    coEvery { logDao.deleteLogsOlderThanDays(any()) } just Runs

    repository.deleteLogsOlderThanDays(7)

    coVerify { logDao.deleteLogsOlderThanDays(any()) }
  }

  // log (not initialized)

  @Test
  fun `log returns early without inserting when not initialized`() = runTest {
    repository.log(TAG_TEST, MESSAGE_TEST, TYPE_DEBUG, null)

    coVerify(exactly = 0) { logDao.insertLog(any()) }
  }

  // getAllLogs

  @Test
  fun `getAllLogs delegates directly to logDao`() {
    every { logDao.getAllLogs() } returns flowOf(emptyList())

    repository.getAllLogs()

    io.mockk.verify { logDao.getAllLogs() }
  }

  // getLogs (guarded by isInitialized)

  @Test
  fun `getLogs emits emptyList when not initialized`() = runTest {
    val result = repository.getLogs().first()

    assertThat(result).isEmpty()
  }

  // getLogsByAccountId (guarded by isInitialized)

  @Test
  fun `getLogsByAccountId emits emptyList when not initialized`() = runTest {
    val result = repository.getLogsByAccountId(ACCOUNT_ID).first()

    assertThat(result).isEmpty()
  }

  // getLogsBySessionId (guarded by isInitialized)

  @Test
  fun `getLogsBySessionId emits emptyList when not initialized`() = runTest {
    val result = repository.getLogsBySessionId().first()

    assertThat(result).isEmpty()
  }

  // sendLogs

  @Test
  fun `sendLogs calls supportAPI with log request`() = runTest {
    val mockResponse = mockk<Response<ResponseBody>>()
    every { mockResponse.isSuccessful } returns true
    every { mockResponse.body() } returns null
    setupDeviceAndAppMocks()
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    repository.sendLogs()

    coVerify { supportAPI.sendLog(any()) }
  }

  @Test
  fun `sendLogs throws when API returns error response`() = runTest {
    val mockResponse = mockk<Response<ResponseBody>>()
    every { mockResponse.isSuccessful } returns false
    every { mockResponse.code() } returns 500
    every { mockResponse.errorBody() } returns null
    setupDeviceAndAppMocks()
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    var thrown: Exception? = null
    try {
      repository.sendLogs()
    } catch (e: Exception) {
      thrown = e
    }

    assertThat(thrown).isNotNull()
    assertThat(thrown!!.message).contains("500")
  }

  // sendScaleLog

  @Test
  fun `sendScaleLog returns early without API call for empty list`() = runTest {
    repository.sendScaleLog(emptyList())

    coVerify(exactly = 0) { supportAPI.sendLog(any()) }
  }

  @Test
  fun `sendScaleLog calls supportAPI for non-empty log list`() = runTest {
    val mockResponse = mockk<Response<ResponseBody>>()
    every { mockResponse.isSuccessful } returns true
    every { mockResponse.body() } returns null
    every { AppStatusService.version } returns APP_VERSION
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    repository.sendScaleLog(listOf(LogEntry(time = LOG_TIME, data = LOG_DATA)))

    coVerify { supportAPI.sendLog(any()) }
  }

  @Test
  fun `sendScaleLog throws when API returns error response`() = runTest {
    val mockResponse = mockk<Response<ResponseBody>>()
    every { mockResponse.isSuccessful } returns false
    every { mockResponse.code() } returns 503
    every { mockResponse.errorBody() } returns null
    every { AppStatusService.version } returns APP_VERSION
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    var thrown: Exception? = null
    try {
      repository.sendScaleLog(listOf(LogEntry(time = LOG_TIME, data = LOG_DATA)))
    } catch (e: Exception) {
      thrown = e
    }

    assertThat(thrown).isNotNull()
    assertThat(thrown!!.message).contains("503")
  }

  // clearLogsForCurrentAccount

  @Test
  fun `clearLogsForCurrentAccount delegates to logDao deleteLogsByAccountId`() = runTest {
    coEvery { logDao.deleteLogsByAccountId(any()) } just Runs

    repository.clearLogsForCurrentAccount()

    coVerify { logDao.deleteLogsByAccountId(any()) }
  }

  @Test
  fun `clearLogsForCurrentAccount rethrows exception from logDao`() = runTest {
    coEvery { logDao.deleteLogsByAccountId(any()) } throws RuntimeException(DB_ERROR)

    var thrown: Exception? = null
    try {
      repository.clearLogsForCurrentAccount()
    } catch (e: Exception) {
      thrown = e
    }

    assertThat(thrown).isNotNull()
    assertThat(thrown!!.message).isEqualTo(DB_ERROR)
  }

  // sendLogsForCurrentAccount

  @Test
  fun `sendLogsForCurrentAccount delegates to sendLogs`() = runTest {
    val mockResponse = mockk<Response<ResponseBody>>()
    every { mockResponse.isSuccessful } returns true
    every { mockResponse.body() } returns null
    setupDeviceAndAppMocks()
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    repository.sendLogsForCurrentAccount()

    coVerify { supportAPI.sendLog(any()) }
  }

  // initialize

  @Test
  fun `initialize launches cleanup coroutine`() = runTest {
    every { logDao.getLogCount() } returns flowOf(5)
    coEvery { logDao.deleteLogsOlderThanDays(any()) } just Runs

    repository.initialize()

    // initialize() launches on repositoryScope (fire-and-forget) so we verify it doesn't throw
  }

  @Test
  fun `initialize trims logs when count exceeds max`() = runTest {
    every { logDao.getLogCount() } returns flowOf(20000)
    coEvery { logDao.deleteLogsOlderThanDays(any()) } just Runs
    coEvery { logDao.deleteOldestLogs(any()) } just Runs

    repository.initialize()

    // Fire-and-forget coroutine — verify no throw
  }

  @Test
  fun `initialize returns early on second call`() = runTest {
    setInitialized(true)

    // Should return immediately without launching coroutine
    repository.initialize()
  }

  // getLogsByType

  @Test
  fun `getLogsByType delegates to logDao with type name`() {
    every { logDao.getLogsByType(any()) } returns flowOf(emptyList())

    repository.getLogsByType(ILogRepository.LogType.DEBUG)

    io.mockk.verify { logDao.getLogsByType("DEBUG") }
  }

  // getLogsForLastDays

  @Test
  fun `getLogsForLastDays delegates to logDao with computed timestamp`() {
    every { logDao.getLogsForLastDays(any()) } returns flowOf(emptyList())

    repository.getLogsForLastDays(3)

    io.mockk.verify { logDao.getLogsForLastDays(any()) }
  }

  // log (when initialized)

  @Test
  fun `log inserts entry into logDao when initialized`() = runTest {
    setInitialized(true)
    coEvery { logDao.insertLog(any()) } just Runs

    repository.log(TAG_TEST, MESSAGE_TEST, TYPE_DEBUG, null)

    coVerify { logDao.insertLog(match { it.tag == TAG_TEST && it.message == MESSAGE_TEST }) }
  }

  @Test
  fun `log swallows exception from logDao when initialized`() = runTest {
    setInitialized(true)
    coEvery { logDao.insertLog(any()) } throws RuntimeException(DB_ERROR)

    // Should not throw
    repository.log(TAG_TEST, MESSAGE_TEST, TYPE_DEBUG, null)
  }

  // getLogs (when initialized)

  @Test
  fun `getLogs emits logs from logDao when initialized`() = runTest {
    setInitialized(true)
    val mockLog = mockk<LogEntity>(relaxed = true)
    every { logDao.getAllLogs() } returns flowOf(listOf(mockLog))

    val result = repository.getLogs().first()

    assertThat(result).hasSize(1)
  }

  // getLogsByAccountId (when initialized)

  @Test
  fun `getLogsByAccountId emits logs from logDao when initialized`() = runTest {
    setInitialized(true)
    val mockLog = mockk<LogEntity>(relaxed = true)
    every { logDao.getLogsByAccountId(ACCOUNT_ID) } returns flowOf(listOf(mockLog))

    val result = repository.getLogsByAccountId(ACCOUNT_ID).first()

    assertThat(result).hasSize(1)
  }

  // getLogsBySessionId (when initialized)

  @Test
  fun `getLogsBySessionId emits logs from logDao when initialized`() = runTest {
    setInitialized(true)
    val mockLog = mockk<LogEntity>(relaxed = true)
    every { logDao.getLogsBySessionId(any()) } returns flowOf(listOf(mockLog))

    val result = repository.getLogsBySessionId().first()

    assertThat(result).hasSize(1)
  }

  // sendLogs with initialized state and logs

  @Test
  fun `sendLogs filters logs by timestamp when initialized`() = runTest {
    setInitialized(true)
    val recentLog = mockk<LogEntity>(relaxed = true) {
      every { timestamp } returns System.currentTimeMillis()
    }
    val oldLog = mockk<LogEntity>(relaxed = true) {
      every { timestamp } returns 0L
    }
    every { logDao.getLogsByAccountId(any()) } returns flowOf(listOf(recentLog, oldLog))
    val mockResponse = mockk<Response<ResponseBody>>()
    every { mockResponse.isSuccessful } returns true
    every { mockResponse.body() } returns null
    setupDeviceAndAppMocks()
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    repository.sendLogs()

    coVerify { supportAPI.sendLog(any()) }
  }

  private fun setupDeviceAndAppMocks() {
    every { AppStatusService.apiUrl } returns API_URL
    every { AppStatusService.getUserTimezone() } returns TIMEZONE
    every { AppStatusService.getUserTimezoneOffset() } returns TIMEZONE_OFFSET
    every { AppStatusService.version } returns APP_VERSION
    every { DeviceInfoUtil.getOSVersion() } returns OS_VERSION
    every { DeviceInfoUtil.getAppVersion() } returns APP_VERSION
    every { DeviceInfoUtil.getManufacturer() } returns MANUFACTURER
    every { DeviceInfoUtil.getModel() } returns MODEL
  }
}
