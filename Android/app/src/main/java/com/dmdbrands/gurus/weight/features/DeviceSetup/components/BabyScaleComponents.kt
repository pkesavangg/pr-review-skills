package com.dmdbrands.gurus.weight.features.DeviceSetup.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.DeviceSetup.modal.BabyProfile
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.dmdbrands.gurus.weight.features.common.components.dismissKeyboardOnTap
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppRadioGroupModal
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableActionItem
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListActions
import com.dmdbrands.gurus.weight.features.common.components.AppSwipeableListItem
import com.dmdbrands.gurus.weight.features.common.components.BaseListItem
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.InputFieldBase
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.DeviceImageDefaults
import com.dmdbrands.gurus.weight.features.common.components.DeviceImageSize
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.form.FormControl
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.borderRadius
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.gurus.weight.theme.MeTheme.typography
import kotlinx.collections.immutable.ImmutableList

/**
 * Scale naming step — allows user to set a nickname for the baby scale.
 */
@Composable
fun DeviceNameContent(
  nickname: String,
  onNicknameChanged: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm)
      .dismissKeyboardOnTap(),
    verticalArrangement = Arrangement.spacedBy(spacing.lg),
  ) {
    AppText(
      text = BabyScaleSetupStrings.DeviceName.Title,
      textType = TextType.Title,
      // TalkBack: step title is the heading.
      modifier = Modifier
        .fillMaxWidth()
        .semantics { heading() },
    )
    val focusRequester = remember { FocusRequester() }
    InputFieldBase<String>(
      value = nickname,
      onValueChange = { onNicknameChanged(it ?: "") },
      label = BabyScaleSetupStrings.DeviceName.Hint,
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      onImeAction = { focusRequester.freeFocus() },
      focusRequester = focusRequester,
    )
  }
}

/**
 * Paired success step — green checkmark with prompt to add baby profile.
 */
@Composable
fun PairedSuccessContent(
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(vertical = spacing.md, horizontal = spacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppText(
      text = BabyScaleSetupStrings.PairedSuccess.Title,
      textType = TextType.Title,
      // TalkBack: step title is the heading.
      modifier = Modifier
        .fillMaxWidth()
        .semantics { heading() },
    )
    Spacer(modifier = Modifier.height(spacing.xs))
    AppText(
      text = BabyScaleSetupStrings.PairedSuccess.Subtitle,
      textType = TextType.Body,
      modifier = Modifier.fillMaxWidth(),
    )
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter = painterResource(id = AppIcons.Setup.BabyScalePairedCheck),
        contentDescription = BabyScaleSetupStrings.accPairedImage,
        modifier = Modifier.size(DeviceImageDefaults.size(DeviceImageSize.Large)),
      )
    }
  }
}

/**
 * Final "You're Done!" step — green checkmark confirming setup is complete.
 */
@Composable
fun SetupFinishedContent(
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(vertical = spacing.md, horizontal = spacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AppText(
      text = BabyScaleSetupStrings.SetupComplete.Title,
      textType = TextType.Title,
      // TalkBack: step title is the heading.
      modifier = Modifier
        .fillMaxWidth()
        .semantics { heading() },
    )
    Spacer(modifier = Modifier.height(spacing.xs))
    AppText(
      text = BabyScaleSetupStrings.SetupComplete.Subtitle,
      textType = TextType.Body,
      modifier = Modifier.fillMaxWidth(),
    )
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Image(
        painter = painterResource(id = AppIcons.Setup.BabyScalePairedCheck),
        contentDescription = BabyScaleSetupStrings.accDoneImage,
        modifier = Modifier.size(DeviceImageDefaults.size(DeviceImageSize.Large)),
      )
    }
  }
}

