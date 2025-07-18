package com.greatergoods.meapp.features.scaleDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppScaleCard
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.helper.ScaleDataHelper.toScaleInfo
import com.greatergoods.meapp.features.permissionSettings.PermissionSettings
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsIntent
import com.greatergoods.meapp.features.scaleDetails.reducer.ScaleDetailsState
import com.greatergoods.meapp.features.scaleDetails.strings.BluetoothSettingStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun BluetoothPermissionScreen(
  state: ScaleDetailsState,
  handleIntent: (ScaleDetailsIntent) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler {
    onClose()
  }
  AppScaffold(
    title = BluetoothSettingStrings.Title,
    navigationIcon = {
      AppIconButton(AppIcons.Default.Close , onClick = onClose)
    },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(vertical = spacing.md, horizontal = spacing.sm),
      verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
      val permissions = AppPermissionsHelper
        .getRequiredPermissions(
          state.permissions,
          listOf(
            GGPermissionType.BLUETOOTH_SWITCH, GGPermissionType.NEARBY_DEVICE,
          ),
        )

      AppText(
        text = BluetoothSettingStrings.Message,
        textType = TextType.Body,
        modifier = Modifier.fillMaxWidth(),
      )
      Column {
        HorizontalDivider(thickness = 0.5.dp, color = colorScheme.utility)
        AppScaleCard(
          scale = state.scale?.toScaleInfo()!!,
          isSavedScale = true,
          onClick = {}
        )
      }

      PermissionSettings(
        permissions,
        onRequestPermission = {
          handleIntent(ScaleDetailsIntent.RequestPermission(it))
        },
      )
    }
  }
}

@PreviewTheme
@Composable
fun BluetoothPermissionScreenPreview() {
  MeAppTheme {

  }
}
