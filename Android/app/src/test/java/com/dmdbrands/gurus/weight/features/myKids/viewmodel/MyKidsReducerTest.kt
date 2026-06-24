package com.dmdbrands.gurus.weight.features.myKids.viewmodel

import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MyKidsReducerTest {

    private lateinit var reducer: MyKidsReducer
    private val initialState = MyKidsState()

    @BeforeEach
    fun setUp() {
        reducer = MyKidsReducer()
    }

    private fun aBabyProfile(
        id: String = "baby-1",
        name: String = "Alice",
        accountId: String = "account-1",
    ) = BabyProfile(id = id, accountId = accountId, name = name)

    // -------------------------------------------------------------------------
    // SetBabies
    // -------------------------------------------------------------------------

    @Test
    fun `SetBabies updates babies list`() {
        val babies = listOf(aBabyProfile()).toImmutableList()
        val result = reducer.reduce(initialState, MyKidsIntent.SetBabies(babies))
        assertThat(result?.babies).isEqualTo(babies)
    }

    @Test
    fun `SetBabies with empty list clears babies`() {
        val stateWithBabies = initialState.copy(babies = listOf(aBabyProfile()).toImmutableList())
        val result = reducer.reduce(stateWithBabies, MyKidsIntent.SetBabies(persistentListOf()))
        assertThat(result?.babies).isEmpty()
    }

    @Test
    fun `SetBabies does not change other state fields`() {
        val stateWithActive = initialState.copy(activeBabyId = "baby-99", isLoading = true)
        val result = reducer.reduce(stateWithActive, MyKidsIntent.SetBabies(persistentListOf()))
        assertThat(result?.activeBabyId).isEqualTo("baby-99")
        assertThat(result?.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetActiveBabyId
    // -------------------------------------------------------------------------

    @Test
    fun `SetActiveBabyId updates activeBabyId`() {
        val result = reducer.reduce(initialState, MyKidsIntent.SetActiveBabyId("baby-1"))
        assertThat(result?.activeBabyId).isEqualTo("baby-1")
    }

    @Test
    fun `SetActiveBabyId with null clears activeBabyId`() {
        val stateWithActive = initialState.copy(activeBabyId = "baby-1")
        val result = reducer.reduce(stateWithActive, MyKidsIntent.SetActiveBabyId(null))
        assertThat(result?.activeBabyId).isNull()
    }

    @Test
    fun `SetActiveBabyId does not change other state fields`() {
        val babies = listOf(aBabyProfile()).toImmutableList()
        val stateWithBabies = initialState.copy(babies = babies, isLoading = true)
        val result = reducer.reduce(stateWithBabies, MyKidsIntent.SetActiveBabyId("baby-1"))
        assertThat(result?.babies).isEqualTo(babies)
        assertThat(result?.isLoading).isTrue()
    }

    // -------------------------------------------------------------------------
    // SetLoading
    // -------------------------------------------------------------------------

    @Test
    fun `SetLoading true sets isLoading to true`() {
        val result = reducer.reduce(initialState, MyKidsIntent.SetLoading(true))
        assertThat(result?.isLoading).isTrue()
    }

    @Test
    fun `SetLoading false sets isLoading to false`() {
        val loadingState = initialState.copy(isLoading = true)
        val result = reducer.reduce(loadingState, MyKidsIntent.SetLoading(false))
        assertThat(result?.isLoading).isFalse()
    }

    @Test
    fun `SetLoading does not change other state fields`() {
        val babies = listOf(aBabyProfile()).toImmutableList()
        val stateWithBabies = initialState.copy(babies = babies, activeBabyId = "baby-1")
        val result = reducer.reduce(stateWithBabies, MyKidsIntent.SetLoading(true))
        assertThat(result?.babies).isEqualTo(babies)
        assertThat(result?.activeBabyId).isEqualTo("baby-1")
    }

    // -------------------------------------------------------------------------
    // SaveBaby / DeleteBaby — pass-through (side effects handled in ViewModel)
    // -------------------------------------------------------------------------

    @Test
    fun `SaveBaby returns state unchanged`() {
        val stateWithData = initialState.copy(activeBabyId = "baby-1")
        val intent = MyKidsIntent.SaveBaby(
            name = "Bob",
            birthdayMillis = 1000L,
            biologicalSex = "male",
            birthLengthMillimeters = 500,
            birthWeightDecigrams = 3500,
        )
        val result = reducer.reduce(stateWithData, intent)
        assertThat(result).isEqualTo(stateWithData)
    }

    @Test
    fun `DeleteBaby returns state unchanged`() {
        val stateWithData = initialState.copy(activeBabyId = "baby-1")
        val result = reducer.reduce(stateWithData, MyKidsIntent.DeleteBaby("baby-1"))
        assertThat(result).isEqualTo(stateWithData)
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty babies list`() {
        assertThat(initialState.babies).isEmpty()
    }

    @Test
    fun `initial state has null activeBabyId`() {
        assertThat(initialState.activeBabyId).isNull()
    }

    @Test
    fun `initial state has isLoading false`() {
        assertThat(initialState.isLoading).isFalse()
    }
}