/** White card TextField — used for read-only fields (e.g. sex selector) where no keyboard appears. */
@Composable
private fun CardTextField(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  trailingIcon: @Composable (() -> Unit)? = null,
) {
  TextField(
    value = value,
    onValueChange = {},
    label = { Text(text = label, style = typography.body3, color = colorScheme.textSubheading) },
    singleLine = true,
    readOnly = true,
    textStyle = typography.body2,
    modifier = modifier.fillMaxWidth().height(56.dp),
    shape = RoundedCornerShape(borderRadius.sm),
    trailingIcon = trailingIcon,
    colors = TextFieldDefaults.colors(
      focusedContainerColor = colorScheme.primaryBackground,
      unfocusedContainerColor = colorScheme.primaryBackground,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent,
      focusedTextColor = colorScheme.textBody,
      unfocusedTextColor = colorScheme.textBody,
      cursorColor = colorScheme.primaryAction,
    ),
  )
}

/**
 * Baby profile form — matches signup AddBabyStep UI:
 * white card inputs, date picker chip, sex dropdown modal.
 */
@Composable
fun BabyProfileFormContent(
  profile: BabyProfile,
  onProfileChanged: (BabyProfile) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showSexModal by remember { mutableStateOf(false) }
  val birthdayForm = remember {
    FormControl.create<DateTimeValue>(
      initialValue = DateTimeValue.Date(
        profile.birthday?.let { DateTimeValue.getEpochMillisFromDateString(it) }
          ?: System.currentTimeMillis(),
      ),
      validators = emptyList(),
    )
  }

  val currentProfile by rememberUpdatedState(profile)

  BabyBirthdaySync(
    birthdayForm = birthdayForm,
    profile = profile,
    onProfileChanged = onProfileChanged,
  )

  if (showSexModal) {
    BabySexModal(
      selectedItem = profile.biologicalSex?.ifEmpty { null },
      onSelectSex = { selected -> onProfileChanged(currentProfile.copy(biologicalSex = selected)) },
      onDismiss = { showSexModal = false },
    )
  }

  val nameFocusRequester = remember { FocusRequester() }
  val birthLengthFocusRequester = remember { FocusRequester() }
  val birthWeightFocusRequester = remember { FocusRequester() }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
  ) {
    BabyProfileFormFieldsTop(
      profile = profile,
      onProfileChanged = onProfileChanged,
      birthdayForm = birthdayForm,
      nameFocusRequester = nameFocusRequester,
      birthLengthFocusRequester = birthLengthFocusRequester,
      onShowSexModal = { showSexModal = true },
    )
    BabyProfileFormFieldsBottom(
      profile = profile,
      onProfileChanged = onProfileChanged,
      birthLengthFocusRequester = birthLengthFocusRequester,
      birthWeightFocusRequester = birthWeightFocusRequester,
    )
  }
}

@Composable
private fun BabyBirthdaySync(
  birthdayForm: FormControl<DateTimeValue>,
  profile: BabyProfile,
  onProfileChanged: (BabyProfile) -> Unit,
) {
  val currentProfile by rememberUpdatedState(profile)
  LaunchedEffect(birthdayForm) {
    snapshotFlow { birthdayForm.value }
      .collect { dateValue ->
        val formatted = when (dateValue) {
          is DateTimeValue.Date -> DateTimeValue.getDateFormatFromMilliseconds(dateValue.millis)
          else -> null
        }
        if (formatted != null && formatted != currentProfile.birthday) {
          onProfileChanged(currentProfile.copy(birthday = formatted))
        }
      }
  }
}

@Composable
private fun BabySexModal(
  selectedItem: String?,
  onSelectSex: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val sexOptions = remember {
    listOf(
      RadioButtonOption(id = BabyScaleSetupStrings.BabyProfileForm.SexMale, label = BabyScaleSetupStrings.BabyProfileForm.SexMale),
      RadioButtonOption(id = BabyScaleSetupStrings.BabyProfileForm.SexFemale, label = BabyScaleSetupStrings.BabyProfileForm.SexFemale),
      RadioButtonOption(id = BabyScaleSetupStrings.BabyProfileForm.SexOther, label = BabyScaleSetupStrings.BabyProfileForm.SexOther),
    )
  }
  AppRadioGroupModal(
    title = BabyScaleSetupStrings.BabyProfileForm.SexHint,
    options = sexOptions,
    selectedItem = selectedItem,
    onCancel = onDismiss,
    onOk = { selected ->
      if (selected != null) onSelectSex(selected)
      onDismiss()
    },
  )
}

