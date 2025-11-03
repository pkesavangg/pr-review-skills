package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * A reusable modal dialog wrapper that handles backdrop dismiss functionality.
 *
 * This component provides a consistent way to create modals with proper backdrop dismiss behavior.
 * It automatically handles the full-screen background with clickable behavior when backdrop dismiss is enabled.
 *
 * @param onDismiss Called when the dialog should be dismissed
 * @param config Configuration for modal behavior
 * @param content The content to display inside the modal
 */
@Composable
fun ModalDialog(
    onDismiss: () -> Unit,
    config: ModalConfig = ModalConfigs.Standard,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = config.dismissOnBackPress,
            dismissOnClickOutside = config.dismissOnClickOutside,
            usePlatformDefaultWidth = config.usePlatformDefaultWidth,
            decorFitsSystemWindows = config.decorFitsSystemWindows,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background overlay - only clickable on areas not covered by modal
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MeTheme.colorScheme.overlay)
                    .then(
                        if (config.dismissOnClickOutside) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            )
                        } else {
                            Modifier
                        }
                    ),
            )

            // Modal content - positioned on top of overlay with click blocking
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* Block clicks from reaching the overlay */ }
                    )
            ) {
                content()
            }
        }
    }
}

/**
 * Convenience overload for backward compatibility.
 */
@Composable
fun ModalDialog(
    onDismiss: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable () -> Unit
) {
    ModalDialog(
        onDismiss = onDismiss,
        config = ModalConfig(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        content = content
    )
}

/**
 * A specialized modal dialog wrapper for BaseModal components.
 *
 * This provides a convenient way to wrap BaseModal with proper backdrop dismiss behavior.
 *
 * @param onDismiss Called when the dialog should be dismissed
 * @param dismissOnBackPress Whether the dialog can be dismissed by pressing back
 * @param dismissOnClickOutside Whether the dialog can be dismissed by clicking outside
 * @param baseModalContent The BaseModal content composable
 */
@Composable
fun BaseModalDialog(
    onDismiss: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    baseModalContent: @Composable () -> Unit
) {
    ModalDialog(
        onDismiss = onDismiss,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
    ) {
        baseModalContent()
    }
}
