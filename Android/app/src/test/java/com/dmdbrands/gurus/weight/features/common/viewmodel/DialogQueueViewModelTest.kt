package com.dmdbrands.gurus.weight.features.common.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.features.common.components.DialogType
import com.dmdbrands.gurus.weight.features.common.components.LoaderStyle
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.common.model.Loader
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class DialogQueueViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true) lateinit var dialogQueueService: IDialogQueueService

    private val currentDialogFlow = MutableStateFlow<DialogModel?>(null)
    private val currentToastFlow = MutableStateFlow<Toast?>(null)
    private val loaderFlow = MutableStateFlow<Loader?>(null)
    private lateinit var viewModel: DialogQueueViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { dialogQueueService.currentDialog } returns currentDialogFlow
        every { dialogQueueService.currentToast } returns currentToastFlow
        every { dialogQueueService.loader } returns loaderFlow
        viewModel = DialogQueueViewModel(dialogQueueService)
    }

    // -------------------------------------------------------------------------
    // State Flows
    // -------------------------------------------------------------------------

    @Test
    fun `currentDialog exposes dialogQueueService currentDialog flow`() {
        val dialog = DialogModel.Alert(message = "Hello", onDismiss = null)
        currentDialogFlow.value = dialog

        assertThat(viewModel.currentDialog.value).isEqualTo(dialog)
    }

    @Test
    fun `currentToast exposes dialogQueueService currentToast flow`() {
        val toast = Toast.Simple(message = "Toast message")
        currentToastFlow.value = toast

        assertThat(viewModel.currentToast.value).isEqualTo(toast)
    }

    @Test
    fun `loader exposes dialogQueueService loader flow`() {
        val loader = Loader(message = "Loading...")
        loaderFlow.value = loader

        assertThat(viewModel.loader.value).isEqualTo(loader)
    }

    // -------------------------------------------------------------------------
    // enqueueAlert
    // -------------------------------------------------------------------------

    @Test
    fun `enqueueAlert delegates to dialogQueueService enqueue with Alert model`() {
        viewModel.enqueueAlert(
            title = "Title",
            message = "Message",
            dismissText = "OK",
            onDismiss = null,
            priority = 1,
            delayMillis = 0L,
        )

        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Alert &&
                        dialog.title == "Title" &&
                        dialog.message == "Message" &&
                        dialog.dismissText == "OK" &&
                        dialog.alertPriority == 1 &&
                        dialog.alertDelayMillis == 0L
                },
            )
        }
    }

    @Test
    fun `enqueueAlert with custom priority and delay`() {
        viewModel.enqueueAlert(
            title = null,
            message = "High priority",
            dismissText = "Dismiss",
            onDismiss = null,
            priority = 0,
            delayMillis = 500L,
        )

        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Alert &&
                        dialog.title == null &&
                        dialog.alertPriority == 0 &&
                        dialog.alertDelayMillis == 500L
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // enqueueConfirm
    // -------------------------------------------------------------------------

    @Test
    fun `enqueueConfirm delegates to dialogQueueService enqueue with Confirm model`() {
        viewModel.enqueueConfirm(
            title = "Confirm?",
            message = "Are you sure?",
            confirmText = "Yes",
            cancelText = "No",
            onConfirm = null,
            onCancel = null,
            onDismiss = null,
            priority = 2,
            delayMillis = 100L,
        )

        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Confirm &&
                        dialog.title == "Confirm?" &&
                        dialog.message == "Are you sure?" &&
                        dialog.confirmText == "Yes" &&
                        dialog.cancelText == "No" &&
                        dialog.confirmPriority == 2 &&
                        dialog.confirmDelayMillis == 100L
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // enqueueCustomDialog
    // -------------------------------------------------------------------------

    @Test
    fun `enqueueCustomDialog delegates to dialogQueueService enqueue with Custom model`() {
        val params = mapOf("key" to "value")
        viewModel.enqueueCustomDialog(
            contentKey = DialogType.HelpPopup,
            params = params,
            onDismiss = null,
            priority = 3,
            delayMillis = 200L,
        )

        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Custom &&
                        dialog.contentKey == DialogType.HelpPopup &&
                        dialog.params == params &&
                        dialog.customPriority == 3 &&
                        dialog.customDelayMillis == 200L
                },
            )
        }
    }

    @Test
    fun `enqueueCustomDialog with dismissOnBackPress and dismissOnClickOutside`() {
        viewModel.enqueueCustomDialog(
            contentKey = DialogType.HelpPopup,
            params = emptyMap(),
            onDismiss = null,
            priority = 1,
            delayMillis = 0L,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        )

        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Custom &&
                        dialog.dismissOnBackPress &&
                        dialog.dismissOnClickOutside
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // showLoader / dismissLoader
    // -------------------------------------------------------------------------

    @Test
    fun `showLoader delegates to dialogQueueService`() {
        viewModel.showLoader("Loading...", LoaderStyle.CIRCULAR)

        verify { dialogQueueService.showLoader(message = "Loading...", style = LoaderStyle.CIRCULAR) }
    }

    @Test
    fun `showLoader uses DASHED as default style`() {
        viewModel.showLoader("Please wait")

        verify { dialogQueueService.showLoader(message = "Please wait", style = LoaderStyle.DASHED) }
    }

    @Test
    fun `dismissLoader delegates to dialogQueueService`() {
        viewModel.dismissLoader()

        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // enqueueToast
    // -------------------------------------------------------------------------

    @Test
    fun `enqueueToast delegates to dialogQueueService showToast`() {
        viewModel.enqueueToast(
            message = "Success",
            title = "Done",
            action = null,
        )

        verify {
            dialogQueueService.showToast(
                match { toast ->
                    toast is Toast.Simple &&
                        toast.message == "Success" &&
                        toast.title == "Done" &&
                        toast.action == null
                },
            )
        }
    }

    @Test
    fun `enqueueToast with action button`() {
        val action = ActionButton(text = "Undo", action = {})
        viewModel.enqueueToast(
            message = "Deleted",
            title = null,
            action = action,
        )

        verify {
            dialogQueueService.showToast(
                match { toast ->
                    toast is Toast.Simple &&
                        toast.message == "Deleted" &&
                        toast.title == null &&
                        toast.action == action
                },
            )
        }
    }

    // -------------------------------------------------------------------------
    // dismissCurrent
    // -------------------------------------------------------------------------

    @Test
    fun `dismissCurrent with showNext true only dismisses current`() {
        viewModel.dismissCurrent(showNext = true)

        verify { dialogQueueService.dismissCurrent() }
        verify(exactly = 0) { dialogQueueService.clear() }
    }

    @Test
    fun `dismissCurrent with showNext false dismisses and clears queue`() {
        viewModel.dismissCurrent(showNext = false)

        verify { dialogQueueService.dismissCurrent() }
        verify { dialogQueueService.clear() }
    }

    // -------------------------------------------------------------------------
    // dismissToast
    // -------------------------------------------------------------------------

    @Test
    fun `dismissToast delegates to dialogQueueService`() {
        viewModel.dismissToast()

        verify { dialogQueueService.dismissToast() }
    }

    // -------------------------------------------------------------------------
    // clear
    // -------------------------------------------------------------------------

    @Test
    fun `clear delegates to dialogQueueService`() {
        viewModel.clear()

        verify { dialogQueueService.clear() }
    }

    // -------------------------------------------------------------------------
    // hasPendingDialogs
    // -------------------------------------------------------------------------

    @Test
    fun `hasPendingDialogs returns true when queue has items`() {
        every { dialogQueueService.getQueueSize() } returns 2
        currentDialogFlow.value = null

        assertThat(viewModel.hasPendingDialogs()).isTrue()
    }

    @Test
    fun `hasPendingDialogs returns true when currentDialog is not null`() {
        every { dialogQueueService.getQueueSize() } returns 0
        currentDialogFlow.value = DialogModel.Alert(message = "Test", onDismiss = null)

        assertThat(viewModel.hasPendingDialogs()).isTrue()
    }

    @Test
    fun `hasPendingDialogs returns false when queue is empty and no current dialog`() {
        every { dialogQueueService.getQueueSize() } returns 0
        currentDialogFlow.value = null

        assertThat(viewModel.hasPendingDialogs()).isFalse()
    }

    // -------------------------------------------------------------------------
    // peekNextDialog
    // -------------------------------------------------------------------------

    @Test
    fun `peekNextDialog delegates to dialogQueueService`() {
        val nextDialog = DialogModel.Alert(message = "Next", onDismiss = null)
        every { dialogQueueService.peekNextDialog() } returns nextDialog

        assertThat(viewModel.peekNextDialog()).isEqualTo(nextDialog)
    }

    @Test
    fun `peekNextDialog returns null when queue is empty`() {
        every { dialogQueueService.peekNextDialog() } returns null

        assertThat(viewModel.peekNextDialog()).isNull()
    }

    // -------------------------------------------------------------------------
    // getQueueSize
    // -------------------------------------------------------------------------

    @Test
    fun `getQueueSize includes current dialog in count`() {
        every { dialogQueueService.getQueueSize() } returns 2
        currentDialogFlow.value = DialogModel.Alert(message = "Current", onDismiss = null)

        assertThat(viewModel.getQueueSize()).isEqualTo(3)
    }

    @Test
    fun `getQueueSize returns only queue size when no current dialog`() {
        every { dialogQueueService.getQueueSize() } returns 2
        currentDialogFlow.value = null

        assertThat(viewModel.getQueueSize()).isEqualTo(2)
    }

    @Test
    fun `getQueueSize returns zero when empty`() {
        every { dialogQueueService.getQueueSize() } returns 0
        currentDialogFlow.value = null

        assertThat(viewModel.getQueueSize()).isEqualTo(0)
    }

    // -------------------------------------------------------------------------
    // updateCurrentDialogDelay
    // -------------------------------------------------------------------------

    @Test
    fun `updateCurrentDialogDelay re-enqueues Alert with new delay`() {
        val alert = DialogModel.Alert(
            title = "Title",
            message = "Message",
            dismissText = "OK",
            onDismiss = null,
            alertPriority = 1,
            alertDelayMillis = 0L,
        )
        currentDialogFlow.value = alert

        viewModel.updateCurrentDialogDelay(1000L)

        verify { dialogQueueService.dismissCurrent() }
        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Alert &&
                        dialog.alertDelayMillis == 1000L &&
                        dialog.title == "Title"
                },
            )
        }
    }

    @Test
    fun `updateCurrentDialogDelay does nothing when no current dialog`() {
        currentDialogFlow.value = null

        viewModel.updateCurrentDialogDelay(1000L)

        verify(exactly = 0) { dialogQueueService.dismissCurrent() }
    }

    // -------------------------------------------------------------------------
    // updateCurrentDialogPriority
    // -------------------------------------------------------------------------

    @Test
    fun `updateCurrentDialogPriority re-enqueues Alert with new priority`() {
        val alert = DialogModel.Alert(
            title = "Title",
            message = "Message",
            dismissText = "OK",
            onDismiss = null,
            alertPriority = 5,
            alertDelayMillis = 100L,
        )
        currentDialogFlow.value = alert

        viewModel.updateCurrentDialogPriority(0)

        verify { dialogQueueService.dismissCurrent() }
        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Alert &&
                        dialog.alertPriority == 0 &&
                        dialog.alertDelayMillis == 100L
                },
            )
        }
    }

    @Test
    fun `updateCurrentDialogPriority re-enqueues Confirm with new priority`() {
        val confirm = DialogModel.Confirm(
            title = "Confirm",
            message = "Sure?",
            confirmText = "Yes",
            cancelText = "No",
            onConfirm = null,
            onCancel = null,
            onDismiss = null,
            confirmPriority = 3,
            confirmDelayMillis = 50L,
        )
        currentDialogFlow.value = confirm

        viewModel.updateCurrentDialogPriority(1)

        verify { dialogQueueService.dismissCurrent() }
        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Confirm &&
                        dialog.confirmPriority == 1 &&
                        dialog.confirmDelayMillis == 50L
                },
            )
        }
    }

    @Test
    fun `updateCurrentDialogPriority re-enqueues Custom with new priority`() {
        val custom = DialogModel.Custom(
            contentKey = DialogType.HelpPopup,
            params = mapOf("foo" to "bar"),
            onDismiss = null,
            customPriority = 5,
            customDelayMillis = 0L,
        )
        currentDialogFlow.value = custom

        viewModel.updateCurrentDialogPriority(2)

        verify { dialogQueueService.dismissCurrent() }
        verify {
            dialogQueueService.enqueue(
                match { dialog ->
                    dialog is DialogModel.Custom &&
                        dialog.customPriority == 2 &&
                        dialog.contentKey == DialogType.HelpPopup
                },
            )
        }
    }

    @Test
    fun `updateCurrentDialogPriority does nothing when no current dialog`() {
        currentDialogFlow.value = null

        viewModel.updateCurrentDialogPriority(0)

        verify(exactly = 0) { dialogQueueService.dismissCurrent() }
    }
}
