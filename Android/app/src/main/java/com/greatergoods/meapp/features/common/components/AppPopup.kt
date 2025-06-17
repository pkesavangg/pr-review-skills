package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import com.greatergoods.meapp.features.common.enum.AppSpacing
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme

sealed class AppPopupImageType {
    data class FullImage(
        val image: Int,
    ) : AppPopupImageType()

    data class DefaultImage(
        val image: Int,
    ) : AppPopupImageType()
}

/**
 * A pixel-perfect, scalable popup dialog matching the Figma MCP spec.
 *
 * @param visible Whether the popup is visible
 * @param heading The main headline text
 * @param supportingText The supporting body text
 * @param onPrimaryAction Click handler for the primary button
 * @param onSecondaryAction Click handler for the secondary button
 * @param onClose Click handler for the close icon
 * @param primaryLabel Label for the primary button
 * @param secondaryLabel Label for the secondary button
 * @param modifier Modifier for the popup
 * @param imageType Optional image type (full width or default)
 * @param subHeading Optional subheading text
 */
@Composable
fun AppPopup(
    visible: Boolean,
    heading: String,
    supportingText: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onClose: () -> Unit,
    primaryLabel: String,
    secondaryLabel: String,
    modifier: Modifier = Modifier,
    imageType: AppPopupImageType? = null,
    subHeading: String? = null,
) {
    Box {
        Column(
            modifier = Modifier.background(MeAppTheme.colorScheme.primary),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Image (if any)
            imageType?.let { imgType ->
                when (imgType) {
                    is AppPopupImageType.FullImage -> {
                        Image(
                            painter = painterResource(imgType.image),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MeAppTheme.colorScheme.utility)
                                    .height(AppSpacing.Modal.Base.ImageHeight),
                        )
                    }

                    is AppPopupImageType.DefaultImage -> {
                        Spacer(Modifier.height(MeAppTheme.spacing.lg + MeAppTheme.spacing.md))
                        Image(
                            painter = painterResource(imgType.image),
                            contentDescription = null,
                        )
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .padding(horizontal = MeAppTheme.spacing.md, vertical = MeAppTheme.spacing.lg)
                        .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MeAppTheme.spacing.sm),
            ) {
                subHeading?.let {
                    AppText(
                        it.toUpperCase(Locale.current),
                        TextType.SubHeading,
                        textAlign = TextAlign.Center,
                    )
                }
                AppText(heading, TextType.Title, textAlign = TextAlign.Center)
                AppText(supportingText, TextType.Body, textAlign = TextAlign.Center)
                Spacer(Modifier.height(MeAppTheme.spacing.lg))
                AppPopupActions(
                    primaryLabel = primaryLabel,
                    secondaryLabel = secondaryLabel,
                    onPrimaryAction = onPrimaryAction,
                    onSecondaryAction = onSecondaryAction,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MeAppTheme.spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                id = AppIcons.Filled.Close,
                contentDescription = "Logo",
                modifier = Modifier.align(Alignment.TopEnd),
                type = AppIconType.Default,
            )
        }
    }
}

@Composable
fun AppPopupModal(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.width(AppSpacing.Modal.Base.Width),
        shape =
            MeAppTheme.borderRadius.xl.let {
                RoundedCornerShape(it)
            },
    ) {
        content()
    }
}

@Composable
private fun AppPopupActions(
    primaryLabel: String,
    secondaryLabel: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MeAppTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppButton(
            label = primaryLabel,
            onClick = onPrimaryAction,
        )
        AppButton(
            label = secondaryLabel,
            onClick = onSecondaryAction,
            type = ButtonType.TextPrimary,
        )
    }
}

@PreviewTheme
@Composable
fun AppPopupPreview() {
    MeAppTheme {
        AppScaffold("") {
            Column {
                AppPopup(
                    heading = "Here's a Headline",
                    subHeading = "Here's a Headline",
                    supportingText = "And here's some supporting copy.",
                    onPrimaryAction = {},
                    onSecondaryAction = {},
                    onClose = {},
                    primaryLabel = "BUTTON",
                    secondaryLabel = "BUTTON",
                    imageType = AppPopupImageType.FullImage(AppIcons.Default.Logo),
                    visible = true,
                )
            }
        }
    }
}
