package com.dmdbrands.gurus.weight.core.service

import com.dmdbrands.gurus.weight.core.network.interfaces.IConnectivityObserver
import com.dmdbrands.gurus.weight.core.network.utility.NetworkState
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.data.storage.db.entity.account.WeightCompSettingsEntity
import com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountInfo
import com.dmdbrands.gurus.weight.domain.model.api.user.AccountResponse
import com.dmdbrands.gurus.weight.domain.model.api.user.BodyCompUpdateRequest
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.repository.IBodyCompositionRepository
import com.dmdbrands.gurus.weight.domain.services.BodyCompUpdateType
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test

class BodyCompositionServiceTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    // --- Mocks ---
    private val bodyCompositionRepository: IBodyCompositionRepository = mockk()
    private val connectivityObserver: IConnectivityObserver = mockk()
    private val dialogQueueService: IDialogQueueService = mockk(relaxed = true)
    private val appNavigationService: IAppNavigationService = mockk(relaxed = true)

    private lateinit var service: BodyCompositionService

    // --- Test Fixtures ---
    private val fakeAccount = Account(
        id = "acc-1",
        firstName = "Jane",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "jane@example.com",
        gender = "female",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 170,
        activityLevel = "normal",
    )

    private val fakeRequest = BodyCompUpdateRequest(
        height = 175,
        activityLevel = "athlete",
        weightUnit = "KG",
    )

    private val fakeApiAccountInfo = AccountInfo(
        id = "acc-1",
        email = "jane@example.com",
        firstName = "Jane",
        lastName = "Doe",
        gender = "female",
        zipcode = "12345",
        weightUnit = "KG",
        isWeightlessOn = false,
        height = 175,
        activityLevel = "athlete",
        dob = "1990-01-01",
        weightlessTimestamp = null,
        weightlessWeight = null,
        isStreakOn = false,
        dashboardType = "DASHBOARD_4_METRICS",
        dashboardMetrics = emptyList(),
        goalWeight = null,
        initialWeight = null,
        shouldSendEntryNotifications = false,
        shouldSendWeightInEntryNotifications = false,
    )

    private val fakeApiResponse = AccountResponse(
        accessToken = null,
        refreshToken = null,
        expiresAt = null,
        account = fakeApiAccountInfo,
    )

    @BeforeEach
    fun setUp() {
        coEvery { bodyCompositionRepository.getActiveAccountFromDB() } returns fakeAccount
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), any()) } just Runs
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(any()) } returns fakeApiResponse
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = true, unAvailable = false)

        service = BodyCompositionService(
            bodyCompositionRepository,
            connectivityObserver,
            dialogQueueService,
            appNavigationService,
        )
    }

    // -------------------------------------------------------------------------
    // updateBodyComposition — online path
    // -------------------------------------------------------------------------

    @Test
    fun `updateBodyComposition calls API when network is available`() = runTest {
        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        coVerify { bodyCompositionRepository.updateBodyCompInAPI(fakeRequest) }
    }

    @Test
    fun `updateBodyComposition passes correct request to API`() = runTest {
        val requestSlot = slot<BodyCompUpdateRequest>()
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(capture(requestSlot)) } returns fakeApiResponse

        service.updateBodyComposition(BodyCompUpdateType.ACTIVITY_LEVEL, fakeRequest)

        assertThat(requestSlot.captured).isEqualTo(fakeRequest)
    }

    @Test
    fun `updateBodyComposition saves entity with isSynced=true from API response fields when online`() = runTest {
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        assertThat(entitySlot.captured.isSynced).isTrue()
    }

    @Test
    fun `updateBodyComposition maps API response height to entity when online`() = runTest {
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        assertThat(entitySlot.captured.height).isEqualTo(fakeApiAccountInfo.height)
    }

    @Test
    fun `updateBodyComposition maps API response activityLevel to entity when online`() = runTest {
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.ACTIVITY_LEVEL, fakeRequest)

        assertThat(entitySlot.captured.activityLevel).isEqualTo(fakeApiAccountInfo.activityLevel)
    }

    @Test
    fun `updateBodyComposition maps API response weightUnit to entity when online`() = runTest {
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, fakeRequest)

        assertThat(entitySlot.captured.weightUnit).isEqualTo(fakeApiAccountInfo.weightUnit)
    }

    @Test
    fun `updateBodyComposition uses active account id as entity accountId when online`() = runTest {
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        assertThat(entitySlot.captured.accountId).isEqualTo(fakeAccount.id)
    }

    @Test
    fun `updateBodyComposition calls updateBodyCompInDB with active account id when online`() = runTest {
        val accountIdSlot = slot<String>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(capture(accountIdSlot), any()) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        assertThat(accountIdSlot.captured).isEqualTo(fakeAccount.id)
    }

    // -------------------------------------------------------------------------
    // updateBodyComposition — offline path
    // -------------------------------------------------------------------------

    @Test
    fun `updateBodyComposition skips API call when network is unavailable`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInAPI(any()) }
    }

    @Test
    fun `updateBodyComposition saves entity with isSynced=false when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        assertThat(entitySlot.captured.isSynced).isFalse()
    }

    @Test
    fun `updateBodyComposition maps request height to entity when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        assertThat(entitySlot.captured.height).isEqualTo(fakeRequest.height)
    }

    @Test
    fun `updateBodyComposition maps request activityLevel to entity when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.ACTIVITY_LEVEL, fakeRequest)

        assertThat(entitySlot.captured.activityLevel).isEqualTo(fakeRequest.activityLevel)
    }

    @Test
    fun `updateBodyComposition maps request weightUnit to entity when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        val entitySlot = slot<WeightCompSettingsEntity>()
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), capture(entitySlot)) } just Runs

        service.updateBodyComposition(BodyCompUpdateType.WEIGHT_UNIT, fakeRequest)

        assertThat(entitySlot.captured.weightUnit).isEqualTo(fakeRequest.weightUnit)
    }

    @Test
    fun `updateBodyComposition still writes to DB when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        coVerify { bodyCompositionRepository.updateBodyCompInDB(fakeAccount.id, any()) }
    }

    // -------------------------------------------------------------------------
    // updateBodyComposition — error handling
    // -------------------------------------------------------------------------

    @Test
    fun `updateBodyComposition returns without calling API or DB when no active account`() = runTest {
        coEvery { bodyCompositionRepository.getActiveAccountFromDB() } returns null

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInAPI(any()) }
        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInDB(any(), any()) }
    }

    @Test
    fun `updateBodyComposition handles getActiveAccountFromDB exception gracefully`() = runTest {
        coEvery { bodyCompositionRepository.getActiveAccountFromDB() } throws RuntimeException("DB error")

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInAPI(any()) }
        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInDB(any(), any()) }
    }

    @Test
    fun `updateBodyComposition handles API exception gracefully and does not write to DB`() = runTest {
        coEvery { bodyCompositionRepository.updateBodyCompInAPI(any()) } throws RuntimeException("API error")

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        coVerify(exactly = 0) { bodyCompositionRepository.updateBodyCompInDB(any(), any()) }
    }

    @Test
    fun `updateBodyComposition handles DB write exception gracefully when online`() = runTest {
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), any()) } throws RuntimeException("DB write error")

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        // Should not crash — exception is caught
    }

    @Test
    fun `updateBodyComposition handles DB write exception gracefully when offline`() = runTest {
        every { connectivityObserver.getCurrentNetworkState() } returns NetworkState(available = false, unAvailable = true)
        coEvery { bodyCompositionRepository.updateBodyCompInDB(any(), any()) } throws RuntimeException("DB write error")

        service.updateBodyComposition(BodyCompUpdateType.HEIGHT, fakeRequest)

        // Should not crash — exception is caught
    }
}
