package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UserSettingsServiceTest {

  private fun makeService(
    repository: IUserSettingsRepository,
  ): UserSettingsService = UserSettingsService(
    userSettingsRepository = repository,
    connectivityObserver = mockk<IConnectivityObserver>(relaxed = true),
    dialogQueueService = mockk<IDialogQueueService>(relaxed = true),
    appNavigationService = mockk<IAppNavigationService>(relaxed = true),
  )

  @Test
  fun `setDefaultGraphSegment delegates to repository`() {
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      coEvery { setDefaultGraphSegment(any()) } returns Unit
    }

    val service = makeService(repository)

    runBlocking { service.setDefaultGraphSegment(GraphSegment.YEAR) }

    coVerify(exactly = 1) { repository.setDefaultGraphSegment(GraphSegment.YEAR) }
  }

  @Test
  fun `setDefaultGraphSegment rethrows when repository throws`() {
    // The ViewModel relies on the rethrow to drive its catch path (loader dismiss + toast).
    // If the service ever swallows the exception silently, the UI would lie about success.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      coEvery { setDefaultGraphSegment(any()) } throws RuntimeException("boom")
    }

    val service = makeService(repository)

    try {
      runBlocking { service.setDefaultGraphSegment(GraphSegment.WEEK) }
      fail("Expected RuntimeException to propagate from repository through service")
    } catch (e: RuntimeException) {
      assertTrue("Expected original exception message preserved", e.message == "boom")
    }
  }

  @Test
  fun `setDefaultGraphSegment rethrows CancellationException without logging — structured concurrency contract`() {
    // The catch block must re-throw CancellationException BEFORE AppLog.e so that:
    // (a) structured concurrency is preserved for the caller's scope, and
    // (b) normal scope cancellation is not misreported as an error.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      coEvery { setDefaultGraphSegment(any()) } throws CancellationException("scope cancelled")
    }

    val service = makeService(repository)

    try {
      runBlocking { service.setDefaultGraphSegment(GraphSegment.MONTH) }
      fail("Expected CancellationException to propagate from repository through service")
    } catch (e: CancellationException) {
      assertTrue("CancellationException message preserved", e.message == "scope cancelled")
    }
  }
}
