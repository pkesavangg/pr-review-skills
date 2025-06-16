package com.greatergoods.meapp.domain.interfaces

interface IReducer<State : IReducer.State, Intent : IReducer.Intent> {
    interface State
    interface Intent

    fun reduce(state: State, intent: Intent): State?
}
