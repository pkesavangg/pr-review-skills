package com.dmdbrands.gurus.weight.features.profile.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.core.shared.utilities.DateTimeUtil
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppStyledCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.HeightInput
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SettingsSection
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.common.model.SettingsItem
import com.dmdbrands.gurus.weight.features.common.model.SettingsItemType
import com.dmdbrands.gurus.weight.features.profile.model.ProfileFormControls
import com.dmdbrands.gurus.weight.features.profile.model.ProfileIntent
import com.dmdbrands.gurus.weight.features.profile.model.ProfileState
import com.dmdbrands.gurus.weight.features.profile.strings.ProfileStrings
import com.dmdbrands.gurus.weight.features.profile.viewmodel.ProfileViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * Profile screen composable. Displays the profile form, handles user input, and shows loading/error states.
 */
@Composable
fun ProfileScreen() {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProfileContent(state, viewModel::handleIntent) {
        viewModel.handleIntent(ProfileIntent.OnRequestBack)
    }
}

@Composable
private fun ProfileContent(state: ProfileState, handleIntent: (ProfileIntent) -> Unit,onBack: () -> Unit,) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    // Focus requesters for proper focus management
    val firstNameFocusRequester = remember { FocusRequester() }
    val lastNameFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }
    val zipcodeFocusRequester = remember { FocusRequester() }
    val birthdayFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

  BackHandler {
        onBack()
    }
    AppScaffold(
        title = ProfileStrings.ScreenTitle,
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) { onBack() }
        },
        actions = {
            AppButton(ProfileStrings.SaveButton, enabled = state.form.isValid && state.form.isDirty, type = ButtonType.InlineTextPrimary, size = ButtonSize.Small) {
                handleIntent.invoke(ProfileIntent.Submit)
            }
        },
        borderColor = colorScheme.utility,
        modifier = Modifier
    ) { scaffoldModifier ->
            AppStyledCard {
                Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { focusManager.clearFocus() },
                      )
                      .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Spacer(modifier = Modifier.padding(top = MeTheme.spacing.md))
                  // First Name Input
                  AppInput(
                    formControl = state.form.controls.firstName,
                    label = ProfileStrings.FirstNameLabel,
                    type = AppInputType.TEXT,
                    showTrailingIcon = true,
                    imeAction = ImeAction.Next,
                    nextFocusRequester = lastNameFocusRequester,
                    modifier = Modifier
                      .semantics { contentType = ContentType.PersonFirstName }
                      .focusRequester(firstNameFocusRequester),
                  )
                  // Last Name Input
                  AppInput(
                    formControl = state.form.controls.lastName,
                    label = ProfileStrings.LastNameLabel,
                    type = AppInputType.TEXT,
                    showTrailingIcon = true,
                    imeAction = ImeAction.Next,
                    nextFocusRequester = emailFocusRequester,
                    modifier = Modifier
                      .semantics { contentType = ContentType.PersonLastName }
                      .focusRequester(lastNameFocusRequester),
                  )
                    // Email Input
                    AppInput(
                        formControl = state.form.controls.email,
                        label = ProfileStrings.EmailLabel,
                        type = AppInputType.EMAIL,
                        showTrailingIcon = true,
                        imeAction = ImeAction.Next,
                        nextFocusRequester = zipcodeFocusRequester,
                        modifier = Modifier
                          .semantics { contentType = ContentType.EmailAddress }
                          .focusRequester(emailFocusRequester),
                    )
                    // Zipcode Input
                    AppInput(
                        formControl = state.form.controls.zipcode,
                        label = ProfileStrings.ZipcodeLabel,
                        type = AppInputType.TEXT,
                        showTrailingIcon = true,
                        imeAction = ImeAction.Next,
                        nextFocusRequester = birthdayFocusRequester,
                        modifier = Modifier
                          .semantics { contentType = ContentType.PostalCode }
                          .focusRequester(zipcodeFocusRequester),
                    )
                    AppText(ProfileStrings.BirthdayLabel, TextType.Title, spacing = MeTheme.spacing.sm)
                    DateTimeInput(
                        formControl = state.form.controls.birthday,
                        mode = DateTimeInputMode.Date,
                        maxValue = DateTimeValue.Date(DateTimeUtil.getMinBirthdayOffsetForDatePicker()),
                        modifier = Modifier.focusRequester(birthdayFocusRequester),
                    )
                  Spacer(modifier = Modifier.padding(top = MeTheme.spacing.md))
                  // Biological Sex dropdown with note
                  SettingsSection(
                    hasBottomSpace = false,
                    items = listOf(
                      SettingsItem(
                        title = ProfileStrings.BiologicalSexLabel,
                        type = SettingsItemType.Dropdown(
                          state.form.controls.gender.value
                            .takeIf { it.isNotEmpty() }
                            ?.replaceFirstChar { it.uppercase() }
                            ?: ProfileStrings.NotSet,
                        ),
                        onClick = { handleIntent(ProfileIntent.ShowBiologicalSexModal) },
                      )
                    ),
                  )
                  AppText(
                    ProfileStrings.BiologicalSexNote,
                    TextType.SubHeading,
                  )
                  Spacer(modifier = Modifier.padding(top = MeTheme.spacing.md))
                  // Height dropdown with note
                  SettingsSection(
                    hasBottomSpace = false,
                    items = listOf(
                      SettingsItem(
                        title = ProfileStrings.HeightLabel,
                        type = SettingsItemType.Dropdown(
                          HeightInput.formatHeightDisplay(
                            height = state.form.controls.height.value.takeIf { it > 0 },
                            isMetric = state.weightUnit == WeightUnit.KG,
                          ),
                        ),
                        onClick = { handleIntent(ProfileIntent.ShowHeightModal) },
                      ),
                    ),
                  )
                  AppText(
                    ProfileStrings.HeightNote,
                    TextType.SubHeading,
                  )
                  Spacer(Modifier.padding(bottom = MeTheme.spacing.xl))

                }
            }
    }
}

@PreviewTheme()
@Composable
fun ProfileScreenPreview() {
    MeAppTheme {
        val dummyProfileState = ProfileState(
            form = FormGroup(
                controls = ProfileFormControls.create(
                    firstName = "fg",
                    lastName = "gg",
                    email = "renu5@gg.com",
                    zipcode = "12345",
                    birthday = DateTimeValue.Date(
                        LocalDate
                            .parse("2000-01-01")
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    )
                ),
            ),
            isLoading = false,
            error = null,
        )
        ProfileContent(
            dummyProfileState,
            handleIntent = {}
        ) {}
    }
}
