package com.dmdbrands.gurus.weight.features.deviceDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.core.shared.utilities.testing.TestTags
import com.dmdbrands.gurus.weight.features.common.components.AppButton
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.ButtonSize
import com.dmdbrands.gurus.weight.features.common.components.ButtonType
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInput
import com.dmdbrands.gurus.weight.features.common.components.DateTimeInputMode
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonData
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonGroup
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonSize
import com.dmdbrands.gurus.weight.features.common.components.SegmentButtonType
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsIntent
import com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceDetailsState
import com.dmdbrands.gurus.weight.features.deviceDetails.strings.DeviceDetailsStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing

/**
 * Software Update Screen - similar to Angular's SoftwareUpdateModalComponent
 * Shows firmware update options and information
 */
@Composable
fun SoftwareUpdateScreen(
  state: DeviceDetailsState,
  handleIntent: (DeviceDetailsIntent) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler {
    onClose()
  }
  val device = state.scale
  val currentVersion = device?.device?.firmwareRevision ?: ""
  val latestVersion = state.scale?.latestVersion ?: ""
  val isUpToDate = currentVersion.isNotEmpty() && latestVersion.isNotEmpty() && currentVersion == latestVersion

  var selectedSegment by remember { mutableStateOf("now") }
  var selectedDateTime by remember {
    mutableStateOf(
      DateTimeValue.DateTime(
        millis = System.currentTimeMillis() + (60 * 60 * 1000), // Default to 1 hour from now
        hour = 12,
        minute = 0,
      ),
    )
  }

  AppScaffold(
    title = DeviceDetailsStrings.SoftwareUpdate,
    navigationIcon = {
      AppIconButton(
        AppIcons.Default.Close,
        modifier = Modifier.testTag(TestTags.DeviceDetails.SoftwareUpdateCloseButton),
      ) {
        onClose()
      }
    },
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(spacing.lg)
        .testTag(TestTags.DeviceDetails.SoftwareUpdateScreenRoot),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {

      if (isUpToDate) {
        // Up-to-date state (similar to Angular's *ngIf="scale.latestVersion === firmwareVersion")
        UpToDateContent(latestVersion = latestVersion)
      } else {
        // Update available state (similar to Angular's ng-template #softwareUpdate)
        UpdateAvailableContent(
          latestVersion = latestVersion,
          selectedSegment = selectedSegment,
          onSegmentChange = { selectedSegment = it },
          selectedDateTime = selectedDateTime,
          onDateTimeChange = { selectedDateTime = it },
          onUpdateNow = { handleIntent(DeviceDetailsIntent.StartFirmwareUpdate) },
          onUpdateScheduled = { handleIntent(DeviceDetailsIntent.StartScheduledFirmwareUpdate(selectedDateTime.getTimestamp())) },
        )
      }
    }
  }
}

/**
 * Content shown when scale is already up to date
 */
@Composable
private fun UpToDateContent(latestVersion: String) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxSize(),
  ) {
    AppText(
      text = "${DeviceDetailsStrings.Version} $latestVersion",
      textType = TextType.Title,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(spacing.md))

    AppText(
      text = DeviceDetailsStrings.AlreadyUpToDate,
      textType = TextType.Subtitle,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(spacing.xl))

    // Checkmark animation placeholder (similar to Angular's check-gif)
    Box(
      modifier = Modifier.size(120.dp),
      contentAlignment = Alignment.Center,
    ) {
      AppText(
        text = "✓",
        textType = TextType.Title,
      )
    }
  }
}

/**
 * Content shown when update is available
 */
@Composable
private fun UpdateAvailableContent(
  latestVersion: String,
  selectedSegment: String,
  onSegmentChange: (String) -> Unit,
  selectedDateTime: DateTimeValue.DateTime,
  onDateTimeChange: (DateTimeValue.DateTime) -> Unit,
  onUpdateNow: () -> Unit,
  onUpdateScheduled: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
  ) {
    // Update message (similar to Angular's ion-label)
    AppText(
      text = "${DeviceDetailsStrings.UpdateMessage} $latestVersion ${DeviceDetailsStrings.UpdateMessage1}",
      textType = TextType.Body,
      spacing = spacing.lg,
    )

    // Segment selection (similar to Angular's ion-segment)
    val segmentOptions = listOf(
      SegmentButtonData(id = 0, label = DeviceDetailsStrings.UpdateNow),
      SegmentButtonData(id = 1, label = DeviceDetailsStrings.UpdateSchedule),
    )

    val selectedOption = if (selectedSegment == "now") segmentOptions[0] else segmentOptions[1]

    SegmentButtonGroup(
      data = segmentOptions,
      selectedData = selectedOption,
      onSelected = { selected ->
        onSegmentChange(if (selected.id == 0) "now" else "scheduled")
      },
      size = SegmentButtonSize.Medium,
      type = SegmentButtonType.Single,
      key = SegmentButtonData::label,
      modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(spacing.lg))

    if (selectedSegment == "now") {
      // Now content (similar to Angular's *ngIf="segment === 'now'")
      NowUpdateContent(onUpdateNow = onUpdateNow)
    } else {
      // Scheduled content (similar to Angular's ng-template #scheduled)
      ScheduledUpdateContent(
        selectedDateTime = selectedDateTime,
        onDateTimeChange = onDateTimeChange,
        onUpdateScheduled = onUpdateScheduled,
      )
    }
  }
}

/**
 * Content for immediate update
 */
@Composable
private fun NowUpdateContent(onUpdateNow: () -> Unit) {
  Column {
    AppText(
      text = DeviceDetailsStrings.UpdateMessage2,
      textType = TextType.Body,
      spacing = spacing.lg,
    )

    AppButton(
      label = DeviceDetailsStrings.Upgrade,
      onClick = onUpdateNow,
      type = ButtonType.PrimaryFilled,
      size = ButtonSize.Large,
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.DeviceDetails.SoftwareUpgradeButton),
    )
  }
}

/**
 * Content for scheduled update
 */
@Composable
private fun ScheduledUpdateContent(
  selectedDateTime: DateTimeValue.DateTime,
  onDateTimeChange: (DateTimeValue.DateTime) -> Unit,
  onUpdateScheduled: () -> Unit,
) {
  Column {
    // Date and time selection using the existing DateTimeInput component
    AppText(
      text = DeviceDetailsStrings.Date,
      textType = TextType.Body,
      spacing = spacing.sm,
    )

    // Use the existing DateTimeInput component (similar to Angular's gg-datetime)
    DateTimeInput(
      value = selectedDateTime,
      onValueChange = { newValue ->
        if (newValue is DateTimeValue.DateTime) {
          onDateTimeChange(newValue)
        }
      },
      mode = DateTimeInputMode.DateTime,
      minValue = DateTimeValue.DateTime(
        millis = System.currentTimeMillis(),
        hour = 0,
        minute = 0,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = spacing.lg),
    )

    AppButton(
      label = DeviceDetailsStrings.Save,
      onClick = onUpdateScheduled,
      type = ButtonType.PrimaryFilled,
      size = ButtonSize.Large,
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TestTags.DeviceDetails.SoftwareUpdateSaveButton),
    )
  }
}

@PreviewTheme
@Composable
fun SoftwareUpdateScreenPreview() {
  val dummyState = DeviceDetailsState(
    scale = null,
    scaleNameForm = com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup(
      com.dmdbrands.gurus.weight.features.deviceDetails.reducer.DeviceNameDialogFormControls.create(),
    ),
  )
  SoftwareUpdateScreen(
    state = dummyState,
    handleIntent = {},
    onClose = {},
  )
}
