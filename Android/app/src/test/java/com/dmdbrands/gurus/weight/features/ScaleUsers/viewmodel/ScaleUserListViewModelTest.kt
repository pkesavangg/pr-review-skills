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
import com.dmdbrands.library.ggbluetooth.enums.GGUserActionResponseType
import com.dmdbrands.library.ggbluetooth.model.GGBTUser
import com.dmdbrands.library.ggbluetooth.model.GGScaleUserResponse
import com.google.common.truth.Truth.assertThat
import com.greatergoods.blewrapper.GGDeviceService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
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
    fun `initial state has null scale and empty user list`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `init subscribes to pairedScales and updates scale when matching id`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `init ignores devices with non-matching scaleId`() = runTest(mainDispatcherRule.scheduler) {
        val otherDevice = TestFixtures.aDevice(id = "other-id")
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(otherDevice))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isNull()
    }

    @Test
    fun `init resets username form with scale display name`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `SetScale updates scale in state`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleUserListIntent.SetScale(device))
        advanceUntilIdle()

        assertThat(viewModel.state.value.scale).isEqualTo(device)
    }

    @Test
    fun `SetScale with hasSetUsername updates flag`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val device = TestFixtures.bleDevice
        viewModel.handleIntent(ScaleUserListIntent.SetScale(device, hasSetUsername = true))
        advanceUntilIdle()

        assertThat(viewModel.state.value.hasSetUsername).isTrue()
    }

    @Test
    fun `SetUserList updates user list and clears loading`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        val users = listOf(mockk<GGBTUser>(relaxed = true))
        viewModel.handleIntent(ScaleUserListIntent.SetUserList(users))
        advanceUntilIdle()

        assertThat(viewModel.state.value.scaleUserList).isEqualTo(users)
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `Save sets isLoading to true`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `Back with no changes navigates back directly`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Back)
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `Back with unsaved username changes shows confirm dialog`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `DeleteUser shows confirmation dialog`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `Save with null scale shows error toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `Save with valid scale and username shows loader`() = runTest(mainDispatcherRule.scheduler) {
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
    fun `RefreshUserList with no scale shows error toast`() = runTest(mainDispatcherRule.scheduler) {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.RefreshUserList)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `RefreshUserList with scale calls ggDeviceService getUsers`() = runTest(mainDispatcherRule.scheduler) {
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

    // -------------------------------------------------------------------------
    // loadScaleUsers — getUsers callback
    // -------------------------------------------------------------------------

    private fun pairedDevice(displayName: String? = TEST_DISPLAY_NAME) =
        TestFixtures.aDevice(id = TEST_SCALE_ID).copy(
            preferences = Preferences(displayName = displayName),
        )

    private fun stubGetUsersCallback(users: List<GGBTUser>) {
        val cb = slot<(GGScaleUserResponse) -> Unit>()
        every { ggDeviceService.getUsers(any(), capture(cb)) } answers {
            cb.captured.invoke(mockk(relaxed = true) { every { user } returns users })
        }
    }

    @Test
    fun `loadScaleUsers filters out current user by display name`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice()))
        val currentUser = mockk<GGBTUser>(relaxed = true) { every { name } returns TEST_DISPLAY_NAME }
        val otherUser = mockk<GGBTUser>(relaxed = true) { every { name } returns "Someone Else" }
        stubGetUsersCallback(listOf(currentUser, otherUser))

        viewModel = createViewModel()
        advanceUntilIdle()

        // Current display-name user is filtered out, leaving only the other user.
        assertThat(viewModel.state.value.scaleUserList).containsExactly(otherUser)
    }

    @Test
    fun `loadScaleUsers keeps all users when display name is null`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice(displayName = null)))
        val users = listOf(
            mockk<GGBTUser>(relaxed = true) { every { name } returns "A" },
            mockk<GGBTUser>(relaxed = true) { every { name } returns "B" },
        )
        stubGetUsersCallback(users)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scaleUserList).hasSize(2)
    }

    @Test
    fun `loadScaleUsers handles getUsers exception by clearing list and showing toast`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice()))
        every { ggDeviceService.getUsers(any(), any()) } throws RuntimeException("ble error")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.scaleUserList).isEmpty()
        verify { dialogQueueService.showToast(any()) }
    }

    // -------------------------------------------------------------------------
    // updateScaleUsername — updateAccount callback
    // -------------------------------------------------------------------------

    private fun stubUpdateAccountCallback(response: GGUserActionResponseType) {
        val cb = slot<(GGUserActionResponseType) -> Unit>()
        every { ggDeviceService.updateAccount(any(), capture(cb)) } answers {
            cb.captured.invoke(response)
        }
    }

    @Test
    fun `Save success updates preferences syncs devices and navigates back`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice()))
        stubGetUsersCallback(emptyList())
        stubUpdateAccountCallback(GGUserActionResponseType.UPDATE_COMPLETED)
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns true

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Save)
        advanceUntilIdle()

        coVerify { deviceService.updateScalePreferences(TEST_SCALE_ID, any()) }
        coVerify { deviceService.syncDevices(any()) }
        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `Save failure on updateScalePreferences shows error toast`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice()))
        stubGetUsersCallback(emptyList())
        stubUpdateAccountCallback(GGUserActionResponseType.CREATION_COMPLETED)
        coEvery { deviceService.updateScalePreferences(any(), any()) } returns false

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `Save with unexpected BLE response shows error toast`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice()))
        stubGetUsersCallback(emptyList())
        stubUpdateAccountCallback(GGUserActionResponseType.EXCEPTION_ENCOUNTERED)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.handleIntent(ScaleUserListIntent.Save)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    // -------------------------------------------------------------------------
    // performDeleteUser — DeleteUser confirm -> deleteAccount callback
    // -------------------------------------------------------------------------

    private fun confirmDeleteFor(user: GGBTUser) {
        val dialogSlot = slot<DialogModel>()
        viewModel.handleIntent(ScaleUserListIntent.DeleteUser(user))
        verify { dialogQueueService.enqueue(capture(dialogSlot)) }
        (dialogSlot.captured as DialogModel.Confirm).onConfirm?.invoke()
    }

    @Test
    fun `performDeleteUser deletes account dismisses loader and reloads users`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice()))
        stubGetUsersCallback(emptyList())
        val cb = slot<(GGUserActionResponseType) -> Unit>()
        every { ggDeviceService.deleteAccount(any(), any(), capture(cb)) } answers {
            cb.captured.invoke(GGUserActionResponseType.UPDATE_COMPLETED)
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        val user = mockk<GGBTUser>(relaxed = true) { every { name } returns "ToDelete" }
        confirmDeleteFor(user)
        advanceUntilIdle()

        verify { ggDeviceService.deleteAccount(any(), any(), any()) }
        verify { dialogQueueService.dismissLoader() }
    }

    @Test
    fun `performDeleteUser with null scale shows error toast`() = runTest {
        // No matching paired scale -> state.scale stays null
        every { deviceService.pairedScales } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val user = mockk<GGBTUser>(relaxed = true) { every { name } returns "ToDelete" }
        confirmDeleteFor(user)
        advanceUntilIdle()

        verify { dialogQueueService.showToast(any()) }
        verify(exactly = 0) { ggDeviceService.deleteAccount(any(), any(), any()) }
    }

    @Test
    fun `performDeleteUser handles deleteAccount exception with error toast`() = runTest {
        every { deviceService.pairedScales } returns MutableStateFlow(listOf(pairedDevice()))
        stubGetUsersCallback(emptyList())
        every { ggDeviceService.deleteAccount(any(), any(), any()) } throws RuntimeException("delete error")

        viewModel = createViewModel()
        advanceUntilIdle()

        val user = mockk<GGBTUser>(relaxed = true) { every { name } returns "ToDelete" }
        confirmDeleteFor(user)
        advanceUntilIdle()

        verify { dialogQueueService.dismissLoader() }
        verify { dialogQueueService.showToast(any()) }
    }
}
