package com.dmdbrands.gurus.weight.features.common.service

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.LoaderStyle
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Loader
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class DialogQueueServiceTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var service: DialogQueueService

    // --- Test Fixtures ---

    private val alertDialog = DialogModel.Alert(
        message = "Test alert",
        onDismiss = null,
        alertPriority = 5,
    )

    private val confirmDialog = DialogModel.Confirm(
        message = "Test confirm",
        confirmPriority = 3,
    )

    private val highPriorityAlert = DialogModel.Alert(
        message = "High priority",
        onDismiss = null,
        alertPriority = 1,
    )

    private val delayedDialog = DialogModel.Alert(
        message = "Delayed",
        onDismiss = null,
        alertPriority = 2,
        alertDelayMillis = 500L,
    )

    private val testToast = Toast.Simple(message = "Test toast")
    private val testToastWithTitle = Toast.Simple(message = "Toast message", title = "Toast title")

    @BeforeEach
    fun setUp() {
        service = DialogQueueService(CoroutineScope(mainDispatcherRule.dispatcher))
    }

    // -------------------------------------------------------------------------
    // Initial State
    // -------------------------------------------------------------------------

    @Test
    fun `currentDialog is null initially`() {
        assertThat(service.currentDialog.value).isNull()
    }

    @Test
    fun `currentToast is null initially`() {
        assertThat(service.currentToast.value).isNull()
    }

    @Test
    fun `loader is null initially`() {
        assertThat(service.loader.value).isNull()
    }

    // -------------------------------------------------------------------------
    // enqueue
    // -------------------------------------------------------------------------

    @Test
    fun `enqueue shows dialog immediately when no dialog is showing`() {
        service.enqueue(alertDialog)
        assertThat(service.currentDialog.value).isEqualTo(alertDialog)
    }

    @Test
    fun `enqueue does not change currentDialog when one is already showing`() {
        service.enqueue(alertDialog)
        service.enqueue(confirmDialog)
        assertThat(service.currentDialog.value).isEqualTo(alertDialog)
    }

    @Test
    fun `enqueue increases queue size`() {
        service.enqueue(alertDialog)
        service.enqueue(confirmDialog)
        assertThat(service.getQueueSize()).isEqualTo(2)
    }

    @Test
    fun `enqueue shows delayed dialog immediately when queue is empty`() {
        service.enqueue(delayedDialog)
        assertThat(service.currentDialog.value).isEqualTo(delayedDialog)
    }

    // -------------------------------------------------------------------------
    // showToast
    // -------------------------------------------------------------------------

    @Test
    fun `showToast sets currentToast`() {
        service.showToast(testToast)
        assertThat(service.currentToast.value).isEqualTo(testToast)
    }

    @Test
    fun `showToast replaces existing toast`() {
        service.showToast(testToast)
        service.showToast(testToastWithTitle)
        assertThat(service.currentToast.value).isEqualTo(testToastWithTitle)
    }

    // -------------------------------------------------------------------------
    // showLoader
    // -------------------------------------------------------------------------

    @Test
    fun `showLoader sets loader with message and style`() {
        service.showLoader("Loading...", LoaderStyle.CIRCULAR)
        assertThat(service.loader.value).isEqualTo(Loader("Loading...", LoaderStyle.CIRCULAR))
    }

    @Test
    fun `showLoader sets loader with DASHED style`() {
        service.showLoader("Syncing...", LoaderStyle.DASHED)
        assertThat(service.loader.value).isEqualTo(Loader("Syncing...", LoaderStyle.DASHED))
    }

    @Test
    fun `showLoader replaces existing loader`() {
        service.showLoader("First", LoaderStyle.CIRCULAR)
        service.showLoader("Second", LoaderStyle.DOT)
        assertThat(service.loader.value).isEqualTo(Loader("Second", LoaderStyle.DOT))
    }

    @Test
    fun `showLoader uses DASHED style by default`() {
        service.showLoader("Loading...")
        assertThat(service.loader.value).isEqualTo(Loader("Loading...", LoaderStyle.DASHED))
    }

    // -------------------------------------------------------------------------
    // dismissLoader
    // -------------------------------------------------------------------------

    @Test
    fun `dismissLoader clears loader`() {
        service.showLoader("Loading...", LoaderStyle.CIRCULAR)
        service.dismissLoader()
        assertThat(service.loader.value).isNull()
    }

    @Test
    fun `dismissLoader when no loader is no-op`() {
        service.dismissLoader()
        assertThat(service.loader.value).isNull()
    }

    // -------------------------------------------------------------------------
    // dismissToast
    // -------------------------------------------------------------------------

    @Test
    fun `dismissToast clears currentToast`() {
        service.showToast(testToast)
        service.dismissToast()
        assertThat(service.currentToast.value).isNull()
    }

    @Test
    fun `dismissToast when no toast is no-op`() {
        service.dismissToast()
        assertThat(service.currentToast.value).isNull()
    }

    // -------------------------------------------------------------------------
    // dismissCurrent
    // -------------------------------------------------------------------------

    @Test
    fun `dismissCurrent clears dialog when queue has only one dialog`() {
        service.enqueue(alertDialog)
        service.dismissCurrent()
        assertThat(service.currentDialog.value).isNull()
    }

    @Test
    fun `dismissCurrent removes dialog from queue`() {
        service.enqueue(alertDialog)
        assertThat(service.getQueueSize()).isEqualTo(1)
        service.dismissCurrent()
        assertThat(service.getQueueSize()).isEqualTo(0)
    }

    @Test
    fun `dismissCurrent shows next dialog when queue has more`() = runTest {
        service.enqueue(alertDialog)
        service.enqueue(confirmDialog)

        service.dismissCurrent()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertThat(service.currentDialog.value).isEqualTo(confirmDialog)
    }

    @Test
    fun `dismissCurrent does nothing when no dialog is showing`() {
        service.dismissCurrent()
        assertThat(service.currentDialog.value).isNull()
        assertThat(service.getQueueSize()).isEqualTo(0)
    }

    @Test
    fun `dismissCurrent shows next dialog respecting priority order`() = runTest {
        service.enqueue(alertDialog)          // p=5, shows immediately
        service.enqueue(confirmDialog)        // p=3, queued
        service.enqueue(highPriorityAlert)    // p=1, queued

        // First dismiss: alertDialog(5) removed, next = highPriorityAlert(1)
        service.dismissCurrent()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertThat(service.currentDialog.value).isEqualTo(highPriorityAlert)

        // Second dismiss: highPriorityAlert(1) removed, next = confirmDialog(3)
        service.dismissCurrent()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertThat(service.currentDialog.value).isEqualTo(confirmDialog)
    }

    @Test
    fun `dismissCurrent respects delay before showing next dialog`() = runTest {
        service.enqueue(alertDialog)
        service.enqueue(delayedDialog)    // delay=500ms

        service.dismissCurrent()

        // Before advancing time, dialog should be null
        assertThat(service.currentDialog.value).isNull()

        // Advance past the delay
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(500L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertThat(service.currentDialog.value).isEqualTo(delayedDialog)
    }

    @Test
    fun `dismissCurrent does not show next dialog before delay elapses`() = runTest {
        service.enqueue(alertDialog)
        service.enqueue(delayedDialog)    // delay=500ms

        service.dismissCurrent()

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(499L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertThat(service.currentDialog.value).isNull()
    }

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    @Test
    fun `clear removes all dialogs and sets currentDialog to null`() {
        service.enqueue(alertDialog)
        service.enqueue(confirmDialog)
        service.clear()
        assertThat(service.currentDialog.value).isNull()
        assertThat(service.getQueueSize()).isEqualTo(0)
    }

    @Test
    fun `clear when already empty is no-op`() {
        service.clear()
        assertThat(service.currentDialog.value).isNull()
        assertThat(service.getQueueSize()).isEqualTo(0)
    }

    @Test
    fun `clear does not affect toast or loader`() {
        service.showToast(testToast)
        service.showLoader("Loading...", LoaderStyle.CIRCULAR)
        service.enqueue(alertDialog)

        service.clear()

        assertThat(service.currentDialog.value).isNull()
        assertThat(service.currentToast.value).isEqualTo(testToast)
        assertThat(service.loader.value).isEqualTo(Loader("Loading...", LoaderStyle.CIRCULAR))
    }

    @Test
    fun `clear cancels pending delayed dialog transition`() = runTest {
        service.enqueue(alertDialog)
        service.enqueue(delayedDialog) // delay=500ms

        service.dismissCurrent()
        // Delayed transition is now pending

        service.clear()

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertThat(service.currentDialog.value).isNull()
    }

    // -------------------------------------------------------------------------
    // getQueueSize
    // -------------------------------------------------------------------------

    @Test
    fun `getQueueSize returns 0 for empty queue`() {
        assertThat(service.getQueueSize()).isEqualTo(0)
    }

    @Test
    fun `getQueueSize returns count of all dialogs including current`() {
        service.enqueue(alertDialog)
        assertThat(service.getQueueSize()).isEqualTo(1)
        service.enqueue(confirmDialog)
        assertThat(service.getQueueSize()).isEqualTo(2)
        service.enqueue(highPriorityAlert)
        assertThat(service.getQueueSize()).isEqualTo(3)
    }

    @Test
    fun `getQueueSize decreases after dismissCurrent`() {
        service.enqueue(alertDialog)
        service.enqueue(confirmDialog)
        assertThat(service.getQueueSize()).isEqualTo(2)

        service.dismissCurrent()
        assertThat(service.getQueueSize()).isEqualTo(1)
    }

    // -------------------------------------------------------------------------
    // peekNextDialog
    // -------------------------------------------------------------------------

    @Test
    fun `peekNextDialog returns null when queue is empty`() {
        assertThat(service.peekNextDialog()).isNull()
    }

    @Test
    fun `peekNextDialog returns null when only current dialog is in queue`() {
        service.enqueue(alertDialog)
        assertThat(service.peekNextDialog()).isNull()
    }

    @Test
    fun `peekNextDialog returns next dialog when higher priority dialog is queued`() {
        service.enqueue(alertDialog)          // p=5, shows immediately
        service.enqueue(highPriorityAlert)    // p=1, queued (becomes head of min-heap)

        // peek() = highPriorityAlert (head), which != currentDialog (alertDialog)
        assertThat(service.peekNextDialog()).isEqualTo(highPriorityAlert)
    }

    @Test
    fun `peekNextDialog returns null when no higher-priority dialog is queued`() {
        service.enqueue(highPriorityAlert)    // p=1, shows immediately (and is head)
        service.enqueue(alertDialog)          // p=5, queued

        // peek() = highPriorityAlert (head) == currentDialog → null
        assertThat(service.peekNextDialog()).isNull()
    }

    // -------------------------------------------------------------------------
    // showDialog
    // -------------------------------------------------------------------------

    @Test
    fun `showDialog enqueues Alert with priority 1`() {
        val dialog = DialogModel.Alert(message = "Test", onDismiss = null, alertPriority = 99)
        service.showDialog(dialog)

        assertThat(service.currentDialog.value).isNotNull()
        assertThat(service.currentDialog.value?.priority).isEqualTo(1)
    }

    @Test
    fun `showDialog enqueues Confirm with priority 1`() {
        val dialog = DialogModel.Confirm(message = "Test", confirmPriority = 99)
        service.showDialog(dialog)

        assertThat(service.currentDialog.value?.priority).isEqualTo(1)
    }

    @Test
    fun `showDialog enqueues Custom with priority 1`() {
        val dialog = DialogModel.Custom(
            contentKey = DialogType.HelpPopup,
            customPriority = 99,
        )
        service.showDialog(dialog)

        assertThat(service.currentDialog.value?.priority).isEqualTo(1)
    }

    @Test
    fun `showDialog shows immediately when queue is empty`() {
        val dialog = DialogModel.Alert(message = "Immediate", onDismiss = null)
        service.showDialog(dialog)

        val shown = service.currentDialog.value
        assertThat(shown).isInstanceOf(DialogModel.Alert::class.java)
        assertThat((shown as DialogModel.Alert).message).isEqualTo("Immediate")
    }

    @Test
    fun `showDialog does not preempt currently showing dialog`() {
        service.enqueue(alertDialog) // p=5, showing
        val highPriority = DialogModel.Confirm(message = "Urgent", confirmPriority = 99)
        service.showDialog(highPriority) // updatePriority(1) → p=1, but still queued

        // Current dialog remains the original, not preempted
        assertThat(service.currentDialog.value).isEqualTo(alertDialog)
        assertThat(service.getQueueSize()).isEqualTo(2)
    }

    @Test
    fun `showDialog dialog appears after dismissing current`() = runTest {
        service.enqueue(alertDialog) // p=5, showing
        val urgent = DialogModel.Confirm(message = "Urgent", confirmPriority = 99)
        service.showDialog(urgent) // becomes p=1, queued

        service.dismissCurrent()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertThat(service.currentDialog.value?.priority).isEqualTo(1)
    }

    // -------------------------------------------------------------------------
    // StateFlow emissions (Turbine)
    // -------------------------------------------------------------------------

    @Test
    fun `currentDialog flow emits null then dialog on enqueue`() = runTest {
        service.currentDialog.test {
            assertThat(awaitItem()).isNull()
            service.enqueue(alertDialog)
            assertThat(awaitItem()).isEqualTo(alertDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentToast flow emits null then toast on showToast`() = runTest {
        service.currentToast.test {
            assertThat(awaitItem()).isNull()
            service.showToast(testToast)
            assertThat(awaitItem()).isEqualTo(testToast)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loader flow emits null then loader on showLoader`() = runTest {
        service.loader.test {
            assertThat(awaitItem()).isNull()
            service.showLoader("Loading...", LoaderStyle.CIRCULAR)
            assertThat(awaitItem()).isEqualTo(Loader("Loading...", LoaderStyle.CIRCULAR))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loader flow emits null after dismissLoader`() = runTest {
        service.loader.test {
            assertThat(awaitItem()).isNull()
            service.showLoader("Loading...", LoaderStyle.CIRCULAR)
            assertThat(awaitItem()).isEqualTo(Loader("Loading...", LoaderStyle.CIRCULAR))
            service.dismissLoader()
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentDialog flow emits null after dismissCurrent with single dialog`() = runTest {
        service.currentDialog.test {
            assertThat(awaitItem()).isNull()
            service.enqueue(alertDialog)
            assertThat(awaitItem()).isEqualTo(alertDialog)
            service.dismissCurrent()
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `currentDialog flow emits null after clear`() = runTest {
        service.currentDialog.test {
            assertThat(awaitItem()).isNull()
            service.enqueue(alertDialog)
            assertThat(awaitItem()).isEqualTo(alertDialog)
            service.clear()
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
