package com.dmdbrands.gurus.weight.features.ScaleUsers.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.domain.model.storage.Preferences
import com.dmdbrands.gurus.weight.domain.repository.IDeviceService
import com.dmdbrands.gurus.weight.features.ScaleUsers.reducer.ScaleUserListIntent
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.testutil.TestFixtures
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleUserListViewModelTest {

    companion object {
        private const val TEST_SCALE_ID = "test-scale-id"
        private const val TEST_DISPLAY_NAME = "TestUser"
    }

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @MockK(relaxed = true) lateinit var ggDeviceService: GGDeviceService
    @MockK(relaxed = true) lateinit var deviceService: IDeviceService

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: ScaleUserListViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaultFlows()
    }

    private fun stubDefaultFlows() {
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())
    }

    private fun createViewModel(): ScaleUserListViewModel =
        ScaleUserListViewModel(
            ggDeviceService = ggDeviceService,
            deviceService = deviceService,
            scaleId = TEST_SCALE_ID,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )

    // -------------------------------------------------------------------------
    // Default State
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has null scale and empty user list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.scale).isNull()
        assertThat(state.scaleUserList).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasSetUsername).isFalse()
    }

    // -------------------------------------------------------------------------
    // Init — flow subscriptions
    // -------------------------------------------------------------------------

    @Test
    fun `init subscribes to pairedScales and updates scale when matching id`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayName = TEST_DISPLAY_NAME),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
        assertThat(viewModel.state.value.hasSetUsername).isTrue()
    }

    @Test
    fun `init ignores devices with non-matching scaleId`() = runTest {
        val otherDevice = TestFixtures.aDevice(id = "other-id")
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(otherDevice))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isNull()
    }

    @Test
    fun `init resets username form with scale display name`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayName = TEST_DISPLAY_NAME),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.usernameForm.username.value).isEqualTo(TEST_DISPLAY_NAME)
    }

    // -------------------------------------------------------------------------
    // Pure State Intents
    // -------------------------------------------------------------------------

    @Test
    fun `SetScale updates scale in state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleUserListIntent.SetScale(device))
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
    }

    @Test
    fun `SetScale with hasSetUsername updates flag`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleUserListIntent.SetScale(device, hasSetUsername = true))
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasSetUsername).isTrue()
    }

    @Test
    fun `SetUserList updates user list and clears loading`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val users = listOf(mockk<GGBTUser>(relaxed = true))
        viewModel.handleIntent(ScaleUserListIntent.SetUserList(users))
        advanceUntilIdle()

        assertThat(viewModel.state.value.scaleUserList).isEqualTo(users)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `Save sets isLoading to true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Save)
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // Back — navigation
    // -------------------------------------------------------------------------

    @Test
    fun `Back with no changes navigates back directly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Back)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `Back with unsaved username changes shows confirm dialog`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayName = "OriginalName"),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Change username to something different
        viewModel.state.value.usernameForm.username.onValueChange("ChangedName")

        viewModel.handleIntent(ScaleUserListIntent.Back)
        advanceUntilIdle()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // DeleteUser — shows confirmation dialog
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteUser shows confirmation dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val user = mockk<GGBTUser>(relaxed = true) {
            every { name } returns "OtherUser"
        }
        viewModel.handleIntent(ScaleUserListIntent.DeleteUser(user))
        advanceUntilIdle()

        verify { dialogQueueService.enqueue(any<DialogModel.Confirm>()) }
    }

    // -------------------------------------------------------------------------
    // Save — updateScaleUsername
    // -------------------------------------------------------------------------

    @Test
    fun `Save with null scale shows error toast`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `Save with valid scale and username shows loader`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayName = TEST_DISPLAY_NAME),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showLoader(message = any()) }
    }

    // -------------------------------------------------------------------------
    // RefreshUserList
    // -------------------------------------------------------------------------

    @Test
    fun `RefreshUserList with no scale shows error toast`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.RefreshUserList)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `RefreshUserList with scale calls ggDeviceService getUsers`() = runTest {
        val device = TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayName = TEST_DISPLAY_NAME),
        )
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(device))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.RefreshUserList)
        advanceUntilIdle()

        verify { ggDeviceService.getUsers(any(), any()) }
    }
}
