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
    title: String,
    primaryAction: ActionButton,
    modifier: Modifier = Modifier,
    body: String? = null,
    secondaryAction: ActionButton? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val cardColors =
        CardDefaults
            .cardColors(
                containerColor = MeAppTheme.colorScheme.primary,
            )

    Card(
        modifier = modifier.width(320.dp),
        shape = RoundedCornerShape(28.dp), // Figma: radius-xl = 28dp (no token found)
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier.padding(MeAppTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeAppTheme.spacing.sm),
        ) {
            Text(
                text = title,
                style = MeAppTheme.typography.heading4,
                color = MeAppTheme.colorScheme.heading,
                modifier = Modifier.fillMaxWidth(),
            )
            body?.let {
                Text(
                    text = body,
                    style = MeAppTheme.typography.body3,
                    color = MeAppTheme.colorScheme.body,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.fillMaxWidth()) {
                content?.let {
                    it()
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .padding(MeAppTheme.spacing.md),
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
                        modifier = Modifier,
                    )
                    Spacer(modifier = Modifier.width(MeAppTheme.spacing.xs))
                }
                AppButton(
                    label = primaryAction.text,
                    onClick = primaryAction.action,
                    type = ButtonType.InlineTextPrimary,
                    size = ButtonSize.Small,
                    modifier = Modifier,
                )
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
        }
    }
}
