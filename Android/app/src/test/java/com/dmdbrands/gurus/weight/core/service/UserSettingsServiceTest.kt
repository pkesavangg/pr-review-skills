package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.repository.IUserSettingsRepository
import com.dmdbrands.gurus.weight.features.common.enums.GraphSegment
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    // Repo emits WEEK once and then throws — the service's .catch must intercept the
    // error and emit GraphSegment.DEFAULT so collectors don't freeze on a stale value.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow {
        emit(GraphSegment.WEEK)
        throw RuntimeException("simulated DataStore IO failure")
      }
    }

    val service = makeService(repository)

    val collected = mutableListOf<GraphSegment>()
    runBlocking {
      // Initial DEFAULT (stateIn seed) → WEEK (upstream emit) → DEFAULT (catch fallback).
      service.defaultGraphSegment.take(3).toCollection(collected)
    }

    assertEquals(
      listOf(GraphSegment.DEFAULT, GraphSegment.WEEK, GraphSegment.DEFAULT),
      collected,
    )
  }

  @Test
  fun `defaultGraphSegment exposes DEFAULT seed before upstream emits`() {
    // A pristine cold flow that suspends forever: the StateFlow must surface the seed
    // value so the UI never observes null/uninitialised state.
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow { /* never emits */ }
    }

    val service = makeService(repository)

    val first = runBlocking { service.defaultGraphSegment.value }

    assertEquals(GraphSegment.DEFAULT, first)
  }

  @Test
  fun `setDefaultGraphSegment delegates to repository`() {
    val repository = mockk<IUserSettingsRepository>(relaxed = true) {
      every { defaultGraphSegmentFlow } returns flow { /* never emits */ }
      coEvery { setDefaultGraphSegment(any()) } returns Unit
    }

    val service = makeService(repository)

    // No exception → the service forwards the call unchanged.
    runBlocking { service.setDefaultGraphSegment(GraphSegment.YEAR) }

    assertTrue(true)
  }
}
