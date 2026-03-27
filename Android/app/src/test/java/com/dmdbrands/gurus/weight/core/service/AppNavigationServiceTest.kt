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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationServiceTest {

    @JvmField
    @RegisterExtension
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

    @BeforeEach
    fun setUp() {
        service = AppNavigationService()
    }

    // -------------------------------------------------------------------------
    // navigateTo — emits NavigateTo intent with correct parameters
    // -------------------------------------------------------------------------

    @Test
    fun `navigateTo emits NavigateTo with route only`() = runTest {
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
    // replaceStack (single route) — wraps route in listOf()
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
    // replaceStack (list of routes) — passes route list directly
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
    fun `registerOnDeactivate emits intent with same route and callback instance`() = runTest {
        val route = AppRoute.Main.Entry
        val callback: suspend () -> Boolean = { true }
        service.navigationIntent.test {
            service.registerOnDeactivate(route, callback)
            val intent = awaitItem() as NavigationIntent.RegisterOnDeactivate
            assertThat(intent.route).isEqualTo(route)
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
    // getCurrentRoute — suspends on CompletableDeferred, returns NavKey from
    // the observer that completes the deferred inside the emitted intent
    // -------------------------------------------------------------------------

    @Test
    fun `getCurrentRoute returns NavKey when deferred is completed`() = runTest {
        var result: NavKey? = null
        service.navigationIntent.test {
            val job = launch { result = service.getCurrentRoute() }
            val intent = awaitItem() as NavigationIntent.GetCurrentRoute
            intent.response.complete(AppRoute.Main.Dashboard)
            job.join()
            assertThat(result).isEqualTo(AppRoute.Main.Dashboard)
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

    // -------------------------------------------------------------------------
    // emitAuthEvent — emits each AuthState variant to authEvent SharedFlow
    // -------------------------------------------------------------------------

    @Test
    fun `emitAuthEvent emits LoggedInFromLoading state`() = runTest {
        val state = AuthState.LoggedInFromLoading(account = fakeAccount)
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits LoggedOut state`() = runTest {
        val state = AuthState.LoggedOut(isActiveAccount = true, isLastAccount = false)
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits UnauthorizedLogout state`() = runTest {
        val state = AuthState.UnauthorizedLogout(accountId = "account-1")
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits AccountAdded state`() = runTest {
        val state = AuthState.AccountAdded(account = fakeAccount)
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits AccountSwitched state`() = runTest {
        val state = AuthState.AccountSwitched(account = fakeAccount, showToast = true)
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits ProfileUpdated state`() = runTest {
        val state = AuthState.ProfileUpdated(account = fakeAccount)
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits TokensUpdated state`() = runTest {
        service.authEvent.test {
            service.emitAuthEvent(AuthState.TokensUpdated)
            assertThat(awaitItem()).isEqualTo(AuthState.TokensUpdated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits Error state`() = runTest {
        val state = AuthState.Error(message = "Something went wrong")
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits AccountDeleted state`() = runTest {
        val state = AuthState.AccountDeleted(isActiveAccount = true, message = "Deleted")
        service.authEvent.test {
            service.emitAuthEvent(state)
            assertThat(awaitItem()).isEqualTo(state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits NavigateToMyAccounts state`() = runTest {
        service.authEvent.test {
            service.emitAuthEvent(AuthState.NavigateToMyAccounts)
            assertThat(awaitItem()).isEqualTo(AuthState.NavigateToMyAccounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emitAuthEvent emits NavigateBackFromMyAccounts state`() = runTest {
        service.authEvent.test {
            service.emitAuthEvent(AuthState.NavigateBackFromMyAccounts)
            assertThat(awaitItem()).isEqualTo(AuthState.NavigateBackFromMyAccounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // SharedFlow behavior — navigationIntent is a SharedFlow (not replay)
    // -------------------------------------------------------------------------

    @Test
    fun `navigationIntent delivers same event to multiple collectors`() = runTest {
        var collector1Result: NavigationIntent? = null
        var collector2Result: NavigationIntent? = null

        // Launch collectors first so they subscribe before emission
        val job1 = launch { collector1Result = service.navigationIntent.first() }
        val job2 = launch { collector2Result = service.navigationIntent.first() }
        advanceUntilIdle()

        service.login()

        job1.join()
        job2.join()

        assertThat(collector1Result).isEqualTo(NavigationIntent.Login)
        assertThat(collector2Result).isEqualTo(NavigationIntent.Login)
    }

    @Test
    fun `authEvent delivers same event to multiple collectors`() = runTest {
        val state = AuthState.TokensUpdated
        var collector1Result: AuthState? = null
        var collector2Result: AuthState? = null

        // Launch collectors first so they subscribe before emission
        val job1 = launch { collector1Result = service.authEvent.first() }
        val job2 = launch { collector2Result = service.authEvent.first() }
        advanceUntilIdle()

        service.emitAuthEvent(state)

        job1.join()
        job2.join()

        assertThat(collector1Result).isEqualTo(state)
        assertThat(collector2Result).isEqualTo(state)
    }

    // -------------------------------------------------------------------------
    // emitNavigationIntent — tested via navigateTo which delegates to it
    // -------------------------------------------------------------------------

    @Test
    fun `navigateTo emits intent via emitNavigationIntent to the shared flow`() = runTest {
        var collected: NavigationIntent? = null
        val job = launch { collected = service.navigationIntent.first() }
        advanceUntilIdle()

        service.navigateTo(AppRoute.Main.History)

        job.join()
        assertThat(collected).isInstanceOf(NavigationIntent.NavigateTo::class.java)
        assertThat((collected as NavigationIntent.NavigateTo).route).isEqualTo(AppRoute.Main.History)
    }

    @Test
    fun `multiple navigateTo calls emit multiple intents sequentially`() = runTest {
        service.navigationIntent.test {
            service.navigateTo(AppRoute.Main.Dashboard)
            assertThat((awaitItem() as NavigationIntent.NavigateTo).route).isEqualTo(AppRoute.Main.Dashboard)

            service.navigateTo(AppRoute.Main.Settings)
            assertThat((awaitItem() as NavigationIntent.NavigateTo).route).isEqualTo(AppRoute.Main.Settings)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
