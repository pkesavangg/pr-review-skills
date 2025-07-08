package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme

/**
 * CustomDialog composable matching the Figma spec (node 6598-69580).
 *
 * @param title The dialog title.
 * @param body The dialog body text.
 * @param primaryAction The primary action button (right, blue).
 * @param secondaryAction The secondary action button (left, tertiary).
 * @param onDismiss Called when the dialog is dismissed (optional, for outside click).
 * @param modifier Modifier for styling.
 */
@Composable
fun BaseModal(
    modifier: Modifier = Modifier,
    primaryActionType: ButtonType = ButtonType.InlineTextPrimary,
    primaryAction: ActionButton? = null,
    title: String? = null,
    body: String? = null,
    secondaryAction: ActionButton? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val cardColors =
        CardDefaults
            .cardColors(
                containerColor = MeTheme.colorScheme.primaryBackground,
            )

    Card(
        modifier = modifier.width(316.dp),
        shape = RoundedCornerShape(28.dp), // Figma: radius-xl = 28dp (no token found)
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier.padding(MeTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.sm),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MeTheme.typography.heading4,
                    color = MeTheme.colorScheme.textHeading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            body?.let {
                Text(
                    text = body,
                    style = MeTheme.typography.body2,
                    color = MeTheme.colorScheme.textBody,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.fillMaxWidth()) {
                content?.let {
                    it()
                }
            }
        }
        if (secondaryAction != null || primaryAction != null) {
            Column(
                modifier =
                    Modifier
                        .padding(MeTheme.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (secondaryAction != null) {
                        AppButton(
                            label = secondaryAction.text,
                            onClick = secondaryAction.action,
                            type = ButtonType.InlineTextTertiary,
                            size = ButtonSize.Small,
                            enabled = secondaryAction.enabled,
                            modifier = Modifier,
                        )
                        Spacer(modifier = Modifier.width(MeTheme.spacing.xs))
                    }
                    if (primaryAction != null) {
                        AppButton(
                            label = primaryAction.text,
                            onClick = primaryAction.action,
                            type = primaryActionType,
                            size = ButtonSize.Small,
                            enabled = primaryAction.enabled,
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Preview for CustomDialog in light and dark mode.
 */
@PreviewTheme
@Composable
fun BaseModelPreview() {
    MeAppTheme {
        Column {
            BaseModal(
                title = "Header",
                body = "Body content goes here. This is a sample dialog body.",
                primaryAction = ActionButton(text = "Button", action = {}),
                secondaryAction = ActionButton(text = "Button", action = {}),
            )
            BaseModal(
                title = "Header",
                primaryAction = ActionButton(text = "Button", action = {}),
            ) {
                Text("Custom content")
            }

            BaseModal(
                title = "Header",
            ) {
                Text("Custom content")
            }
        }
    }
}
