package com.greatergoods.meapp.features.ScaleSetup.reducer

import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.interfaces.IReducer
import com.greatergoods.meapp.features.ScaleSetup.enums.ScaleSetupStep
import com.greatergoods.meapp.features.ScaleSetup.modal.ConnectionState

data class SetupState<T>(
    val step: T,
    val connectionState: ConnectionState = ConnectionState.Loading
)

sealed interface BaseState<Step : ScaleSetupStep, S : BaseState<Step, S>> : IReducer.State {

    // Holds the core setup state for the flow and provides a type-safe copy function
// to create a new instance of the concrete state with updated setup data.
    val scaleSetupState: ScaleSetupState<Step>
    fun copyBaseState(scaleSetupState: ScaleSetupState<Step>): S
    
    // Convenience properties for accessing the current step, step list, navigation flow (first/last/next step),
// and setup status such as loading state — all derived from the underlying scaleSetupState.
    val isLastStep: Boolean
        get() = scaleSetupState.setupState.step == scaleSetupState.steps.last()
    val isFirstStep: Boolean
        get() = scaleSetupState.setupState.step == scaleSetupState.steps.first()
    val steps: List<Step>
        get() = scaleSetupState.steps
    val step: Step
        get() = scaleSetupState.setupState.step
    val isLoading: Boolean
        get() = scaleSetupState.setupState.connectionState == ConnectionState.Loading
    val nextStep: Step?
        get() = steps.getOrNull(steps.indexOf(step) + 1)

}

data class ScaleSetupState<T>(
    val setupState: SetupState<T>,
    val steps: List<T>,
    val sku: String = "",
    val permissions: GGPermissionStatusMap = mutableMapOf()
) : IReducer.State

sealed interface ScaleSetupIntent : IReducer.Intent {
    object Next : ScaleSetupIntent

    object Back : ScaleSetupIntent

    object Skip : ScaleSetupIntent

    object TryAgain : ScaleSetupIntent

    object OpenHelp : ScaleSetupIntent

    data class ExitSetup(
        val isSetupFinished: Boolean,
    ) : ScaleSetupIntent

    data class SetNewStep<Step : ScaleSetupStep>(val step: Step) : ScaleSetupIntent

    data class AlterConnectionState(
        val connectionState: ConnectionState
    ) : ScaleSetupIntent

    data class RequestPermission(
        val permission: String
    ) : ScaleSetupIntent

    data class SetPermissions(
        val permissions: GGPermissionStatusMap
    ) : ScaleSetupIntent

    data class SetSku(
        val sku: String
    ) : ScaleSetupIntent
}

open class ScaleSetupReducer<
        Step : ScaleSetupStep,
        S : BaseState<Step, S>,
        > : IReducer<S, ScaleSetupIntent> {

    override fun reduce(state: S, intent: ScaleSetupIntent): S? {
        val baseState = state.scaleSetupState

        val updatedBaseState = when (intent) {
            is ScaleSetupIntent.SetNewStep<*> -> {
                @Suppress("UNCHECKED_CAST")
                val step = intent.step as? Step ?: return state.copyBaseState(baseState)

                val updatedSteps = baseState.steps.toMutableList()
                val index = updatedSteps.indexOf(step)

                if (index >= 0) {
                    updatedSteps[index] = step
                } else {
                    updatedSteps.add(step)
                }

                baseState.copy(
                    setupState = SetupState(step),
                    steps = updatedSteps,
                )
            }

            is ScaleSetupIntent.AlterConnectionState -> baseState.copy(
                setupState = baseState.setupState.copy(
                    connectionState = intent.connectionState,
                ),
            )

            is ScaleSetupIntent.SetPermissions -> baseState.copy(
                permissions = intent.permissions,
            )

            is ScaleSetupIntent.SetSku -> baseState.copy(
                sku = intent.sku,
            )

            else -> null

        }
        return if (updatedBaseState == null) state else state.copyBaseState(updatedBaseState)
    }
}