@Composable
private fun BabyProfileFormFieldsTop(
  profile: BabyProfile,
  onProfileChanged: (BabyProfile) -> Unit,
  birthdayForm: FormControl<DateTimeValue>,
  nameFocusRequester: FocusRequester,
  birthLengthFocusRequester: FocusRequester,
  onShowSexModal: () -> Unit,
) {
  AppText(text = BabyScaleSetupStrings.BabyProfileForm.Title, textType = TextType.Title, modifier = Modifier.fillMaxWidth().semantics { heading() })
  Spacer(modifier = Modifier.height(spacing.xs))
  AppText(text = BabyScaleSetupStrings.BabyProfileForm.Subtitle, textType = TextType.Body, modifier = Modifier.fillMaxWidth())
  Spacer(modifier = Modifier.height(spacing.lg))

  InputFieldBase<String>(
    value = profile.name,
    onValueChange = { onProfileChanged(profile.copy(name = it ?: "")) },
    label = BabyScaleSetupStrings.BabyProfileForm.NameHint,
    modifier = Modifier.fillMaxWidth(),
    focusRequester = nameFocusRequester,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    onImeAction = { birthLengthFocusRequester.requestFocus() },
  )
  Spacer(modifier = Modifier.height(spacing.xs))

  AppText(text = BabyScaleSetupStrings.BabyProfileForm.BirthdayHint, textType = TextType.Subtitle, modifier = Modifier.padding(bottom = spacing.xs))
  DateTimeInput(
    formControl = birthdayForm,
    mode = DateTimeInputMode.Date,
    maxValue = DateTimeValue.Date(System.currentTimeMillis()),
    // DOB: calendar grid only to prevent silent leap-day normalization. (MOB-868)
    showModeToggle = false,
  )
  Spacer(modifier = Modifier.height(spacing.sm))

  Box(modifier = Modifier.fillMaxWidth()) {
    CardTextField(
      value = profile.biologicalSex ?: "",
      label = BabyScaleSetupStrings.BabyProfileForm.SexHint,
      trailingIcon = {
        Icon(
          painter = painterResource(id = com.dmdbrands.gurus.weight.R.drawable.ic_chevron_down),
          contentDescription = BabyScaleSetupStrings.BabyProfileForm.SexSelectContentDescription,
          tint = colorScheme.textBody,
        )
      },
    )
    Box(
      modifier = Modifier
        .matchParentSize()
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
        ) { onShowSexModal() },
    )
  }
  Spacer(modifier = Modifier.height(spacing.md))
}

@Composable
private fun BabyProfileFormFieldsBottom(
  profile: BabyProfile,
  onProfileChanged: (BabyProfile) -> Unit,
  birthLengthFocusRequester: FocusRequester,
  birthWeightFocusRequester: FocusRequester,
) {
  InputFieldBase<String>(
    value = profile.birthLength ?: "",
    onValueChange = { onProfileChanged(profile.copy(birthLength = it)) },
    label = BabyScaleSetupStrings.BabyProfileForm.BirthLengthHint,
    modifier = Modifier.fillMaxWidth(),
    focusRequester = birthLengthFocusRequester,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    onImeAction = { birthWeightFocusRequester.requestFocus() },
  )
  Spacer(modifier = Modifier.height(spacing.sm))

  InputFieldBase<String>(
    value = profile.birthWeight ?: "",
    onValueChange = { onProfileChanged(profile.copy(birthWeight = it)) },
    label = BabyScaleSetupStrings.BabyProfileForm.BirthWeightHint,
    modifier = Modifier.fillMaxWidth(),
    focusRequester = birthWeightFocusRequester,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
    onImeAction = { birthWeightFocusRequester.freeFocus() },
  )
}

/**
 * Baby list step — matches signup BabyAddedStep: avatar, swipe-to-delete, edit icon, filled button.
 */
