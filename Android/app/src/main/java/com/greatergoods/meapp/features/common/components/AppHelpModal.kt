package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.greatergoods.meapp.features.common.strings.AppHelpModalStrings
import com.greatergoods.meapp.proto.ThemeMode
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.LocalAppTheme
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import android.annotation.SuppressLint
import android.content.Intent

/**
 * Help modal matching Figma (node 14585-12302): shows support phone/email and info.
 *
 * @param onClose Called when the close button is pressed.
 * @param modifier Modifier for styling.
 */
@SuppressLint("UseKtx")
@Composable
fun AppHelpModal(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val themeMode = LocalAppTheme.current
    val stampImage = if (themeMode == ThemeMode.LIGHT) AppIcons.Default.Stamp else AppIcons.Default.StampDark
    AppPopupModal {
        AppPopup(
            true,
            AppHelpModalStrings.Title,
            supportingText = AppHelpModalStrings.SupportingText,
            onClose = {
                onClose()
            },
            imageType = AppPopupImageType.DefaultImage(stampImage),
        ) {
            Spacer(Modifier.height(MeTheme.spacing.md))
            // Phone row
            val phoneNumber = AppHelpModalStrings.Phone
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .wrapContentSize()
                        .clickable(
                            true,
                        ) {
                            val intent =
                                Intent(Intent.ACTION_DIAL).apply {
                                    data = "tel:$phoneNumber".toUri()
                                }
                            context.startActivity(intent)
                        },
            ) {
                // TODO: Replace with custom phone icon if available
                Icon(
                    painter = painterResource(id = android.R.drawable.sym_action_call),
                    contentDescription = AppHelpModalStrings.PhoneContentDescription,
                    tint = MeTheme.colorScheme.primaryAction,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(MeTheme.spacing.x2s))
                AppText(
                    text = phoneNumber,
                    textType = TextType.Link,
                    textAlign = TextAlign.Start,
                )
            }
            // Email row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .wrapContentSize()
                        .clickable(
                            true,
                        ) {
                            val intent =
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = "mailto:$AppHelpModalStrings.Email".toUri()
                                }
                            context.startActivity(intent)
                        },
            ) {
                // TODO: Replace with custom email icon if available
                Icon(
                    painter = painterResource(id = android.R.drawable.sym_action_email),
                    contentDescription = AppHelpModalStrings.EmailContentDescription,
                    tint = MeTheme.colorScheme.primaryAction,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(MeTheme.spacing.x2s))
                AppText(
                    text = AppHelpModalStrings.Email,
                    textType = TextType.Link,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppHelpModalPreview() {
    MeAppTheme {
        AppHelpModal(onClose = {})
    }
}
