package com.dmdbrands.gurus.weight.core.service

import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.entry.ScaleApiEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.services.IAccountService
import com.dmdbrands.gurus.weight.domain.services.IEntryService
import com.dmdbrands.gurus.weight.features.common.model.Toast
import com.dmdbrands.gurus.weight.features.manualEntry.strings.EntryScreenStrings
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSyncServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val entryService: IEntryService = mockk()
    private val accountService: IAccountService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)

    private lateinit var service: AppSyncService

    // --- Test fixtures ---
    private val fakeScaleEntry: ScaleEntry = mockk(relaxed = true)
    private val fakeApiEntry: ScaleApiEntry = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(AppLog)
        service = AppSyncService(
            entryService = entryService,
            accountService = accountService,
            appNavigationService = appNavigationService,
            dialogQueueService = dialogQueueService,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(AppLog)
    }

    // -------------------------------------------------------------------------
    // appSyncDataForEditing — initial state is null
    // -------------------------------------------------------------------------

    @Test
    fun `appSyncDataForEditing initial value is null`() {
        assertThat(service.appSyncDataForEditing.value).isNull()
    }

    // -------------------------------------------------------------------------
    // appSyncData — initial state is null
    // -------------------------------------------------------------------------

    @Test
    fun `appSyncData initial value is null`() {
        assertThat(service.appSyncData.value).isNull()
    }

    // -------------------------------------------------------------------------
    // setAppSyncDataForEditing — updates appSyncDataForEditing StateFlow
    // -------------------------------------------------------------------------

    @Test
    fun `setAppSyncDataForEditing updates state with ScaleEntry`() = runTest {
        service.setAppSyncDataForEditing(fakeScaleEntry)
        assertThat(service.appSyncDataForEditing.value).isEqualTo(fakeScaleEntry)
    }

    @Test
    fun `setAppSyncDataForEditing with null clears state`() = runTest {
        service.setAppSyncDataForEditing(fakeScaleEntry)
        service.setAppSyncDataForEditing(null)
        assertThat(service.appSyncDataForEditing.value).isNull()
    }

    @Test
    fun `setAppSyncDataForEditing emits values via StateFlow`() = runTest {
        service.appSyncDataForEditing.test {
            assertThat(awaitItem()).isNull()
            service.setAppSyncDataForEditing(fakeScaleEntry)
            assertThat(awaitItem()).isEqualTo(fakeScaleEntry)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // setAppSyncData — updates appSyncData StateFlow
    // -------------------------------------------------------------------------

    @Test
    fun `setAppSyncData updates state with ScaleApiEntry`() = runTest {
        service.setAppSyncData(fakeApiEntry)
        assertThat(service.appSyncData.value).isEqualTo(fakeApiEntry)
    }

    @Test
    fun `setAppSyncData with null clears state`() = runTest {
        service.setAppSyncData(fakeApiEntry)
        service.setAppSyncData(null)
        assertThat(service.appSyncData.value).isNull()
    }

    @Test
    fun `setAppSyncData emits values via StateFlow`() = runTest {
        service.appSyncData.test {
            assertThat(awaitItem()).isNull()
            service.setAppSyncData(fakeApiEntry)
            assertThat(awaitItem()).isEqualTo(fakeApiEntry)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // handleEditAppSyncData — sets editing data and navigates to Entry
    // -------------------------------------------------------------------------

    @Test
    fun `handleEditAppSyncData sets editing data`() = runTest {
        service.handleEditAppSyncData(fakeScaleEntry)
        assertThat(service.appSyncDataForEditing.value).isEqualTo(fakeScaleEntry)
    }

    @Test
    fun `handleEditAppSyncData navigates to Entry with Home topLevel`() = runTest {
        service.handleEditAppSyncData(fakeScaleEntry)
        coVerify { appNavigationService.navigateTo(AppRoute.Main.Entry, AppRoute.Home) }
    }

    @Test
    fun `handleEditAppSyncData does not show toast on success`() = runTest {
        service.handleEditAppSyncData(fakeScaleEntry)
        coVerify(exactly = 0) { dialogQueueService.showToast(any()) }
    }

    @Test
    fun `handleEditAppSyncData on navigation exception shows failure toast`() = runTest {
        coEvery {
            appNavigationService.navigateTo(any<AppRoute>(), any<AppRoute>())
        } throws RuntimeException("nav error")

        service.handleEditAppSyncData(fakeScaleEntry)

        val toastSlot = slot<Toast>()
        coVerify { dialogQueueService.showToast(capture(toastSlot)) }
        assertThat(toastSlot.captured.message).contains("Failed to navigate")
        assertThat(toastSlot.captured.message).contains("nav error")
    }

    @Test
    fun `handleEditAppSyncData on exception still sets editing data before navigation`() = runTest {
        coEvery {
            appNavigationService.navigateTo(any<AppRoute>(), any<AppRoute>())
        } throws RuntimeException("fail")

        service.handleEditAppSyncData(fakeScaleEntry)

        assertThat(service.appSyncDataForEditing.value).isEqualTo(fakeScaleEntry)
    }

    // -------------------------------------------------------------------------
    // handleSaveAppSyncData — saves entry, shows toast, clears editing data
    // -------------------------------------------------------------------------

    @Test
    fun `handleSaveAppSyncData calls entryService addEntry`() = runTest {
        coEvery { entryService.addEntry(any<ScaleEntry>()) } just Runs

        service.handleSaveAppSyncData(fakeScaleEntry)

        coVerify { entryService.addEntry(fakeScaleEntry) }
    }

    @Test
    fun `handleSaveAppSyncData shows success toast with correct title and message`() = runTest {
        coEvery { entryService.addEntry(any<ScaleEntry>()) } just Runs

        service.handleSaveAppSyncData(fakeScaleEntry)

        val toastSlot = slot<Toast>()
        coVerify { dialogQueueService.showToast(capture(toastSlot)) }
        assertThat(toastSlot.captured.title).isEqualTo(EntryScreenStrings.EntryAddedTitle)
        assertThat(toastSlot.captured.message).isEqualTo(EntryScreenStrings.EntryAdded)
    }

    @Test
    fun `handleSaveAppSyncData clears editing data on success`() = runTest {
        coEvery { entryService.addEntry(any<ScaleEntry>()) } just Runs

        service.setAppSyncDataForEditing(fakeScaleEntry)
        assertThat(service.appSyncDataForEditing.value).isNotNull()

        service.handleSaveAppSyncData(fakeScaleEntry)

        assertThat(service.appSyncDataForEditing.value).isNull()
    }

    @Test
    fun `handleSaveAppSyncData on addEntry exception shows failure toast with error message`() = runTest {
        coEvery { entryService.addEntry(any<ScaleEntry>()) } throws RuntimeException("save error")

        service.handleSaveAppSyncData(fakeScaleEntry)

        val toastSlot = slot<Toast>()
        coVerify { dialogQueueService.showToast(capture(toastSlot)) }
        assertThat(toastSlot.captured.message).contains("Failed to save entry")
        assertThat(toastSlot.captured.message).contains("save error")
    }

    @Test
    fun `handleSaveAppSyncData on addEntry exception does not clear editing data`() = runTest {
        coEvery { entryService.addEntry(any<ScaleEntry>()) } throws RuntimeException("fail")

        service.setAppSyncDataForEditing(fakeScaleEntry)
        service.handleSaveAppSyncData(fakeScaleEntry)

        assertThat(service.appSyncDataForEditing.value).isEqualTo(fakeScaleEntry)
    }
}
