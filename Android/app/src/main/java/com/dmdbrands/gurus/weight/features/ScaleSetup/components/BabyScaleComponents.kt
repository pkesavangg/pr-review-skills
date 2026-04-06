package com.dmdbrands.gurus.weight.features.ScaleSetup.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.ScaleSetup.modal.BabyProfile
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.BabyScaleSetupStrings
import com.dmdbrands.gurus.weight.features.ScaleSetup.strings.ScaleSetupStrings
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.InputFieldBase
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageDefaults
import com.dmdbrands.gurus.weight.features.common.components.ScaleImageSize
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
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

/**
 * Baby profile form — collects name, birthday, sex, birth length, birth weight.
 */
@Composable
fun BabyProfileFormContent(
  profile: BabyProfile,
  onProfileChanged: (BabyProfile) -> Unit,
  onSkip: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
    verticalArrangement = Arrangement.spacedBy(spacing.md),
  ) {
    AppText(
      text = BabyScaleSetupStrings.BabyProfileForm.Title,
      textType = TextType.Title,
      modifier = Modifier.fillMaxWidth(),
    )
    AppText(
      text = BabyScaleSetupStrings.BabyProfileForm.Subtitle,
      textType = TextType.Body,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = profile.name,
      onValueChange = { onProfileChanged(profile.copy(name = it)) },
      label = { Text(BabyScaleSetupStrings.BabyProfileForm.NameHint) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MeTheme.colorScheme.primaryAction,
        unfocusedBorderColor = MeTheme.colorScheme.secondaryAction,
      ),
    )

    OutlinedTextField(
      value = profile.birthday ?: "",
      onValueChange = { onProfileChanged(profile.copy(birthday = it)) },
      label = { Text(BabyScaleSetupStrings.BabyProfileForm.BirthdayHint) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MeTheme.colorScheme.primaryAction,
        unfocusedBorderColor = MeTheme.colorScheme.secondaryAction,
      ),
    )

    OutlinedTextField(
      value = profile.biologicalSex ?: "",
      onValueChange = { onProfileChanged(profile.copy(biologicalSex = it)) },
      label = { Text(BabyScaleSetupStrings.BabyProfileForm.SexHint) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MeTheme.colorScheme.primaryAction,
        unfocusedBorderColor = MeTheme.colorScheme.secondaryAction,
      ),
    )

    OutlinedTextField(
      value = profile.birthLength ?: "",
      onValueChange = { onProfileChanged(profile.copy(birthLength = it)) },
      label = { Text(BabyScaleSetupStrings.BabyProfileForm.BirthLengthHint) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MeTheme.colorScheme.primaryAction,
        unfocusedBorderColor = MeTheme.colorScheme.secondaryAction,
      ),
    )

    OutlinedTextField(
      value = profile.birthWeight ?: "",
      onValueChange = { onProfileChanged(profile.copy(birthWeight = it)) },
      label = { Text(BabyScaleSetupStrings.BabyProfileForm.BirthWeightHint) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MeTheme.colorScheme.primaryAction,
        unfocusedBorderColor = MeTheme.colorScheme.secondaryAction,
      ),
    )

    if (onSkip != null) {
      Spacer(modifier = Modifier.height(spacing.xs))
      AppButton(
        label = BabyScaleSetupStrings.BabyProfileForm.SkipButton,
        type = ButtonType.InlineTextPrimary,
        onClick = onSkip,
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
    }
  }
}

/**
 * Baby list step — displays added babies with edit/delete, and an "ADD A BABY" button.
 */
@Composable
fun BabyListContent(
  babyProfiles: ImmutableList<BabyProfile>,
  onEditBaby: (Int) -> Unit,
  onDeleteBaby: (Int) -> Unit,
  onAddBaby: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(vertical = spacing.md, horizontal = spacing.sm),
    verticalArrangement = Arrangement.spacedBy(spacing.md),
  ) {
    AppText(
      text = BabyScaleSetupStrings.BabyList.Title,
      textType = TextType.Title,
      modifier = Modifier.fillMaxWidth(),
    )

    babyProfiles.forEachIndexed { index, profile ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AppText(
          text = profile.name.ifEmpty { "Baby ${index + 1}" },
          textType = TextType.Body,
          modifier = Modifier.weight(1f),
        )
        Row {
          IconButton(onClick = { onEditBaby(index) }) {
            Icon(
              painter = painterResource(id = AppIcons.Setup.EditPencil),
              contentDescription = "Edit",
              tint = MeTheme.colorScheme.primaryAction,
            )
          }
          IconButton(onClick = { onDeleteBaby(index) }) {
            Icon(
              painter = painterResource(id = AppIcons.Default.Delete),
              contentDescription = "Delete",
              tint = MeTheme.colorScheme.errorAction,
            )
          }
        }
      }
      if (index < babyProfiles.lastIndex) {
        HorizontalDivider(color = MeTheme.colorScheme.secondaryActionDisabled)
      }
    }

    Spacer(modifier = Modifier.height(spacing.sm))

    OutlinedButton(
      onClick = onAddBaby,
      modifier = Modifier.align(Alignment.CenterHorizontally),
      border = BorderStroke(1.dp, MeTheme.colorScheme.primaryAction),
    ) {
      Text(
        text = BabyScaleSetupStrings.BabyList.AddBabyButton,
        style = MeTheme.typography.button1,
        color = MeTheme.colorScheme.primaryAction,
      )
    }
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
