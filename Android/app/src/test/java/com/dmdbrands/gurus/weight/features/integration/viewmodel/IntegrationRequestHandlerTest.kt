package com.dmdbrands.gurus.weight.features.integration.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.services.IIntegrationService
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.integration.strings.IntegrationStrings
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [enqueueIntegrationRequestModal] and its submit handler.
 *
 * The submit handler is private, so it is exercised through the enqueued
 * [DialogModel.Custom.onConfirm] callback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationRequestHandlerTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val service: IIntegrationService = mockk(relaxed = true)

    private val dialogSlot = slot<DialogModel>()

    @BeforeEach
    fun setUp() {
        // Capture must be registered before enqueueIntegrationRequestModal runs.
        every { dialogQueueService.enqueue(capture(dialogSlot)) } just Runs
    }

    private fun capturedOnConfirm(): (Any) -> Unit =
        (dialogSlot.captured as DialogModel.Custom).onConfirm!!

    @Test
    fun `submitting non-blank input shows the success toast`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { service.submitIntegrationRequest(any()) } just Runs
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } just Runs

        enqueueIntegrationRequestModal(this, dialogQueueService, service)
        capturedOnConfirm().invoke("  Garmin  ")
        advanceUntilIdle()

        coVerify { service.submitIntegrationRequest("Garmin") }
        verify { dialogQueueService.showLoader(IntegrationStrings.loading) }
        verify { dialogQueueService.dismissLoader() }
        assertThat(toastSlot.captured.message).isEqualTo(IntegrationStrings.RequestSubmittedToast)
    }

    @Test
    fun `submission failure shows the failure toast`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { service.submitIntegrationRequest(any()) } throws RuntimeException("boom")
        val toastSlot = slot<Toast.Simple>()
        every { dialogQueueService.showToast(capture(toastSlot)) } just Runs

        enqueueIntegrationRequestModal(this, dialogQueueService, service)
        capturedOnConfirm().invoke("Garmin")
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
        assertThat(toastSlot.captured.message).isEqualTo(IntegrationStrings.RequestFailedToast)
    }

    @Test
    fun `blank input does not submit or show a toast`() = runTest(mainDispatcherRule.scheduler) {
        enqueueIntegrationRequestModal(this, dialogQueueService, service)
        capturedOnConfirm().invoke("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { service.submitIntegrationRequest(any()) }
        verify(exactly = 0) { dialogQueueService.showToast(any()) }
        verify(exactly = 0) { dialogQueueService.showLoader(any(), any()) }
    }

    @Test
    fun `cancellation propagates without a false failure toast`() = runTest(mainDispatcherRule.scheduler) {
        coEvery { service.submitIntegrationRequest(any()) } throws CancellationException("screen left")

        enqueueIntegrationRequestModal(this, dialogQueueService, service)
        capturedOnConfirm().invoke("Garmin")
        advanceUntilIdle()

        // Cancellation must not be reported to the user as a failure.
        verify(exactly = 0) { dialogQueueService.showToast(any()) }
    }
}
