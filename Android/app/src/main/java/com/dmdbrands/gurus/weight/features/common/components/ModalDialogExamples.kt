package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.gurus.weight.theme.MeAppTheme

/**
 * Examples showing different ways to use the new ModalDialog system.
 * 
 * This demonstrates how to migrate existing modals and use different configurations.
 */
object ModalDialogExamples {
    
    /**
     * Example 1: Standard modal with default configuration
     */
    @Composable
    fun StandardModalExample(
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        ModalDialog(
            onDismiss = onDismiss,
            // Uses ModalConfigs.Standard by default
        ) {
            content()
        }
    }
    
    /**
     * Example 2: Critical modal that prevents outside dismissal
     */
    @Composable
    fun CriticalModalExample(
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        ModalDialog(
            onDismiss = onDismiss,
            config = ModalConfigs.Critical, // Prevents outside click dismissal
        ) {
            content()
        }
    }
    
    /**
     * Example 3: Custom configuration
     */
    @Composable
    fun CustomModalExample(
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        ModalDialog(
            onDismiss = onDismiss,
            config = ModalConfig(
                dismissOnBackPress = true,
                dismissOnClickOutside = false, // Custom behavior
            ),
        ) {
            content()
        }
    }
    
    /**
     * Example 4: Backward compatibility with boolean parameters
     */
    @Composable
    fun BackwardCompatibleModalExample(
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        ModalDialog(
            onDismiss = onDismiss,
            dismissOnBackPress = true,
            dismissOnClickOutside = false, // Old way still works
        ) {
            content()
        }
    }
    
    /**
     * Example 5: BaseModalDialog for BaseModal components
     */
    @Composable
    fun BaseModalExample(
        onDismiss: () -> Unit,
        baseModalContent: @Composable () -> Unit
    ) {
        ModalDialog(
            onDismiss = onDismiss,
            config = ModalConfigs.Informational,
        ) {
            baseModalContent()
        }
    }
}

/**
 * Migration guide for existing modals:
 * 
 * BEFORE (old way):
 * ```kotlin
 * Dialog(
 *   onDismissRequest = onClose,
 *   properties = DialogProperties(
 *     dismissOnBackPress = true,
 *     dismissOnClickOutside = true,
 *     usePlatformDefaultWidth = false,
 *     decorFitsSystemWindows = false,
 *   ),
 * ) {
 *   Box(
 *     modifier = Modifier
 *       .fillMaxSize()
 *       .background(MeTheme.colorScheme.glow)
 *       .clickable { onClose() },
 *   ) {
 *     Box(modifier = Modifier.align(Alignment.Center)) {
 *       // Modal content...
 *     }
 *   }
 * }
 * ```
 * 
 * AFTER (new way):
 * ```kotlin
 * ModalDialog(
 *   onDismiss = onClose,
 *   config = ModalConfigs.Standard, // Choose appropriate config
 * ) {
 *   // Modal content...
 * }
 * ```
 * 
 * Benefits:
 * - ✅ Consistent behavior across all modals
 * - ✅ Easy to change global modal behavior
 * - ✅ Predefined configurations for common use cases
 * - ✅ Backward compatibility maintained
 * - ✅ Cleaner, more readable code
 * - ✅ Single place to fix backdrop dismiss issues
 */