@Composable
fun BabyListContent(
  babyProfiles: ImmutableList<BabyProfile>,
  onEditBaby: (Int) -> Unit,
  onDeleteBaby: (Int) -> Unit,
  onAddBaby: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var openIndex by remember { mutableStateOf<Int?>(null) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
  ) {
    AppText(
      text = BabyScaleSetupStrings.BabyList.Title,
      textType = TextType.Title,
      // TalkBack: step title is the heading.
      modifier = Modifier
        .fillMaxWidth()
        .semantics { heading() },
    )
    Spacer(modifier = Modifier.height(spacing.lg))

    Column(verticalArrangement = Arrangement.spacedBy(spacing.x3s)) {
      babyProfiles.forEachIndexed { index, profile ->
        BabyProfileRow(
          index = index,
          profile = profile,
          showAction = openIndex == index,
          onActionOpened = { openedIdx -> openIndex = openedIdx },
          onDeleteBaby = onDeleteBaby,
          onEditBaby = onEditBaby,
        )
      }
    }

    Spacer(modifier = Modifier.height(spacing.lg))

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      AppButton(
        label = BabyScaleSetupStrings.BabyList.AddBabyButton,
        onClick = onAddBaby,
        type = ButtonType.PrimaryFilled,
        size = ButtonSize.Small,
      )
    }
  }
}

@Composable
private fun BabyProfileRow(
  index: Int,
  profile: BabyProfile,
  showAction: Boolean,
  onActionOpened: (Int?) -> Unit,
  onDeleteBaby: (Int) -> Unit,
  onEditBaby: (Int) -> Unit,
) {
  AppSwipeableListItem(
    onActionOpened = onActionOpened,
    isSwipeable = true,
    index = index,
    iconWidth = 56.dp,
    showAction = showAction,
    actionContent = {
      AppSwipeableListActions {
        AppSwipeableActionItem(
          iconId = AppIcons.Default.Delete,
          contentDescription = BabyScaleSetupStrings.BabyList.DeleteContentDescription,
          backgroundColor = MeTheme.colorScheme.danger,
        ) { onDeleteBaby(index) }
      }
    },
  ) {
    BaseListItem(
      title = profile.name.ifEmpty { BabyScaleSetupStrings.BabyList.BabyFallbackName(index) },
      leadingContent = {
        BabyAvatar(name = profile.name.ifEmpty { BabyScaleSetupStrings.BabyList.AvatarInitialFallback })
      },
      trailingContent = {
        IconButton(onClick = { onEditBaby(index) }) {
          Icon(
            painter = painterResource(AppIcons.Default.EditPencil),
            contentDescription = BabyScaleSetupStrings.BabyList.EditContentDescription,
            tint = MeTheme.colorScheme.textBody,
            modifier = Modifier.size(20.dp),
          )
        }
      },
    )
  }
}

@Composable
private fun BabyAvatar(name: String, modifier: Modifier = Modifier) {
  val initial = name.firstOrNull()?.uppercase() ?: BabyScaleSetupStrings.BabyList.AvatarInitialFallback
  Box(
    modifier = modifier
      .size(40.dp)
      .clip(CircleShape)
      .background(MeTheme.colorScheme.secondaryBackground),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = initial,
      style = MeTheme.typography.heading5,
      color = MeTheme.colorScheme.textBody,
      textAlign = TextAlign.Center,
    )
  }
}

/**
 * Connection failed screen for baby scale — left-aligned title/subtitle at top,
 * PAIR AGAIN + SUPPORT buttons at bottom.
 */
@Composable
fun BabyScaleConnectionFailed(
  title: String,
  subtitle: String,
  onPairAgain: () -> Unit,
  onSupport: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = spacing.sm, vertical = spacing.md),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      AppText(
        text = title,
        textType = TextType.Title,
        // TalkBack: connection-failed title is the heading.
        modifier = Modifier
          .fillMaxWidth()
          .semantics { heading() },
      )
      Spacer(modifier = Modifier.height(spacing.xs))
      AppText(
        text = subtitle,
        textType = TextType.Body,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AppButton(
        label = BabyScaleSetupStrings.SetupButtons.PairAgain,
        type = ButtonType.PrimaryFilled,
        onClick = onPairAgain,
      )
      Spacer(modifier = Modifier.height(spacing.xs))
      AppButton(
        label = DeviceSetupStrings.SetupButtons.Support,
        type = ButtonType.InlineTextPrimary,
        onClick = onSupport,
      )
    }
  }
}
