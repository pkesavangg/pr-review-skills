package com.dmdbrands.gurus.weight.data.repository

import com.dmdbrands.gurus.weight.core.service.AppStatusService
import com.dmdbrands.gurus.weight.core.shared.utilities.DeviceInfoUtil
import com.dmdbrands.gurus.weight.data.api.ISupportAPI
import com.dmdbrands.gurus.weight.data.storage.db.dao.LogDao
import com.dmdbrands.gurus.weight.data.storage.db.entity.log.LogEntity
import com.dmdbrands.gurus.weight.domain.model.api.support.LogEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
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
    repository = LogRepository(logDao, supportAPI, accountService)
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
    repository.updateAccountId("account-123")
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

    repository.deleteLogsByAccountId("account-123")

    coVerify { logDao.deleteLogsByAccountId("account-123") }
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
    coEvery { logDao.deleteAllLogs() } throws RuntimeException("DB error")

    var thrown: Exception? = null
    try {
      repository.clearLogs()
    } catch (e: Exception) {
      thrown = e
    }

    assertThat(thrown).isNotNull()
    assertThat(thrown!!.message).isEqualTo("DB error")
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
    repository.log("TAG", "message", "debug", null)

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
    val result = repository.getLogsByAccountId("account-123").first()

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
    every { AppStatusService.apiUrl } returns "https://api.test.com"
    every { AppStatusService.getUserTimezone() } returns "America/New_York"
    every { AppStatusService.getUserTimezoneOffset() } returns "-300"
    every { AppStatusService.version } returns "1.0.0"
    every { DeviceInfoUtil.getOSVersion() } returns "14"
    every { DeviceInfoUtil.getAppVersion() } returns "1.0.0"
    every { DeviceInfoUtil.getManufacturer() } returns "Google"
    every { DeviceInfoUtil.getModel() } returns "Pixel"
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
    every { AppStatusService.apiUrl } returns "https://api.test.com"
    every { AppStatusService.getUserTimezone() } returns "America/New_York"
    every { AppStatusService.getUserTimezoneOffset() } returns "-300"
    every { AppStatusService.version } returns "1.0.0"
    every { DeviceInfoUtil.getOSVersion() } returns "14"
    every { DeviceInfoUtil.getAppVersion() } returns "1.0.0"
    every { DeviceInfoUtil.getManufacturer() } returns "Google"
    every { DeviceInfoUtil.getModel() } returns "Pixel"
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
    every { AppStatusService.version } returns "1.0.0"
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    repository.sendScaleLog(listOf(LogEntry(time = "2024-01-01T00:00:00Z", data = "test log")))

    coVerify { supportAPI.sendLog(any()) }
  }

  @Test
  fun `sendScaleLog throws when API returns error response`() = runTest {
    val mockResponse = mockk<Response<ResponseBody>>()
    every { mockResponse.isSuccessful } returns false
    every { mockResponse.code() } returns 503
    every { mockResponse.errorBody() } returns null
    every { AppStatusService.version } returns "1.0.0"
    coEvery { supportAPI.sendLog(any()) } returns mockResponse

    var thrown: Exception? = null
    try {
      repository.sendScaleLog(listOf(LogEntry(time = "2024-01-01T00:00:00Z", data = "test")))
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
}
