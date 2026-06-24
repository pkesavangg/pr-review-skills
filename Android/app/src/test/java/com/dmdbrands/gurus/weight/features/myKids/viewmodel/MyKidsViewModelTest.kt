package com.dmdbrands.gurus.weight.features.myKids.viewmodel

import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.domain.repository.IAccountRepository
import com.dmdbrands.gurus.weight.domain.services.IBabyProfileService
import com.dmdbrands.gurus.weight.features.common.viewmodel.BaseViewModel
import com.dmdbrands.gurus.weight.testutil.initTestDependencies
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class MyKidsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var babyProfileService: IBabyProfileService

    @MockK(relaxUnitFun = true)
    lateinit var accountRepository: IAccountRepository

    private lateinit var navigationService: IAppNavigationService
    private lateinit var dialogQueueService: IDialogQueueService
    private lateinit var viewModel: MyKidsViewModel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        navigationService = mockk(relaxed = true)
        dialogQueueService = mockk(relaxed = true)
        stubDefaults()
        viewModel = createViewModel()
    }

    private fun stubDefaults() {
        every { babyProfileService.observeAll() } returns flowOf(emptyList())
        coEvery { accountRepository.getActiveBabyId() } returns null
        coEvery { accountRepository.getActiveAccount() } returns flowOf(null)
    }

    private fun createViewModel(): MyKidsViewModel {
        val vm = MyKidsViewModel(
            babyProfileService = babyProfileService,
            accountRepository = accountRepository,
        ).initTestDependencies(
            navigationService = navigationService,
            dialogQueueService = dialogQueueService,
        )
        // initTestDependencies sets fields but doesn't call onDependenciesReady();
        // invoke it here to mirror what Hilt's @Inject fun injectBaseDependencies does.
        var clazz: Class<*>? = BaseViewModel::class.java
        while (clazz != null) {
            try {
                val method = clazz.getDeclaredMethod("onDependenciesReady")
                method.isAccessible = true
                method.invoke(vm)
                break
            } catch (_: NoSuchMethodException) {
                clazz = clazz.superclass
            }
        }
        return vm
    }

    private fun aBabyProfile(
        id: String = "baby-1",
        name: String = "Alice",
        accountId: String = "account-1",
    ) = BabyProfile(id = id, accountId = accountId, name = name)

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty babies`() {
        assertThat(viewModel.state.value.babies).isEmpty()
    }

    @Test
    fun `initial state has null activeBabyId`() {
        assertThat(viewModel.state.value.activeBabyId).isNull()
    }

    @Test
    fun `initial state has isLoading false`() {
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    // -------------------------------------------------------------------------
    // onDependenciesReady — observeBabies + loadActiveBabyId
    // -------------------------------------------------------------------------

    @Test
    fun `init observes babies and sets state`() = runTest {
        val babies = listOf(aBabyProfile("baby-1"), aBabyProfile("baby-2"))
        every { babyProfileService.observeAll() } returns flowOf(babies)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.babies).containsExactlyElementsIn(babies).inOrder()
    }

    @Test
    fun `init loads activeBabyId and sets state`() = runTest {
        coEvery { accountRepository.getActiveBabyId() } returns "baby-1"

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.activeBabyId).isEqualTo("baby-1")
    }

    @Test
    fun `init with null activeBabyId keeps activeBabyId null`() = runTest {
        coEvery { accountRepository.getActiveBabyId() } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.activeBabyId).isNull()
    }

    // -------------------------------------------------------------------------
    // SetBabies
    // -------------------------------------------------------------------------

    @Test
    fun `SetBabies updates babies in state`() = runTest {
        val babies = listOf(aBabyProfile()).toImmutableList()
        viewModel.handleIntent(MyKidsIntent.SetBabies(babies))
        assertThat(viewModel.state.value.babies).isEqualTo(babies)
    }

    // -------------------------------------------------------------------------
    // SaveBaby — success path
    // -------------------------------------------------------------------------

    @Test
    fun `SaveBaby calls babyProfileService save`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        coEvery { accountRepository.getActiveBabyId() } returns null

        viewModel.handleIntent(
            MyKidsIntent.SaveBaby(
                name = "Alice",
                birthdayMillis = 1000L,
                biologicalSex = "female",
                birthLengthMillimeters = 500,
                birthWeightDecigrams = 3500,
            )
        )
        advanceUntilIdle()

        coVerify { babyProfileService.save(any()) }
    }

    @Test
    fun `SaveBaby navigates back on success`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        coEvery { accountRepository.getActiveBabyId() } returns null

        viewModel.handleIntent(
            MyKidsIntent.SaveBaby(
                name = "Bob",
                birthdayMillis = 2000L,
                biologicalSex = null,
                birthLengthMillimeters = null,
                birthWeightDecigrams = null,
            )
        )
        advanceUntilIdle()

        coVerify { navigationService.navigateBack() }
    }

    @Test
    fun `SaveBaby with null active account does not call save`() = runTest {
        coEvery { accountRepository.getActiveAccount() } returns flowOf(null)

        viewModel.handleIntent(
            MyKidsIntent.SaveBaby(
                name = "Alice",
                birthdayMillis = 1000L,
                biologicalSex = null,
                birthLengthMillimeters = null,
                birthWeightDecigrams = null,
            )
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { babyProfileService.save(any()) }
        coVerify(exactly = 0) { navigationService.navigateBack() }
    }

    @Test
    fun `SaveBaby sets activeBabyId when none exists`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        coEvery { accountRepository.getActiveBabyId() } returns null

        viewModel.handleIntent(
            MyKidsIntent.SaveBaby(
                name = "Alice",
                birthdayMillis = 1000L,
                biologicalSex = null,
                birthLengthMillimeters = null,
                birthWeightDecigrams = null,
            )
        )
        advanceUntilIdle()

        coVerify { accountRepository.setActiveBabyId(account.id, any()) }
    }

    @Test
    fun `SaveBaby does not overwrite existing activeBabyId`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        coEvery { accountRepository.getActiveBabyId() } returns "existing-baby"

        viewModel.handleIntent(
            MyKidsIntent.SaveBaby(
                name = "Alice",
                birthdayMillis = 1000L,
                biologicalSex = null,
                birthLengthMillimeters = null,
                birthWeightDecigrams = null,
            )
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { accountRepository.setActiveBabyId(any(), any()) }
    }

    @Test
    fun `SaveBaby clears isLoading on exception`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        coEvery { accountRepository.getActiveBabyId() } returns null
        coEvery { babyProfileService.save(any()) } throws RuntimeException("DB error")

        viewModel.handleIntent(
            MyKidsIntent.SaveBaby(
                name = "Alice",
                birthdayMillis = 1000L,
                biologicalSex = null,
                birthLengthMillimeters = null,
                birthWeightDecigrams = null,
            )
        )
        advanceUntilIdle()

        assertThat(viewModel.state.value.isLoading).isFalse()
        coVerify(exactly = 0) { navigationService.navigateBack() }
    }

    // -------------------------------------------------------------------------
    // DeleteBaby
    // -------------------------------------------------------------------------

    @Test
    fun `DeleteBaby calls babyProfileService delete`() = runTest {
        viewModel.handleIntent(MyKidsIntent.DeleteBaby("baby-1"))
        advanceUntilIdle()

        coVerify { babyProfileService.delete("baby-1") }
    }

    @Test
    fun `DeleteBaby of active baby clears activeBabyId when no remaining babies`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        viewModel.handleIntent(MyKidsIntent.SetActiveBabyId("baby-1"))

        viewModel.handleIntent(MyKidsIntent.DeleteBaby("baby-1"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.activeBabyId).isNull()
    }

    @Test
    fun `DeleteBaby of active baby sets next baby as active`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        val baby1 = aBabyProfile("baby-1")
        val baby2 = aBabyProfile("baby-2")
        viewModel.handleIntent(MyKidsIntent.SetBabies(listOf(baby1, baby2).toImmutableList()))
        viewModel.handleIntent(MyKidsIntent.SetActiveBabyId("baby-1"))

        viewModel.handleIntent(MyKidsIntent.DeleteBaby("baby-1"))
        advanceUntilIdle()

        coVerify { accountRepository.setActiveBabyId(account.id, "baby-2") }
    }

    @Test
    fun `DeleteBaby of active baby clears activeBabyId in db when no remaining babies`() = runTest {
        val account = com.dmdbrands.gurus.weight.testutil.TestFixtures.activeAccount
        coEvery { accountRepository.getActiveAccount() } returns flowOf(account)
        viewModel.handleIntent(MyKidsIntent.SetActiveBabyId("baby-1"))

        viewModel.handleIntent(MyKidsIntent.DeleteBaby("baby-1"))
        advanceUntilIdle()

        coVerify { accountRepository.clearActiveBabyId(account.id) }
    }

    @Test
    fun `DeleteBaby of non-active baby does not change activeBabyId`() = runTest {
        viewModel.handleIntent(MyKidsIntent.SetActiveBabyId("baby-1"))

        viewModel.handleIntent(MyKidsIntent.DeleteBaby("baby-2"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.activeBabyId).isEqualTo("baby-1")
    }

    @Test
    fun `DeleteBaby does not crash on exception`() = runTest {
        coEvery { babyProfileService.delete(any()) } throws RuntimeException("DB error")

        viewModel.handleIntent(MyKidsIntent.DeleteBaby("baby-1"))
        advanceUntilIdle()

        // Should not crash — state stays intact
        assertThat(viewModel.state.value.isLoading).isFalse()
    }
}
