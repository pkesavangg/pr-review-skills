package com.dmdbrands.gurus.weight.features.MyAccounts.reducer

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.Account.Account
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [MyAccountsReducer].
 *
 * The reducer is a pure function — no mocking or coroutines needed.
 * Each test creates an initial state, dispatches an intent, and asserts the result.
 */
class MyAccountsReducerTest {

    private lateinit var reducer: MyAccountsReducer

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
        reducer = MyAccountsReducer()
    }

    // -------------------------------------------------------------------------
    // Default state
    // -------------------------------------------------------------------------

    @Test
    fun `default MyAccountsState has expected initial values`() {
        val state = MyAccountsState()

        assertThat(state.accounts).isEmpty()
        assertThat(state.showMaxAccountsDialog).isFalse()
        assertThat(state.accountToRemove).isNull()
        assertThat(state.hasReachedMaxAccounts).isFalse()
    }

    // -------------------------------------------------------------------------
    // SetAccounts
    // -------------------------------------------------------------------------

    @Test
    fun `SetAccounts populates accounts list`() {
        val state = MyAccountsState()

        val result = reducer.reduce(
            state,
            MyAccountsIntent.SetAccounts(
                accounts = listOf(fakeAccount, fakeAccount2),
                hasReachedMaxAccounts = false,
            ),
        )

        assertThat(result?.accounts).containsExactly(fakeAccount, fakeAccount2).inOrder()
    }

    @Test
    fun `SetAccounts with empty list clears accounts`() {
        val state = MyAccountsState(accounts = persistentListOf(fakeAccount))

        val result = reducer.reduce(
            state,
            MyAccountsIntent.SetAccounts(accounts = emptyList(), hasReachedMaxAccounts = false),
        )

        assertThat(result?.accounts).isEmpty()
    }

    @Test
    fun `SetAccounts sets hasReachedMaxAccounts to true`() {
        val state = MyAccountsState(hasReachedMaxAccounts = false)

        val result = reducer.reduce(
            state,
            MyAccountsIntent.SetAccounts(
                accounts = listOf(fakeAccount),
                hasReachedMaxAccounts = true,
            ),
        )

        assertThat(result?.hasReachedMaxAccounts).isTrue()
    }

    @Test
    fun `SetAccounts sets hasReachedMaxAccounts to false`() {
        val state = MyAccountsState(hasReachedMaxAccounts = true)

        val result = reducer.reduce(
            state,
            MyAccountsIntent.SetAccounts(
                accounts = listOf(fakeAccount),
                hasReachedMaxAccounts = false,
            ),
        )

        assertThat(result?.hasReachedMaxAccounts).isFalse()
    }

    @Test
    fun `SetAccounts preserves showMaxAccountsDialog and accountToRemove`() {
        val state = MyAccountsState(showMaxAccountsDialog = true, accountToRemove = fakeAccount2)

        val result = reducer.reduce(
            state,
            MyAccountsIntent.SetAccounts(accounts = listOf(fakeAccount), hasReachedMaxAccounts = false),
        )

        assertThat(result?.showMaxAccountsDialog).isTrue()
        assertThat(result?.accountToRemove).isEqualTo(fakeAccount2)
    }

    // -------------------------------------------------------------------------
    // ShowMaxAccountsAlert
    // -------------------------------------------------------------------------

    @Test
    fun `ShowMaxAccountsAlert sets showMaxAccountsDialog to true`() {
        val state = MyAccountsState(showMaxAccountsDialog = false)

        val result = reducer.reduce(state, MyAccountsIntent.ShowMaxAccountsAlert)

        assertThat(result?.showMaxAccountsDialog).isTrue()
    }

    @Test
    fun `ShowMaxAccountsAlert preserves other fields`() {
        val state = MyAccountsState(
            accounts = persistentListOf(fakeAccount),
            hasReachedMaxAccounts = true,
            accountToRemove = fakeAccount2,
        )

        val result = reducer.reduce(state, MyAccountsIntent.ShowMaxAccountsAlert)

        assertThat(result?.accounts).containsExactly(fakeAccount)
        assertThat(result?.hasReachedMaxAccounts).isTrue()
        assertThat(result?.accountToRemove).isEqualTo(fakeAccount2)
    }

    // -------------------------------------------------------------------------
    // RequestRemoveAccount
    // -------------------------------------------------------------------------

    @Test
    fun `RequestRemoveAccount sets accountToRemove`() {
        val state = MyAccountsState(accountToRemove = null)

        val result = reducer.reduce(state, MyAccountsIntent.RequestRemoveAccount(fakeAccount))

        assertThat(result?.accountToRemove).isEqualTo(fakeAccount)
    }

    @Test
    fun `RequestRemoveAccount overwrites previous accountToRemove`() {
        val state = MyAccountsState(accountToRemove = fakeAccount)

        val result = reducer.reduce(state, MyAccountsIntent.RequestRemoveAccount(fakeAccount2))

        assertThat(result?.accountToRemove).isEqualTo(fakeAccount2)
    }

    @Test
    fun `RequestRemoveAccount preserves accounts hasReachedMaxAccounts and showMaxAccountsDialog`() {
        val state = MyAccountsState(
            accounts = persistentListOf(fakeAccount, fakeAccount2),
            hasReachedMaxAccounts = true,
            showMaxAccountsDialog = true,
        )

        val result = reducer.reduce(state, MyAccountsIntent.RequestRemoveAccount(fakeAccount))

        assertThat(result?.accounts).containsExactly(fakeAccount, fakeAccount2).inOrder()
        assertThat(result?.hasReachedMaxAccounts).isTrue()
        assertThat(result?.showMaxAccountsDialog).isTrue()
    }

    // -------------------------------------------------------------------------
    // Side-effect intents — reducer returns null (handled only in ViewModel)
    // -------------------------------------------------------------------------

    @Test
    fun `LoginToAccount with account returns null`() {
        val state = MyAccountsState(accounts = persistentListOf(fakeAccount))

        val result = reducer.reduce(state, MyAccountsIntent.LoginToAccount(fakeAccount))

        assertThat(result).isNull()
    }

    @Test
    fun `LoginToAccount with null account returns null`() {
        val state = MyAccountsState()

        val result = reducer.reduce(state, MyAccountsIntent.LoginToAccount(null))

        assertThat(result).isNull()
    }

    @Test
    fun `CreateAccount returns null`() {
        val state = MyAccountsState(accounts = persistentListOf(fakeAccount))

        val result = reducer.reduce(state, MyAccountsIntent.CreateAccount)

        assertThat(result).isNull()
    }

    @Test
    fun `SelectAccount returns null`() {
        val state = MyAccountsState(accounts = persistentListOf(fakeAccount))

        val result = reducer.reduce(state, MyAccountsIntent.SelectAccount(fakeAccount))

        assertThat(result).isNull()
    }
}
