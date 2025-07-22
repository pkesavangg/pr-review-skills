package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.greatergoods.libs.appsync.model.AppSyncResult
import com.greatergoods.meapp.features.common.model.ActionButton
import com.greatergoods.meapp.features.common.strings.AppPopupStrings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

/**
 * Popup dialog for displaying AppSync scan results with body composition metrics.
 *
 * @param entry The AppSync result containing body composition data
 * @param onEdit Callback when edit button is pressed
 * @param onSave Callback when save button is pressed
 */
@Composable
fun AppsyncEntryPopup(
  entry: AppSyncResult,
  onEdit: () -> Unit,
  onSave: () -> Unit,
) {
  BaseModal {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = spacing.md, horizontal = spacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(MeTheme.spacing.xs)
    ) {
      AppText(
        text = AppPopupStrings.AppsyncEntryPopup.Title,
        textType = TextType.Title,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = spacing.md)
      )
      // Body composition metrics - only show if values are not null
      entry.weight?.let { weight ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.Weight(weight),
          textType = TextType.Body
        )
      }

      entry.fat?.let { fat ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.Bodyfat(bodyfat = fat),
          textType = TextType.Body
        )
      }

      entry.muscle?.let { muscle ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.MuscleMass(muscleMass = muscle),
          textType = TextType.Body
        )
      }

      entry.water?.let { water ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.WaterWeight(waterWeight = water),
          textType = TextType.Body
        )
      }

      // Note: BMI would need to be added to AppSyncResult model if it's calculated elsewhere
      // For now, this is commented out until BMI is available in the model
      // entry.bmi?.let { bmi ->
      //   AppText(
      //     text = AppPopupStrings.AppsyncEntryPopup.Bmi(bmi),
      //     textType = TextType.Body
      //   )
      // }

      Spacer(modifier = Modifier.height(spacing.md))
      AppButton(
        label = AppPopupStrings.AppsyncEntryPopup.SaveButton,
        type = ButtonType.PrimaryFilled,
        onClick = onSave,
        modifier = Modifier.fillMaxWidth()
      )
      AppButton(
        label = AppPopupStrings.AppsyncEntryPopup.EditButton,
        type = ButtonType.InlineTextPrimary,
        onClick = onSave,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

@PreviewTheme
@Composable
fun AppsyncEntryPopupPreview() {
  MeAppTheme {
    val mockEntry = AppSyncResult(
      weight = 85.1f,
      fat = 28.2f,
      muscle = 31.9f,
      water = 50.5f,
      mode = "kg"
    )

    AppsyncEntryPopup(
      entry = mockEntry,
      onEdit = {},
      onSave = {}
    )
  }

}

