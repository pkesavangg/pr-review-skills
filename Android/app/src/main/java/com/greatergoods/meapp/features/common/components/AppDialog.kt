package com.greatergoods.meapp.features.common.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greatergoods.meapp.features.common.model.ActionButton

@Composable
fun AppDialog(
    title: String,
    body: String,
    confirmAction: ActionButton,
    modifier: Modifier = Modifier,
    dismissAction: ActionButton? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val dismissActionEvent = dismissAction?.action ?: confirmAction.action
    Dialog(onDismissRequest = dismissActionEvent, properties) {
        BaseModal(
            modifier,
            title = title,
            body = body,
            primaryAction = confirmAction,
            secondaryAction = dismissAction,
            onDismiss = dismissActionEvent,
        ) { }
    }
}

@PreviewTheme
@Composable
fun AppDialogPreview() {
}
