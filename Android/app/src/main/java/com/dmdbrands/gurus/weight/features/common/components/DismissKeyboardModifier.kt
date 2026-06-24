package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Dismisses the keyboard and clears focus when the user taps outside input fields.
 *
 * Uses `pointerInput` + `detectTapGestures` rather than `Modifier.clickable` so the
 * wrapper does not publish click semantics. TalkBack therefore ignores the container
 * (no spurious "double-tap to activate" announcement) and inner clickable children
 * keep working.
 *
 * Note: This is a `@Composable` modifier factory and must be called during composition.
 * It cannot be stored in a non-composable variable or used outside a `@Composable` scope.
 */
@Composable
fun Modifier.dismissKeyboardOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        )
    }
}
