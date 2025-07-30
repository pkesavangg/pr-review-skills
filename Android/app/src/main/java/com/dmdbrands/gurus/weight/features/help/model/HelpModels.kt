package com.dmdbrands.gurus.weight.features.help.model

import com.dmdbrands.gurus.weight.domain.interfaces.IReducer


/**
 * State model for the Help screen.
 */
data class HelpState(
    val isLoading: Boolean = false,
    val error: String? = null
) : IReducer.State

/**
 * Intent model for Help screen actions.
 */
sealed class HelpIntent : IReducer.Intent {
    data object OnBack : HelpIntent()
    data object ShowModelNumberHelpPopup: HelpIntent()
    data object OpenDebugMenu : HelpIntent()
    data class OpenUrl(val url: String) : HelpIntent()
    data class Error(val message: String) : HelpIntent()
}
