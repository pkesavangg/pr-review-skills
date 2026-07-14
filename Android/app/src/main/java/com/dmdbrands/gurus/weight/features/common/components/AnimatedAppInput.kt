package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import kotlinx.coroutines.delay

/**
 * Animated wrapper for AppInput that provides staggered fade-in animation.
 *
 * @param formControl The form control for the input field
 * @param label The label text for the input
 * @param type The input type (NUMBER, BODY_COMP, etc.)
 * @param imeAction The IME action for the input
 * @param nextFocusRequester Optional focus requester for the next field
 * @param onImeAction Optional callback for IME action
 * @param maxLength Maximum length for the input
 * @param modifier Modifier for the input
 * @param index Index for staggered animation timing
 * @param trailingText Optional unit suffix pinned to the field's right edge (rendered as "(unit)")
 */
@Composable
fun AnimatedAppInput(
    formControl: FormControl<String>,
    label: String,
    type: AppInputType,
    imeAction: ImeAction,
    nextFocusRequester: FocusRequester?,
    onImeAction: (() -> Unit)?,
    maxLength: Int,
    modifier: Modifier,
    index: Int,
    testTag: String? = null,
    trailingText: String? = null,
    enabled: Boolean = true,
) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val delayMs = 75L  // Balanced timing for smooth staggered animation
        delay(index * delayMs)
        alpha.animateTo(1f, animationSpec = tween(300))
    }

    AppInput(
        formControl = formControl,
        label = label,
        type = type,
        imeAction = imeAction,
        nextFocusRequester = nextFocusRequester,
        onImeAction = onImeAction,
        maxLength = maxLength,
        modifier = modifier.graphicsLayer { this.alpha = alpha.value },
        testTag = testTag,
        trailingText = trailingText,
        enabled = enabled,
    )
}
