package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dmdbrands.gurus.weight.features.common.model.ActionButton
import com.dmdbrands.gurus.weight.features.common.model.DialogModel
import com.dmdbrands.gurus.weight.features.settings.strings.RadioGroupModalStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

/**
 * One section of a sectioned radio modal — a labelled group of options with
 * its own current selection. Sections are independent: changing one does not
 * affect another. [key] identifies the section in the SAVE callback's result
 * map.
 */
data class RadioGroupSection<T>(
  val key: String,
  val label: String,
  val options: List<RadioButtonOption<T>>,
  val selectedItem: T?,
  /**
   * When false the section is shown but locked: its options are non-selectable
   * (pinned to [selectedItem], the system default) and [lockedMessage] explains
   * how to unlock it. Used by the Unit Type dialog when the account lacks the
   * relevant product. (MOB-1175)
   */
  val enabled: Boolean = true,
  val lockedMessage: String? = null,
)

/**
 * Sectioned radio group modal — multiple labelled radio groups in a single
 * dialog with one SAVE that commits every section's current selection.
 *
 * Used by the per-product Unit Type dialog (My Weight + My Kids), where each
 * section persists independently. Sections are separated by a thin divider.
 */
@Composable
fun <T> AppSectionedRadioGroupModal(
  title: String,
  sections: List<RadioGroupSection<T>>,
  onCancel: () -> Unit,
  onOk: (Map<String, T?>) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  confirmText: String = RadioGroupModalStrings.Button.Save,
  cancelText: String = RadioGroupModalStrings.Button.Cancel,
) {
  val currentSelections: SnapshotStateMap<String, T?> = remember { mutableStateMapOf() }
  // Re-sync when the caller supplies new sections (e.g. on reopen).
  LaunchedEffect(sections) {
    currentSelections.clear()
    sections.forEach { currentSelections[it.key] = it.selectedItem }
  }

  Dialog(
    onDismissRequest = onCancel,
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true,
      usePlatformDefaultWidth = false,
      decorFitsSystemWindows = false,
    ),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MeTheme.colorScheme.overlay),
    ) {
      Box(modifier = Modifier.align(Alignment.Center)) {
        BaseModal(
          title = title,
          subtitle = subtitle,
          primaryAction = ActionButton(
            text = confirmText,
            action = { onOk(currentSelections.toMap()) },
          ),
          secondaryAction = ActionButton(
            text = cancelText,
            action = onCancel,
          ),
          modifier = modifier,
        ) {
          SectionedRadioGroupContent(
            sections = sections,
            currentSelections = currentSelections,
          )
        }
      }
    }
  }
}

/**
 * Section list for [AppSectionedRadioGroupModal]. Capped to a fraction of the
 * screen and vertically scrollable — mirroring [AppRadioGroupModal] — so that
 * with both sections shown under large accessibility font scaling the content
 * scrolls instead of growing the card past the screen and clipping the
 * SAVE/Cancel buttons below it.
 */
@Composable
private fun <T> SectionedRadioGroupContent(
  sections: List<RadioGroupSection<T>>,
  currentSelections: SnapshotStateMap<String, T?>,
) {
  val scrollState = rememberScrollState()
  val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = maxContentHeight)
      .verticalScroll(scrollState),
  ) {
    Spacer(modifier = Modifier.height(MeTheme.spacing.xs))
    sections.forEachIndexed { index, section ->
      if (index > 0) {
        HorizontalDivider(
          modifier = Modifier.padding(vertical = MeTheme.spacing.sm),
          color = MeTheme.colorScheme.utility,
        )
      }
      // A locked section renders its options greyed and non-selectable, pinned to
      // the default supplied via [section.selectedItem]. (MOB-1175)
      val options = if (section.enabled) {
        section.options
      } else {
        section.options.map { it.copy(enabled = false) }
      }
      AppRadioGroup(
        options = options,
        selectedItem = currentSelections[section.key],
        onOptionSelected = { selected ->
          if (section.enabled) currentSelections[section.key] = selected
        },
        groupLabel = section.label,
        // Active section header uses the heading colour; a locked section is greyed. (MOB-1256)
        groupLabelColor = if (section.enabled) {
          MeTheme.colorScheme.textHeading
        } else {
          MeTheme.colorScheme.textSubheading
        },
        modifier = Modifier.fillMaxWidth(),
      )
      if (!section.enabled && section.lockedMessage != null) {
        Text(
          text = section.lockedMessage,
          style = MeTheme.typography.body2,
          color = MeTheme.colorScheme.textSubheading,
          modifier = Modifier.padding(top = MeTheme.spacing.xs),
        )
      }
    }
  }
}

/**
 * Config wrapper to pass sectioned modal params via the DialogQueue plumbing,
 * mirroring [RadioGroupModalConfig] for the single-group variant.
 */
data class SectionedRadioGroupModalConfig<T>(
  val title: String,
  val subtitle: String? = null,
  val sections: List<RadioGroupSection<T>>,
  val confirmText: String = RadioGroupModalStrings.Button.Save,
  val cancelText: String = RadioGroupModalStrings.Button.Cancel,
)

/**
 * Convenience for enqueueing a sectioned modal via the dialog service.
 * [onConfirm] receives a map from section key to selection (nullable when no
 * option was picked in that section).
 */
fun <T> showSectionedRadioGroupModal(
  dialogService: com.dmdbrands.gurus.weight.domain.interfaces.IDialogQueueService,
  title: String,
  sections: List<RadioGroupSection<T>>,
  onConfirm: (Map<String, T?>) -> Unit,
  onCancel: (() -> Unit)? = null,
  subtitle: String? = null,
  confirmText: String = RadioGroupModalStrings.Button.Save,
  cancelText: String = RadioGroupModalStrings.Button.Cancel,
) {
  val config = SectionedRadioGroupModalConfig(
    title = title,
    subtitle = subtitle,
    sections = sections,
    confirmText = confirmText,
    cancelText = cancelText,
  )
  // DialogHost invokes params["onConfirm"] directly for SectionedRadioGroupPicker
  // (see DialogHost), so the model-level onConfirm is never called — omit it.
  val dialog = DialogModel.Custom(
    contentKey = DialogType.SectionedRadioGroupPicker,
    params = mapOf(
      "config" to config,
      "onConfirm" to onConfirm,
      "onCancel" to onCancel,
    ),
    onDismiss = { onCancel?.invoke() },
    customPriority = 1,
  )
  dialogService.enqueue(dialog)
}

@PreviewTheme
@Composable
private fun AppSectionedRadioGroupModalPreview() {
  MeAppTheme {
    AppSectionedRadioGroupModal(
      title = "Unit Type",
      sections = listOf(
        RadioGroupSection(
          key = "myWeight",
          label = "My Weight",
          options = listOf(
            RadioButtonOption(id = "lb", label = "lb & ft"),
            RadioButtonOption(id = "kg", label = "kg & cm"),
          ),
          selectedItem = "lb",
        ),
        RadioGroupSection(
          key = "myKids",
          label = "My Kids",
          options = listOf(
            RadioButtonOption(id = "lb_oz", label = "lb-oz & in"),
            RadioButtonOption(id = "lb", label = "lb & in"),
            RadioButtonOption(id = "kg", label = "kg & cm"),
          ),
          selectedItem = "lb_oz",
          enabled = false,
          lockedMessage = "Add a baby scale or baby profile to configure this unit.",
        ),
      ),
      onCancel = {},
      onOk = {},
    )
  }
}
