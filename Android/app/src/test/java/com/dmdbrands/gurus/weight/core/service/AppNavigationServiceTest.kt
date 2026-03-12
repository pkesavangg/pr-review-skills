package com.dmdbrands.gurus.weight.core.service

import androidx.navigation3.runtime.NavKey
import app.cash.turbine.test
import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.core.rules.MainDispatcherRule
import com.dmdbrands.gurus.weight.domain.interfaces.NavigationIntent
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.dmdbrands.gurus.weight.domain.services.AuthState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var service: AppNavigationService

    // --- Test fixtures ---
    private val fakeAccount = Account(
        id = "account-1",
        firstName = "Test",
        lastName = "User",
        dob = "1990-01-01",
        email = "test@example.com",
        gender = "male",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 70,
        activityLevel = "moderate",
    )

    @Before
    fun setUp() {
        service = AppNavigationService()
    }

    // -------------------------------------------------------------------------
    // navigateTo — emits NavigateTo intent with correct parameters
    // -------------------------------------------------------------------------

    @Test
    fun `navigateTo emits NavigateTo with correct route`() = runTest {
        service.navigationIntent.test {
            service.navigateTo(AppRoute.Main.Dashboard)
            val intent = awaitItem() as NavigationIntent.NavigateTo
            assertThat(intent.route).isEqualTo(AppRoute.Main.Dashboard)
            assertThat(intent.topLevel).isNull()
            assertThat(intent.popUpTo).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateTo emits NavigateTo with topLevel and popUpTo`() = runTest {
        service.navigationIntent.test {
            service.navigateTo(
                route = AppRoute.Main.Settings,
                topLevel = AppRoute.Main.Dashboard,
                popUpTo = AppRoute.Home,
            )
            val intent = awaitItem() as NavigationIntent.NavigateTo
            assertThat(intent.route).isEqualTo(AppRoute.Main.Settings)
            assertThat(intent.topLevel).isEqualTo(AppRoute.Main.Dashboard)
            assertThat(intent.popUpTo).isEqualTo(AppRoute.Home)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // navigateBack — emits NavigateBack intent
    // -------------------------------------------------------------------------

    @Test
    fun `navigateBack emits NavigateBack with null topLevel`() = runTest {
        service.navigationIntent.test {
            service.navigateBack()
            val intent = awaitItem() as NavigationIntent.NavigateBack
            assertThat(intent.topLevel).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigateBack emits NavigateBack with topLevel`() = runTest {
        service.navigationIntent.test {
            service.navigateBack(topLevel = AppRoute.Main.Dashboard)
            val intent = awaitItem() as NavigationIntent.NavigateBack
            assertThat(intent.topLevel).isEqualTo(AppRoute.Main.Dashboard)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // replaceLastAndNavigate — emits ReplaceLastAndNavigate intent
    // -------------------------------------------------------------------------

    @Test
    fun `replaceLastAndNavigate emits correct intent without topLevel`() = runTest {
        service.navigationIntent.test {
            service.replaceLastAndNavigate(AppRoute.Main.Entry)
            val intent = awaitItem() as NavigationIntent.ReplaceLastAndNavigate
            assertThat(intent.route).isEqualTo(AppRoute.Main.Entry)
            assertThat(intent.topLevel).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceLastAndNavigate emits correct intent with topLevel`() = runTest {
        service.navigationIntent.test {
            service.replaceLastAndNavigate(
                route = AppRoute.Main.Entry,
                topLevel = AppRoute.Main.Dashboard,
            )
            val intent = awaitItem() as NavigationIntent.ReplaceLastAndNavigate
            assertThat(intent.route).isEqualTo(AppRoute.Main.Entry)
            assertThat(intent.topLevel).isEqualTo(AppRoute.Main.Dashboard)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // replaceStack (single route) — emits ReplaceStack with listOf(route)
    // -------------------------------------------------------------------------

    @Test
    fun `replaceStack single route emits ReplaceStack with single-element list`() = runTest {
        service.navigationIntent.test {
            service.replaceStack(AppRoute.Main.History)
            val intent = awaitItem() as NavigationIntent.ReplaceStack
            assertThat(intent.route).containsExactly(AppRoute.Main.History)
            assertThat(intent.topLevel).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceStack single route emits ReplaceStack with topLevel`() = runTest {
        service.navigationIntent.test {
            service.replaceStack(
                route = AppRoute.Main.Settings,
                topLevel = AppRoute.Home,
            )
            val intent = awaitItem() as NavigationIntent.ReplaceStack
            assertThat(intent.route).containsExactly(AppRoute.Main.Settings)
            assertThat(intent.topLevel).isEqualTo(AppRoute.Home)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // replaceStack (list of routes) — emits ReplaceStack with full route list
    // -------------------------------------------------------------------------

    @Test
    fun `replaceStack route list emits ReplaceStack with all routes`() = runTest {
        val routes = listOf(AppRoute.Main.Dashboard, AppRoute.Main.Entry, AppRoute.Main.History)
        service.navigationIntent.test {
            service.replaceStack(routes)
            val intent = awaitItem() as NavigationIntent.ReplaceStack
            assertThat(intent.route).isEqualTo(routes)
            assertThat(intent.topLevel).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replaceStack route list emits ReplaceStack with topLevel`() = runTest {
        val routes = listOf(AppRoute.Main.Dashboard, AppRoute.Main.Settings)
        service.navigationIntent.test {
            service.replaceStack(routes, topLevel = AppRoute.Home)
            val intent = awaitItem() as NavigationIntent.ReplaceStack
            assertThat(intent.route).isEqualTo(routes)
            assertThat(intent.topLevel).isEqualTo(AppRoute.Home)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // login — emits Login intent
    // -------------------------------------------------------------------------

    @Test
    fun `login emits Login intent`() = runTest {
        service.navigationIntent.test {
            service.login()
            val intent = awaitItem()
            assertThat(intent).isEqualTo(NavigationIntent.Login)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // autoLogin — emits AutoLogin intent
    // -------------------------------------------------------------------------

    @Test
    fun `autoLogin emits AutoLogin intent`() = runTest {
        service.navigationIntent.test {
            service.autoLogin()
            val intent = awaitItem()
            assertThat(intent).isEqualTo(NavigationIntent.AutoLogin)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // reInitialize — emits ReInitialize intent
    // -------------------------------------------------------------------------

    @Test
    fun `reInitialize emits ReInitialize intent`() = runTest {
        service.navigationIntent.test {
            service.reInitialize()
            val intent = awaitItem()
            assertThat(intent).isEqualTo(NavigationIntent.ReInitialize)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // registerOnDeactivate — emits RegisterOnDeactivate with route and callback
    // -------------------------------------------------------------------------

    @Test
    fun `registerOnDeactivate emits RegisterOnDeactivate with correct route`() = runTest {
        val route = AppRoute.Main.Entry
        val callback: suspend () -> Boolean = { true }
        service.navigationIntent.test {
            service.registerOnDeactivate(route, callback)
            val intent = awaitItem() as NavigationIntent.RegisterOnDeactivate
            assertThat(intent.route).isEqualTo(route)
            assertThat(intent.callback).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `registerOnDeactivate callback is the same instance passed in`() = runTest {
        val route = AppRoute.Main.Settings
        val callback: suspend () -> Boolean = { false }
        service.navigationIntent.test {
            service.registerOnDeactivate(route, callback)
            val intent = awaitItem() as NavigationIntent.RegisterOnDeactivate
            assertThat(intent.callback).isSameInstanceAs(callback)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // unregisterOnDeactivate — emits UnregisterOnDeactivate with route
    // -------------------------------------------------------------------------

    @Test
    fun `unregisterOnDeactivate emits UnregisterOnDeactivate with correct route`() = runTest {
        val route = AppRoute.Main.Dashboard
        service.navigationIntent.test {
            service.unregisterOnDeactivate(route)
            val intent = awaitItem() as NavigationIntent.UnregisterOnDeactivate
            assertThat(intent.route).isEqualTo(route)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // getCurrentRoute — uses CompletableDeferred to return current NavKey
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentRoute returns NavKey when deferred is completed`() = runTest {
        val expectedRoute = AppRoute.Main.Dashboard
        var result: NavKey? = null

        service.navigationIntent.test {
            val job = launch { result = service.getCurrentRoute() }
            val intent = awaitItem() as NavigationIntent.GetCurrentRoute
            intent.response.complete(expectedRoute)
            job.join()
            assertThat(result).isEqualTo(expectedRoute)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCurrentRoute returns null when deferred is completed with null`() = runTest {
        var result: Any? = "sentinel"
        service.navigationIntent.test {
            val job = launch { result = service.getCurrentRoute() }
            val intent = awaitItem() as NavigationIntent.GetCurrentRoute
            intent.response.complete(null)
            job.join()
            assertThat(result).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getCurrentRoute emits GetCurrentRoute intent`() = runTest {
        service.navigationIntent.test {
            val job = launch { service.getCurrentRoute() }
            val intent = awaitItem() as NavigationIntent.GetCurrentRoute
            assertThat(intent.response).isInstanceOf(CompletableDeferred::class.java)
            intent.response.complete(null) // complete to unblock getCurrentRoute
            job.join()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // emitAuthEvent — emits AuthState to authEvent flow
    // -------------------------------------------------------------------------

    @Test
    fun `emitAuthEvent emits LoggedOut state`() = runTest {
        val state = AuthState.LoggedOut(isActiveAccount = true, isLastAccount = false)
        service.authEvent.test {
            service.emitAuthEvent(state)
            val emitted = awaitItem()
            assertThat(emitted).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits AccountSwitched state`() = runTest {
        val state = AuthState.AccountSwitched(account = fakeAccount, showToast = true)
        service.authEvent.test {
            service.emitAuthEvent(state)
            val emitted = awaitItem()
            assertThat(emitted).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits Error state`() = runTest {
        val state = AuthState.Error(message = "Something went wrong")
        service.authEvent.test {
            service.emitAuthEvent(state)
            val emitted = awaitItem()
            assertThat(emitted).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits TokensUpdated state`() = runTest {
        service.authEvent.test {
            service.emitAuthEvent(AuthState.TokensUpdated)
            val emitted = awaitItem()
            assertThat(emitted).isEqualTo(AuthState.TokensUpdated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits NavigateToMyAccounts state`() = runTest {
        service.authEvent.test {
            service.emitAuthEvent(AuthState.NavigateToMyAccounts)
            val emitted = awaitItem()
            assertThat(emitted).isEqualTo(AuthState.NavigateToMyAccounts)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
