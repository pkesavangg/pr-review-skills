package com.dmdbrands.gurus.weight.features.myKids.viewmodel

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer

class MyKidsReducer : IReducer<MyKidsState, MyKidsIntent> {
    override fun reduce(state: MyKidsState, intent: MyKidsIntent): MyKidsState? = when (intent) {
        is MyKidsIntent.SetBabies -> state.copy(babies = intent.babies)
        is MyKidsIntent.SetActiveBabyId -> state.copy(activeBabyId = intent.babyId)
        is MyKidsIntent.SetLoading -> state.copy(isLoading = intent.isLoading)
        is MyKidsIntent.SetMeasurementUnits -> state.copy(measurementUnits = intent.units)
        is MyKidsIntent.SaveBaby -> state
        is MyKidsIntent.DeleteBaby -> state
    }
}
