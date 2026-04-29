package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
  fun `defaultGraphSegment falls back to DEFAULT when upstream throws`() {
    // Repo emits WEEK then throws — the service's .catch must intercept the error and
    // emit GraphSegment.DEFAULT so collectors don't freeze on a stale value.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow {
        emit(GraphSegment.WEEK)
        throw RuntimeException("simulated DataStore IO failure")
      }
    }

    val service = makeService(repository)

    val collected = runBlocking { service.defaultGraphSegment.toList() }

    // retry(3) restarts the cold upstream on each failure; WEEK is re-emitted per attempt
    // (1 original + 3 retries = 4), then .catch emits DEFAULT after retries are exhausted.
    assertEquals(
      listOf(GraphSegment.WEEK, GraphSegment.WEEK, GraphSegment.WEEK, GraphSegment.WEEK, GraphSegment.DEFAULT),
      collected,
    )
  }

  @Test
  fun `defaultGraphSegment forwards persisted value when upstream succeeds`() {
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flowOf(GraphSegment.YEAR)
    }

    val service = makeService(repository)

    val collected = runBlocking { service.defaultGraphSegment.toList() }

    assertEquals(listOf(GraphSegment.YEAR), collected)
  }

  @Test
  fun `setDefaultGraphSegment delegates to repository`() {
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow { /* never emits */ }
      coEvery { setDefaultGraphSegment(any()) } returns Unit
    }

    val service = makeService(repository)

    runBlocking { service.setDefaultGraphSegment(GraphSegment.YEAR) }

    coVerify(exactly = 1) { repository.setDefaultGraphSegment(GraphSegment.YEAR) }
  }

  @Test
  fun `defaultGraphSegment does not swallow CancellationException — structured concurrency contract`() {
    // .catch catches all Throwable; this test pins that CancellationException is NOT consumed
    // by .catch but propagates out, preserving structured concurrency for coroutine cancellation.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow {
        throw CancellationException("scope cancelled")
      }
    }

    val service = makeService(repository)

    try {
      runBlocking { service.defaultGraphSegment.toList() }
      fail("Expected CancellationException to propagate through the flow, not be swallowed by .catch")
    } catch (e: CancellationException) {
      assertTrue("CancellationException message preserved", e.message == "scope cancelled")
    }
  }

  @Test
  fun `setDefaultGraphSegment rethrows when repository throws`() {
    // The ViewModel relies on the rethrow to drive its catch path (loader dismiss + toast).
    // If the service ever swallows the exception silently, the UI would lie about success.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow { /* never emits */ }
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
      every { defaultGraphSegmentFlow } returns flow { /* never emits */ }
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
