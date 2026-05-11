package com.dmdbrands.gurus.weight.features.common.viewmodel

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.core.service.IAppNavigationService
import com.dmdbrands.gurus.weight.domain.interfaces.NavigationIntent
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var appEventService: IAppNavigationService

    private lateinit var viewModel: NavigationViewmodel

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { appEventService.navigationIntent } returns MutableSharedFlow()
        viewModel = NavigationViewmodel(appEventService)
    }

    // -------------------------------------------------------------------------
    // navigationState
    // -------------------------------------------------------------------------

    @Test
    fun `navigationState exposes appEventService navigationIntent`() {
        val flow = mockk<MutableSharedFlow<NavigationIntent>>()
        every { appEventService.navigationIntent } returns flow

        val vm = NavigationViewmodel(appEventService)
        assertThat(vm.navigationState).isEqualTo(flow)
    }

    @Test
    fun `navigationState is not null when appEventService provides flow`() {
        val flow = MutableSharedFlow<NavigationIntent>()
        every { appEventService.navigationIntent } returns flow

        val vm = NavigationViewmodel(appEventService)
        assertThat(vm.navigationState).isNotNull()
    }

    // -------------------------------------------------------------------------
    // navigateTo
    // -------------------------------------------------------------------------

    @Test
    fun `navigateTo calls appEventService navigateTo with route`() = runTest {
      val route = AppRoute.AccountSettings.MyDevices
        viewModel.navigateTo(route)
        advanceUntilIdle()

        coVerify { appEventService.navigateTo(route, null, null) }
    }

    @Test
    fun `navigateTo passes topLevel parameter`() = runTest {
      val route = AppRoute.AccountSettings.MyDevices
        val topLevel = AppRoute.Main.Dashboard

        viewModel.navigateTo(route, topLevel = topLevel)
        advanceUntilIdle()

        coVerify { appEventService.navigateTo(route, topLevel, null) }
    }

    @Test
    fun `navigateTo passes popUpTo parameter`() = runTest {
      val route = AppRoute.AccountSettings.MyDevices
        val popUpTo = AppRoute.Main.Dashboard

        viewModel.navigateTo(route, popUpTo = popUpTo)
        advanceUntilIdle()

        coVerify { appEventService.navigateTo(route, null, popUpTo) }
    }

    @Test
    fun `navigateTo passes all parameters`() = runTest {
      val route = AppRoute.AccountSettings.MyDevices
        val topLevel = AppRoute.Main.Dashboard
        val popUpTo = AppRoute.Integration.IntegrationList

        viewModel.navigateTo(route, topLevel = topLevel, popUpTo = popUpTo)
        advanceUntilIdle()

        coVerify { appEventService.navigateTo(route, topLevel, popUpTo) }
    }

    // -------------------------------------------------------------------------
    // navigateBack
    // -------------------------------------------------------------------------

    @Test
    fun `navigateBack calls appEventService navigateBack with topLevel`() = runTest {
        val topLevel = AppRoute.Main.Dashboard
        viewModel.navigateBack(topLevel)
        advanceUntilIdle()

        coVerify { appEventService.navigateBack(topLevel) }
    }

    @Test
    fun `navigateBack with null calls appEventService navigateBack with null`() = runTest {
        viewModel.navigateBack(null)
        advanceUntilIdle()

        coVerify { appEventService.navigateBack(null) }
    }
}
