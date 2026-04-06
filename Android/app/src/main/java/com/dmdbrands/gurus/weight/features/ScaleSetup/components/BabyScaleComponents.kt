package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.BabyProfile
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import com.dmdbrands.gurus.weight.features.common.components.RadioButtonOption
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageDefaults
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageSize
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
fun ScaleNameContent(
  nickname: String,
  onNicknameChanged: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
    verticalArrangement = Arrangement.spacedBy(spacing.lg),
  ) {
    AppText(
      text = BabyScaleSetupStrings.ScaleName.Title,
      textType = TextType.Title,
      modifier = Modifier.fillMaxWidth(),
    )
    val focusRequester = remember { FocusRequester() }
    InputFieldBase<String>(
      value = nickname,
      onValueChange = { onNicknameChanged(it ?: "") },
      label = BabyScaleSetupStrings.ScaleName.Hint,
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
      modifier = Modifier.fillMaxWidth(),
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
        contentDescription = "Paired",
        modifier = Modifier.size(ScaleImageDefaults.size(ScaleImageSize.Large)),
      )
    }
  }
}

/** White card TextField — same style as AppInput (no border, white bg, rounded). */
@Composable
private fun CardTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  readOnly: Boolean = false,
  trailingIcon: @Composable (() -> Unit)? = null,
) {
  TextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(text = label, style = typography.body3, color = colorScheme.textSubheading) },
    singleLine = true,
    readOnly = readOnly,
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
      initialValue = DateTimeValue.Date(System.currentTimeMillis()),
      validators = emptyList(),
    )
  }
  val sexOptions = remember {
    listOf(
      RadioButtonOption(id = "Male", label = "Male"),
      RadioButtonOption(id = "Female", label = "Female"),
      RadioButtonOption(id = "Other", label = "Other"),
    )
  }

  if (showSexModal) {
    AppRadioGroupModal(
      title = BabyScaleSetupStrings.BabyProfileForm.SexHint,
      options = sexOptions,
      selectedItem = profile.biologicalSex?.ifEmpty { null },
      onCancel = { showSexModal = false },
      onOk = { selected ->
        if (selected != null) onProfileChanged(profile.copy(biologicalSex = selected))
        showSexModal = false
      },
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
  ) {
    AppText(text = BabyScaleSetupStrings.BabyProfileForm.Title, textType = TextType.Title, modifier = Modifier.fillMaxWidth())
    Spacer(modifier = Modifier.height(spacing.xs))
    AppText(text = BabyScaleSetupStrings.BabyProfileForm.Subtitle, textType = TextType.Body, modifier = Modifier.fillMaxWidth())
    Spacer(modifier = Modifier.height(spacing.lg))

    CardTextField(
      value = profile.name,
      onValueChange = { onProfileChanged(profile.copy(name = it)) },
      label = BabyScaleSetupStrings.BabyProfileForm.NameHint,
    )
    Spacer(modifier = Modifier.height(spacing.xs))

    AppText(text = BabyScaleSetupStrings.BabyProfileForm.BirthdayHint, textType = TextType.Subtitle, modifier = Modifier.padding(bottom = spacing.xs))
    DateTimeInput(
      formControl = birthdayForm,
      mode = DateTimeInputMode.Date,
      maxValue = DateTimeValue.Date(System.currentTimeMillis()),
    )
    Spacer(modifier = Modifier.height(spacing.sm))

    Box(modifier = Modifier.fillMaxWidth()) {
      CardTextField(
        value = profile.biologicalSex ?: "",
        onValueChange = {},
        label = BabyScaleSetupStrings.BabyProfileForm.SexHint,
        readOnly = true,
        trailingIcon = {
          Icon(
            painter = painterResource(id = com.dmdbrands.gurus.weight.R.drawable.ic_chevron_down),
            contentDescription = "Select",
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
          ) { showSexModal = true },
      )
    }
    Spacer(modifier = Modifier.height(spacing.md))

    CardTextField(
      value = profile.birthLength ?: "",
      onValueChange = { onProfileChanged(profile.copy(birthLength = it)) },
      label = BabyScaleSetupStrings.BabyProfileForm.BirthLengthHint,
    )
    Spacer(modifier = Modifier.height(spacing.sm))

    CardTextField(
      value = profile.birthWeight ?: "",
      onValueChange = { onProfileChanged(profile.copy(birthWeight = it)) },
      label = BabyScaleSetupStrings.BabyProfileForm.BirthWeightHint,
    )
  }
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
      .padding(vertical = spacing.md, horizontal = spacing.sm),
  ) {
    AppText(
      text = BabyScaleSetupStrings.BabyList.Title,
      textType = TextType.Title,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(spacing.lg))

    Column(verticalArrangement = Arrangement.spacedBy(spacing.x3s)) {
      babyProfiles.forEachIndexed { index, profile ->
        AppSwipeableListItem(
          onActionOpened = { openedIdx -> openIndex = openedIdx },
          isSwipeable = true,
          index = index,
          iconWidth = 56.dp,
          showAction = openIndex == index,
          actionContent = {
            AppSwipeableListActions {
              AppSwipeableActionItem(
                iconId = AppIcons.Default.Delete,
                contentDescription = "Delete",
                backgroundColor = MeTheme.colorScheme.danger,
              ) { onDeleteBaby(index) }
            }
          },
        ) {
          BaseListItem(
            title = profile.name.ifEmpty { "Baby ${index + 1}" },
            leadingContent = { BabyAvatar(name = profile.name.ifEmpty { "?" }) },
            trailingContent = {
              IconButton(onClick = { onEditBaby(index) }) {
                Icon(
                  painter = painterResource(AppIcons.Default.EditPencil),
                  contentDescription = "Edit",
                  tint = MeTheme.colorScheme.textBody,
                  modifier = Modifier.size(20.dp),
                )
              }
            },
          )
        }
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
private fun BabyAvatar(name: String, modifier: Modifier = Modifier) {
  val initial = name.firstOrNull()?.uppercase() ?: "?"
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
        modifier = Modifier.fillMaxWidth(),
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
        label = ScaleSetupStrings.SetupButtons.Support,
        type = ButtonType.InlineTextPrimary,
        onClick = onSupport,
      )
    }
  }
}
