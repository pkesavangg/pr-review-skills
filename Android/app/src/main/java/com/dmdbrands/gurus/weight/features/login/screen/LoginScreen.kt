package com.dmdbrands.gurus.weight.features.login.screen

import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppInputType
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.login.model.LoginFormControls
import com.dmdbrands.gurus.weight.features.login.model.LoginIntent
import com.dmdbrands.gurus.weight.features.login.model.LoginState
import com.dmdbrands.gurus.weight.features.login.strings.LoginStrings
import com.dmdbrands.gurus.weight.features.login.viewmodel.LoginViewModel
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

/**
 * Login screen composable. Displays the login form, handles user input, and shows loading/error states.
 */
@Composable
fun LoginScreen(email: String? = null) {
  val viewmodel: LoginViewModel =
    hiltViewModel<LoginViewModel, LoginViewModel.Factory>(
      creationCallback = { factory ->
        factory.create(email)
      },
    )
  val state by viewmodel.state.collectAsStateWithLifecycle()
  BackHandler {
    viewmodel.handleIntent(LoginIntent.OnRequestBack)
  }
  LoginContent(state, viewmodel::handleIntent)
}

@Composable
private fun LoginContent(
  state: LoginState,
  handleIntent: (LoginIntent) -> Unit,
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val interactionSource = remember { MutableInteractionSource() }
  val emailFocusRequester = remember { FocusRequester() }
  val passwordFocusRequester = remember { FocusRequester() }

  val context = LocalContext.current
  val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }

  LaunchedEffect(state.error) {
    if (state.error != null) {
      autofillManager?.cancel()
    }
  }

  AppScaffold(
    title = null,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close) { handleIntent(LoginIntent.OnRequestBack) }
    },
    actions = {
      AppIconButton(AppIcons.Outlined.Help) { handleIntent(LoginIntent.OpenHelpModal) }
    },
    containerColor = colorScheme.secondaryBackground,
    appBarColor = colorScheme.secondaryBackground,
    borderColor = Color.Transparent,
  ) { scaffoldModifier ->
    Column(
      modifier = scaffoldModifier
        .fillMaxSize().padding(horizontal = spacing.xs)
        .verticalScroll(rememberScrollState()),
    ) {
      Spacer(Modifier.weight(1f))
      AppText(
        text = LoginStrings.WelcomeBack,
        textType = TextType.Title,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = spacing.sm),
      )
      Spacer(Modifier.height(spacing.xl))
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm)
            .clickable(
              interactionSource = interactionSource,
              indication = null,
              onClick = { focusManager.clearFocus() },
            ),
      ) {
        AppInput(
          formControl = state.form.controls.email,
          label = LoginStrings.EmailLabel,
          type = AppInputType.EMAIL,
          showTrailingIcon = true,
          imeAction = ImeAction.Next,
          nextFocusRequester = passwordFocusRequester,
          modifier =
            Modifier
              .semantics { contentType = ContentType.Username }
              .focusRequester(emailFocusRequester),
        )
        AppInput(
          formControl = state.form.controls.password,
          label = LoginStrings.PasswordLabel,
          type = AppInputType.PASSWORD,
          showTrailingIcon = true,
          imeAction = ImeAction.Done,
          onImeAction = {
            handleIntent(LoginIntent.Submit)
            focusManager.clearFocus()
            keyboardController?.hide()
          },
          modifier =
            Modifier
              .semantics { contentType = ContentType.Password }
              .focusRequester(passwordFocusRequester),
        )
        Spacer(Modifier.height(spacing.xl))
        AppButton(
          label = LoginStrings.LoginButton,
          enabled = state.form.isValid,
          modifier = Modifier.align(Alignment.CenterHorizontally),
          onClick = {
            keyboardController?.hide()
            handleIntent(LoginIntent.Submit)
          },
        )
        Spacer(Modifier.height(spacing.md))
        AppButton(
          label = LoginStrings.ForgotPassword,
          type = ButtonType.TextPrimary,
          size = ButtonSize.Small,
          modifier = Modifier.align(Alignment.CenterHorizontally),
          onClick = { handleIntent(LoginIntent.OpenForgotPasswordModal) },
        )
      }
      Spacer(Modifier.weight(1f))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = spacing.x2l),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        AppText(
          text = LoginStrings.TermsAgreement,
          textType = TextType.Subtitle,
        )
        Spacer(Modifier.height(spacing.x2s))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Absolute.Center,
        ) {
          AppText(
            text = LoginStrings.TermsOfService,
            textType = TextType.Link2,
            onClick = {
              handleIntent(LoginIntent.OpenInAppBrowser(LoginStrings.TermsOfServiceUrl))
            },
          )
          Spacer(Modifier.padding(start = spacing.x3s))
          Text(LoginStrings.And, style = typography.subHeading2, color = colorScheme.textBody)
          Spacer(Modifier.padding(end = spacing.x3s))
          AppText(
            text = LoginStrings.PrivacyPolicy,
            textType = TextType.Link2,
            onClick = {
              handleIntent(LoginIntent.OpenInAppBrowser(LoginStrings.PrivacyPolicyUrl))
            },
          )
        }
      }
    }
  }
}

@PreviewTheme()
@Composable
fun LoginScreenPreview() {
  MeAppTheme {
    val dummyLoginState =
      LoginState(
        form =
          FormGroup(
            controls =
              LoginFormControls(
                email = FormControl.create(""),
                password = FormControl.create(""),
              ),
          ),
        isLoading = false,
        error = null,
      )

    LoginContent(
      state = dummyLoginState,
      handleIntent = {},
    )
  }
}
