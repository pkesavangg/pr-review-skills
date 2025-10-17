package com.dmdbrands.gurus.weight.features.common.components

/**
 * Configuration for modal dialog behavior.
 * 
 * This can be used to globally control modal behavior across the app.
 * You can create different configurations for different contexts (e.g., critical vs informational modals).
 */
data class ModalConfig(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val usePlatformDefaultWidth: Boolean = false,
    val decorFitsSystemWindows: Boolean = false,
)

/**
 * Predefined modal configurations for common use cases.
 */
object ModalConfigs {
    /**
     * Standard modal configuration - allows dismissal by back press and outside click.
     */
    val Standard = ModalConfig(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
    )
    
    /**
     * Critical modal configuration - prevents dismissal by outside click.
     * Use for important confirmations or critical information.
     */
    val Critical = ModalConfig(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
    )
    
    /**
     * Informational modal configuration - allows all dismissal methods.
     * Use for help dialogs, tips, or non-critical information.
     */
    val Informational = ModalConfig(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
    )
    
    /**
     * Persistent modal configuration - prevents all dismissal methods except explicit actions.
     * Use for loading states or mandatory user interactions.
     */
    val Persistent = ModalConfig(
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
    )
}
