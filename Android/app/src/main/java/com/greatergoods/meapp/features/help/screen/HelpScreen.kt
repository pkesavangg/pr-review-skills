package com.greatergoods.meapp.features.help.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.config.AppConfig
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.ScaleList
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.help.model.HelpIntent
import com.greatergoods.meapp.features.help.strings.HelpScreenStrings
import com.greatergoods.meapp.features.help.viewmodel.HelpViewModel
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import android.content.Intent

/**
 * Help screen composable. Displays segmented control for different help sections and related content.
 */
@Composable
fun HelpScreen() {
    val viewModel: HelpViewModel = hiltViewModel()

    BackHandler {
        viewModel.handleIntent(HelpIntent.OnBack)
    }

    HelpContent(viewModel::handleIntent)
}

/**
 * Main content composable for the Help screen.
 */
@Composable
private fun HelpContent(
    handleIntent: (HelpIntent) -> Unit
) {
    AppScaffold(
        title = HelpScreenStrings.Title,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {
                handleIntent(HelpIntent.OnBack)
            }
        },
    ) { scaffoldModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Body content based on selected segment
            ContactUsContent(handleIntent)
            Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.xl))
            // Reuse ScaleList from ChooseScaleScreen
            ScaleList(
                onScaleSelected = { scale ->
                    val url = "${AppConfig.PRODUCT_URL}/${scale.sku}"
                    handleIntent(HelpIntent.OpenUrl(url))
                },
            )
        }
    }
}

/**
 * Contact Us content composable.
 */
@Composable
private fun ContactUsContent(handleIntent: (HelpIntent) -> Unit) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = MeTheme.spacing.sm, end = MeTheme.spacing.sm, top = MeTheme.spacing.md),
    ) {
        AppText(
            text = HelpScreenStrings.ContactSectionTitle,
            textType = TextType.Title,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.padding(top = MeTheme.spacing.x3s))
        AppText(
            text = HelpScreenStrings.ContactSectionSubtitle,
            textType = TextType.Subtitle,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
        // Phone contact
        val phoneNumber = HelpScreenStrings.Phone
        AppText(
            text = phoneNumber,
            textType = TextType.Link,
            textAlign = TextAlign.Start,
            modifier = Modifier.clickable(
                true,
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = "tel:$phoneNumber".toUri()
                    }
                    context.startActivity(intent)
                },
            ),
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.sm))
        AppText(
            text = HelpScreenStrings.Email,
            textType = TextType.Link,
            textAlign = TextAlign.Start,
            modifier = Modifier.clickable(true) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = "mailto:${HelpScreenStrings.Email}".toUri()
                }
                context.startActivity(intent)
            },
        )
        Spacer(modifier = Modifier.padding(bottom = MeTheme.spacing.md))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppText(
                text = HelpScreenStrings.UserManualSectionTitle,
                textType = TextType.Title,
            )
            AppIconButton(AppIcons.Outlined.Help) {
                handleIntent(HelpIntent.ShowModelNumberHelpPopup)
            }
        }
        AppText(
            text = HelpScreenStrings.UserManualSectionSubtitle,
            textType = TextType.Subtitle,
        )
    }
}

@PreviewTheme
@Composable
private fun HelpScreenPreview() {
    MeAppTheme {
        HelpContent(
            handleIntent = {},
        )
    }
}
