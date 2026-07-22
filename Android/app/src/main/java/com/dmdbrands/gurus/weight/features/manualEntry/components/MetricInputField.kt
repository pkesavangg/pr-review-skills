package com.dmdbrands.gurus.weight.features.manualEntry.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.dmdbrands.gurus.weight.features.common.components.AnimatedAppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl

/**
 * Shared metric input row used by [GeneralMetricsSection] and [R4ScaleMetricsSection].
 * Wraps [AnimatedAppInput] with the common full-width + focus-requester boilerplate so the metric
 * sections stay declarative. Behaviour is identical to the previous inline call sites.
 */
@Composable
internal fun MetricInputField(
    formControl: FormControl<String>,
    label: String,
    type: AppInputType,
    imeAction: ImeAction,
    focusRequester: FocusRequester,
    maxLength: Int,
    index: Int,
    testTag: String,
    enabled: Boolean,
    nextFocusRequester: FocusRequester? = null,
    onImeAction: (() -> Unit)? = null,
    trailingText: String? = null,
) {
    AnimatedAppInput(
        formControl = formControl,
        label = label,
        trailingText = trailingText,
        type = type,
        imeAction = imeAction,
        nextFocusRequester = nextFocusRequester,
        onImeAction = onImeAction,
        maxLength = maxLength,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        index = index,
        testTag = testTag,
        enabled = enabled,
    )
}
