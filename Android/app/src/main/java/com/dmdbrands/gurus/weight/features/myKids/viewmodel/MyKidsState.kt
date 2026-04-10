package com.dmdbrands.gurus.weight.features.myKids.viewmodel

import androidx.compose.runtime.Stable
import com.dmdbrands.gurus.weight.domain.interfaces.IReducer
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class MyKidsState(
    val babies: ImmutableList<BabyProfile> = persistentListOf(),
    val activeBabyId: String? = null,
    val isLoading: Boolean = false,
) : IReducer.State

sealed interface MyKidsIntent : IReducer.Intent {
    data class SetBabies(val babies: ImmutableList<BabyProfile>) : MyKidsIntent
    data class SetActiveBabyId(val babyId: String?) : MyKidsIntent
    data class SetLoading(val isLoading: Boolean) : MyKidsIntent
    data class SaveBaby(
        val name: String,
        val birthdayMillis: Long,
        val biologicalSex: String?,
        val birthLengthMillimeters: Int?,
        val birthWeightDecigrams: Int?,
    ) : MyKidsIntent
    data class DeleteBaby(val babyId: String) : MyKidsIntent
}
