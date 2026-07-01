package com.dmdbrands.gurus.weight.features.DeviceSetup.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceInfoContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DevicePermissions
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.DeviceSetupHeader
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SelectButton
import com.dmdbrands.gurus.weight.features.DeviceSetup.components.SetupContent
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStep
import com.dmdbrands.gurus.weight.features.DeviceSetup.enums.MonitorSetupStepHelper
import com.dmdbrands.gurus.weight.features.common.helper.DeviceHelper
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.SetupInitData
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.MonitorSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.MonitorSetupState
import com.dmdbrands.gurus.weight.features.DeviceSetup.reducer.DeviceSetupIntent
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.MonitorSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.viewmodel.MonitorSetupViewModel
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppInput
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.HorizontalPagerWithBottomNavigation
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonDisplayValue
import com.dmdbrands.gurus.weight.features.common.model.SelectButtonItem
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Unified BPM monitor setup screen — handles both A3 and A6 protocols via SKU.
 */
@Composable
fun BpmSetupScreen(sku: String) {
  val setupInit = SetupInitData(
    sku = sku,
    initialStep = MonitorSetupStep.MONITOR_DETAIL,
  )
  val viewModel: MonitorSetupViewModel =
    hiltViewModel<MonitorSetupViewModel, MonitorSetupViewModel.Factory> { factory ->
      factory.create(setupInit)
    }
  val state by viewModel.state.collectAsStateWithLifecycle()
  BpmSetupScreenContent(
    state = state,
    sku = sku,
    onIntent = viewModel::handleIntent,
  )
}

