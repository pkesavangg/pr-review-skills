package com.dmdbrands.gurus.weight.features.landing.reducer

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MultiAccountLandingReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class MultiAccountLandingReducerTest {

    private lateinit var reducer: MultiAccountLandingReducer

    private val fakeAccount: Account = Account(
        id = "account-1",
        firstName = "Jane",
        lastName = "Doe",
        dob = "1990-01-01",
        email = "jane@example.com",
        gender = "female",
        zipcode = "12345",
        weightUnit = WeightUnit.LB,
        height = 1650,
        activityLevel = "normal",
    )

    private val fakeAccount2: Account = Account(
        id = "account-2",
        firstName = "John",
        lastName = "Smith",
        dob = "1985-06-15",
        email = "john@example.com",
        gender = "male",
        zipcode = "67890",
        weightUnit = WeightUnit.KG,
        height = 1800,
        activityLevel = "normal",
    )

    @BeforeEach
    fun setUp() {
        reducer = MultiAccountLandingReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default MultiAccountLandingState has expected initial values`() {
        val state = MultiAccountLandingState()

        assertThat(state.accounts).isEmpty()
        assertThat(state.hasReachedMaxAccounts).isFalse()
        assertThat(state.accountToRemove).isNull()
    }

    // -------------------------------------------------------------------------
    // SetAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `SetAccounts populates accounts list`() {
        val state = MultiAccountLandingState()

        val result = reducer.reduce(
            state,
            MultiAccountLandingIntent.SetAccounts(listOf(fakeAccount, fakeAccount2)),
        )

        assertThat(result?.accounts).containsExactly(fakeAccount, fakeAccount2).inOrder()
    }

    @Test
    fun `SetAccounts with empty list clears accounts`() {
        val state = MultiAccountLandingState(accounts = persistentListOf(fakeAccount))

        val result = reducer.reduce(state, MultiAccountLandingIntent.SetAccounts(emptyList()))

        assertThat(result?.accounts).isEmpty()
    }

    @Test
    fun `SetAccounts sets hasReachedMaxAccounts when provided`() {
        val state = MultiAccountLandingState(hasReachedMaxAccounts = false)

        val result = reducer.reduce(
            state,
            MultiAccountLandingIntent.SetAccounts(
                accounts = listOf(fakeAccount),
                hasReachedMaxAccounts = true,
            ),
        )

        assertThat(result?.hasReachedMaxAccounts).isTrue()
    }

    @Test
    fun `SetAccounts hasReachedMaxAccounts defaults to false when not provided`() {
        val state = MultiAccountLandingState(hasReachedMaxAccounts = true)

        val result = reducer.reduce(
            state,
            MultiAccountLandingIntent.SetAccounts(accounts = listOf(fakeAccount)),
        )

        assertThat(result?.hasReachedMaxAccounts).isFalse()
    }

    @Test
    fun `SetAccounts preserves accountToRemove`() {
        val state = MultiAccountLandingState(accountToRemove = fakeAccount2)

        val result = reducer.reduce(
            state,
            MultiAccountLandingIntent.SetAccounts(listOf(fakeAccount)),
        )

        assertThat(result?.accountToRemove).isEqualTo(fakeAccount2)
    }

    // -------------------------------------------------------------------------
    // RequestRemoveAccount
    // -------------------------------------------------------------------------

    @Test
    fun `RequestRemoveAccount sets accountToRemove`() {
        val state = MultiAccountLandingState(accountToRemove = null)

        val result = reducer.reduce(
            state,
            MultiAccountLandingIntent.RequestRemoveAccount(fakeAccount),
        )

        assertThat(result?.accountToRemove).isEqualTo(fakeAccount)
    }

    @Test
    fun `RequestRemoveAccount overwrites previous accountToRemove`() {
        val state = MultiAccountLandingState(accountToRemove = fakeAccount)

        val result = reducer.reduce(
            state,
            MultiAccountLandingIntent.RequestRemoveAccount(fakeAccount2),
        )

        assertThat(result?.accountToRemove).isEqualTo(fakeAccount2)
    }

    @Test
    fun `RequestRemoveAccount preserves accounts and hasReachedMaxAccounts`() {
        val state = MultiAccountLandingState(
            accounts = persistentListOf(fakeAccount, fakeAccount2),
            hasReachedMaxAccounts = true,
        )

        val result = reducer.reduce(
            state,
            MultiAccountLandingIntent.RequestRemoveAccount(fakeAccount),
        )

        assertThat(result?.accounts).containsExactly(fakeAccount, fakeAccount2).inOrder()
        assertThat(result?.hasReachedMaxAccounts).isTrue()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — no state change, returns state unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `SelectAccount returns state unchanged`() {
        val state = MultiAccountLandingState(
            accounts = persistentListOf(fakeAccount),
            hasReachedMaxAccounts = false,
        )

        val result = reducer.reduce(state, MultiAccountLandingIntent.SelectAccount(fakeAccount))

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `Login with account returns state unchanged`() {
        val state = MultiAccountLandingState(accounts = persistentListOf(fakeAccount))

        val result = reducer.reduce(state, MultiAccountLandingIntent.Login(fakeAccount))

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `Login with null account returns state unchanged`() {
        val state = MultiAccountLandingState()

        val result = reducer.reduce(state, MultiAccountLandingIntent.Login(null))

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `CreateAccount returns state unchanged`() {
        val state = MultiAccountLandingState(
            accounts = persistentListOf(fakeAccount),
            hasReachedMaxAccounts = true,
        )

        val result = reducer.reduce(state, MultiAccountLandingIntent.CreateAccount)

        assertThat(result).isEqualTo(state)
    }

    @Test
    fun `ShowMaxLimitReachedAlert returns state unchanged`() {
        val state = MultiAccountLandingState(hasReachedMaxAccounts = true)

        val result = reducer.reduce(state, MultiAccountLandingIntent.ShowMaxLimitReachedAlert)

        assertThat(result).isEqualTo(state)
    }
}
