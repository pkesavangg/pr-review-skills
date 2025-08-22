package com.dmdbrands.gurus.weight.features.common.components

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
import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.BodyScaleEntryEntity
import com.dmdbrands.gurus.weight.data.storage.db.entity.entry.EntryEntity
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntry
import com.dmdbrands.gurus.weight.domain.model.storage.entry.ScaleEntryWithMetrics
import com.dmdbrands.gurus.weight.features.common.strings.AppPopupStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Popup dialog for displaying AppSync scan results with body composition metrics.
 *
 * @param entry The AppSync result containing body composition data
 * @param onEdit Callback when edit button is pressed
 * @param onSave Callback when save button is pressed
 */
@Composable
fun AppsyncEntryPopup(
  entry: ScaleEntry,
  onEdit: () -> Unit,
  onSave: () -> Unit,
) {

  val scaleEntry = entry.scale.scaleEntry
  BaseModal {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = spacing.md, horizontal = spacing.sm),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
      AppText(
        text = AppPopupStrings.AppsyncEntryPopup.Title,
        textType = TextType.Title,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = spacing.md),
      )
      val isMetric = entry.entry.unit.value.lowercase() == "kg"
      val conversionWeight = ConversionTools.convertStoredToDisplay(scaleEntry.weight, isMetric)
      val displayBmi = scaleEntry.bmi?.toFloat()
      val displayBodyFat = scaleEntry.bodyFat?.toFloat()
      val displayWater = scaleEntry.water?.toFloat()
      val displayMuscleMass = scaleEntry.muscleMass?.toFloat()

      AppText(
        text = AppPopupStrings.AppsyncEntryPopup.Weight(conversionWeight.toFloat(), entry.entry.unit.value),
        textType = TextType.Body,
      )

      displayBodyFat?.let { fat ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.Bodyfat(bodyfat = fat),
          textType = TextType.Body,
        )
      }

      displayMuscleMass?.let { muscle ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.MuscleMass(muscleMass = muscle),
          textType = TextType.Body,
        )
      }

      displayWater?.let { water ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.WaterWeight(waterWeight = water),
          textType = TextType.Body,
        )
      }

      // Display BMI if available
      displayBmi?.let { bmi ->
        AppText(
          text = AppPopupStrings.AppsyncEntryPopup.Bmi(bmi),
          textType = TextType.Body,
        )
      }

      Spacer(modifier = Modifier.height(spacing.md))
      AppButton(
        label = AppPopupStrings.AppsyncEntryPopup.SaveButton,
        type = ButtonType.PrimaryFilled,
        onClick = onSave,
        modifier = Modifier.fillMaxWidth(),
      )
      AppButton(
        label = AppPopupStrings.AppsyncEntryPopup.EditButton,
        type = ButtonType.InlineTextPrimary,
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@PreviewTheme
@Composable
fun AppsyncEntryPopupPreview() {
  MeAppTheme {
    val mockEntry = ScaleEntry(
      entry = EntryEntity(
        id = 1L,
        accountId = "preview-account-123",
        entryTimestamp = System.currentTimeMillis().toString(),
        serverTimestamp = null,
        opTimestamp = null,
        unit = WeightUnit.KG,
        operationType = "CREATE",
        deviceType = "appsync",
        deviceId = "appsync-scale-001",
        attempts = 0,
        isSynced = false,
      ),
      scale = ScaleEntryWithMetrics(
        scaleEntry = BodyScaleEntryEntity(
          id = 1L,
          weight = 85.1,
          bodyFat = 28.2,
          muscleMass = 31.9,
          water = 50.5,
          bmi = 35.5,
          source = "Appsync scale",
        ),
        scaleEntryMetric = null,
      ),
    )

    AppsyncEntryPopup(
      entry = mockEntry,
      onEdit = {},
      onSave = {},
    )
  }
}