@Composable
fun BpmSetupScreenContent(
  state: MonitorSetupState,
  sku: String,
  onIntent: (DeviceSetupIntent) -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val pagerState = rememberPagerState { state.scaleSetupState.steps.size }
  val currentStep = state.step
  val primarySku = DeviceHelper.primaryBpmSku(sku)
  val setupType = MonitorSetupStepHelper.setupTypeForSku(sku)

  LaunchedEffect(currentStep) {
    val targetPage = state.steps.indexOf(currentStep)
    if (targetPage >= 0 && pagerState.currentPage != targetPage) {
      try {
        pagerState.animateScrollToPage(targetPage)
      } catch (e: Exception) {
        pagerState.scrollToPage(targetPage)
      }
    }
  }

  DeviceSetupHeader(
    sku = sku,
    onBack = { onIntent(DeviceSetupIntent.ExitSetup(state.isLastStep)) },
    onHelp = { onIntent(DeviceSetupIntent.OpenHelp) },
  ) {
    HorizontalPagerWithBottomNavigation(
      steps = state.steps,
      containerColor = MeTheme.colorScheme.secondaryBackground,
      pagerState = pagerState,
      leadingContent = {
        AppButton(
          type = ButtonType.TextPrimary,
          label = DeviceSetupStrings.backButton,
          size = ButtonSize.Small,
          enabled = state.backEnabled,
          onClick = { onIntent(DeviceSetupIntent.Back) },
        )
      },
      trailingContent = {
        AppButton(
          type = ButtonType.PrimaryFilled,
          label = if (state.isLastStep || currentStep == MonitorSetupStep.SUCCESS_SCREEN) DeviceSetupStrings.FinishButton else DeviceSetupStrings.nextButton,
          size = ButtonSize.Small,
          enabled = state.nextEnabled,
          onClick = {
            focusManager.clearFocus()
            onIntent(DeviceSetupIntent.Next)
          },
        )
      },
      pageContent = { step ->
        Column(modifier = Modifier.fillMaxSize()) {
          when (step) {
            MonitorSetupStep.MONITOR_DETAIL -> {
              DeviceInfoContent(sku = sku, setupType = setupType)
            }

            MonitorSetupStep.PERMISSIONS -> {
              DevicePermissions(
                sku = sku,
                permissions = state.scaleSetupState.permissions,
                onRequestPermission = { onIntent(DeviceSetupIntent.RequestPermission(it)) },
              )
            }

            MonitorSetupStep.USER_SELECTION -> {
              val selectedUser = state.selectedUser
              val userItems = if (state.hasNumericUsers) {
                listOf(
                  SelectButtonItem("1", SelectButtonDisplayValue.Image(AppIcons.Monitor.UserToggleImage(primarySku, "1")), emitValue = "1", isSelected = selectedUser == "1"),
                  SelectButtonItem("2", SelectButtonDisplayValue.Image(AppIcons.Monitor.UserToggleImage(primarySku, "2")), emitValue = "2", isSelected = selectedUser == "2"),
                )
              } else {
                listOf(
                  SelectButtonItem("A", SelectButtonDisplayValue.Image(AppIcons.Monitor.UserToggleImage(primarySku, "A")), emitValue = "A", isSelected = selectedUser == "A"),
                  SelectButtonItem("B", SelectButtonDisplayValue.Image(AppIcons.Monitor.UserToggleImage(primarySku, "B")), emitValue = "B", isSelected = selectedUser == "B"),
                )
              }
              SelectButton(
                title = MonitorSetupStrings.UserSelection.Title,
                subtitle = MonitorSetupStrings.UserSelection.Subtitle,
                selectButtonItems = userItems,
                isSelectable = true,
                imageWidth = 130.dp,
                imageHeight = 210.dp,
                onItemSelected = { value ->
                  onIntent(MonitorSetupIntent.SetSelectedUser(value))
                },
              )
            }

            MonitorSetupStep.POWER_SWITCH -> {
              SetupContent(
                title = MonitorSetupStrings.PowerSwitch.Title,
                subtitle = MonitorSetupStrings.PowerSwitch.Subtitle,
                noteMessage = MonitorSetupStrings.PowerSwitch.Note,
                supportingImage = AppIcons.Monitor.PowerSwitchImage,
              )
            }

            MonitorSetupStep.USER_CONFIRMATION -> {
              val selectedUser = state.selectedUser ?: if (state.hasNumericUsers) "1" else "A"
              val userLabel = "User $selectedUser"
              SetupContent(
                title = "${MonitorSetupStrings.UserConfirmation.Title} $userLabel",
                subtitle = MonitorSetupStrings.UserConfirmation.Subtitle(primarySku),
                supportingImage = AppIcons.Monitor.UserGif(primarySku, selectedUser),
                isGifImage = true,
              )
            }

            MonitorSetupStep.MONITOR_OFF -> {
              SetupContent(
                title = MonitorSetupStrings.MonitorOff.Title,
                subtitle = MonitorSetupStrings.MonitorOff.Subtitle(primarySku),
                supportingImage = AppIcons.Monitor.MonitorOffImage(primarySku),
                // supportingImageSize removed — SetupContent does not accept this parameter
              )
            }

            MonitorSetupStep.MEMORY_SELECTION -> {
              SetupContent(
                title = MonitorSetupStrings.MemorySelection.Title(primarySku),
                subtitle = MonitorSetupStrings.MemorySelection.Subtitle(primarySku),
                supportingImage = AppIcons.Monitor.PulseGif(primarySku),
                isGifImage = true,
              )
            }

            MonitorSetupStep.MONITOR_PAIRING -> {
              SetupContent(
                title = MonitorSetupStrings.Connectivity.SearchingTitle,
                subtitle = MonitorSetupStrings.Connectivity.SuccessSubtitle,
                supportingImage = AppIcons.Monitor.SyncingGif(primarySku),
                isGifImage = true,
                connectionState = state.scaleSetupState.setupState.connectionState,
                loaderText = MonitorSetupStrings.Connectivity.SearchingTitle,
                loaderClick = { onIntent(DeviceSetupIntent.TryAgain) },
              )
            }

            MonitorSetupStep.MONITOR_NICKNAME -> {
              val nicknameFormControl = remember(state.monitorNickname) {
                FormControl.create(
                  initialValue = state.monitorNickname,
                  onValueChangeCallback = { _, newValue ->
                    onIntent(MonitorSetupIntent.SetMonitorNickname(newValue))
                  },
                )
              }
              Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = spacing.sm, vertical = spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                AppText(
                  text = MonitorSetupStrings.MonitorNickname.Title,
                  textType = TextType.Title,
                  // TalkBack: step title is the heading.
                  modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.height(spacing.lg))
                AppInput(
                  formControl = nicknameFormControl,
                  label = MonitorSetupStrings.MonitorNickname.Label,
                  modifier = Modifier.fillMaxWidth(),
                  imeAction = ImeAction.Done,
                  onImeAction = { focusManager.clearFocus() },
                )
              }
            }

            // ── Success & instruction screens ─────────────────────────────────

            MonitorSetupStep.SUCCESS_SCREEN -> {
              // Companion scale is paired separately (not in this wizard), so every monitor shows
              // the same monitor-only success copy + "learn how to measure" tutorial link. (MOB-596)
              SetupContent(
                title = MonitorSetupStrings.SuccessScreen.Title,
                subtitle = MonitorSetupStrings.SuccessScreen.Subtitle,
                annotatedSubtitle = MonitorSetupStrings.SuccessScreen.TutorialLinkText,
                onAnnotationClick = { onIntent(MonitorSetupIntent.TutorialLinkClicked) },
                setupFinished = true,
              )
            }

            MonitorSetupStep.INSTRUCTION_CUFF -> {
              SetupContent(
                title = MonitorSetupStrings.InstructionCuff.Title,
                subtitle = MonitorSetupStrings.InstructionCuff.Subtitle(primarySku),
                supportingImage = AppIcons.Monitor.CuffGif(primarySku),
                isGifImage = true,
              )
            }

            MonitorSetupStep.INSTRUCTION_START -> {
              SetupContent(
                title = MonitorSetupStrings.InstructionStart.Title,
                subtitle = MonitorSetupStrings.InstructionStart.Subtitle(primarySku),
                supportingImage = AppIcons.Monitor.StartGif(primarySku),
                isGifImage = true,
              )
            }

            MonitorSetupStep.SETUP_COMPLETED -> {
              SetupContent(
                title = MonitorSetupStrings.SetupCompleted.Title,
                subtitle = MonitorSetupStrings.SetupCompleted.Subtitle,
                setupFinished = true,
              )
            }
          }
        }
      },
    )
  }
}
