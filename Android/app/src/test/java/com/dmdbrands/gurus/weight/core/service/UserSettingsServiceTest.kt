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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    // Unconfined makes the eagerly-started StateFlow pump its upstream synchronously
    // during construction so tests can assert on `.value` without races.
    dispatcher = Dispatchers.Unconfined,
  )

  @Test
  fun `defaultGraphSegment stays at DEFAULT seed while upstream is perpetually failing`() {
    // Throw-only upstream: retryWhen will keep re-subscribing forever, but since the
    // upstream never emits a value, the eagerly-seeded StateFlow stays at DEFAULT.
    // This pins the contract that consumers see DEFAULT (not a stale value or crash)
    // when DataStore is broken.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow {
        throw RuntimeException("simulated DataStore IO failure")
      }
    }

    val service = makeService(repository)

    assertEquals(GraphSegment.DEFAULT, service.defaultGraphSegment.value)
  }

  @Test
  fun `defaultGraphSegment retains last successful emission across upstream errors`() {
    // Critical regression guard for the prior catch-then-stateIn bug: emitting a value
    // and then throwing must not leave the StateFlow stuck — and must not lose the
    // already-observed value. retryWhen keeps the upstream alive; .value reflects WEEK.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow {
        emit(GraphSegment.WEEK)
        throw RuntimeException("simulated transient DataStore failure")
      }
    }

    val service = makeService(repository)

    assertEquals(GraphSegment.WEEK, service.defaultGraphSegment.value)
  }

  @Test
  fun `defaultGraphSegment forwards persisted value when upstream succeeds`() {
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flowOf(GraphSegment.YEAR)
    }

    val service = makeService(repository)

    assertEquals(GraphSegment.YEAR, service.defaultGraphSegment.value)
  }

  @Test
  fun `defaultGraphSegment retryWhen does not retry CancellationException — structured concurrency contract`() {
    // retryWhen must return false for CancellationException. If it returned true,
    // cooperative cancellation would loop forever, hanging tests and leaking the
    // serviceScope's collector in production. Pin this with a counter: if the upstream
    // is subscribed more than once, retryWhen has swallowed the cancellation.
    var subscriptionCount = 0
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow {
        subscriptionCount++
        throw CancellationException("upstream cancelled")
      }
    }

    val service = makeService(repository)

    assertEquals(1, subscriptionCount)
    // Cancellation is not converted to a fallback emission, so .value stays at the seed.
    assertEquals(GraphSegment.DEFAULT, service.defaultGraphSegment.value)
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
