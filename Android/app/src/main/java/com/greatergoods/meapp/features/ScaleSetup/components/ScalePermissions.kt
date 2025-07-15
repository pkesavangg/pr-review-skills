package com.greatergoods.meapp.features.ScaleSetup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import com.greatergoods.meapp.domain.model.permission.PermissionState
import com.greatergoods.meapp.features.ScaleSetup.strings.ScaleSetupStrings
import com.greatergoods.meapp.features.appPermissions.helper.AppPermissionsHelper
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.features.common.model.SCALES
import com.greatergoods.meapp.features.permissionSettings.PermissionSettings
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.spacing

@Composable
fun ScalePermissions(
  sku: String,
  permissions: GGPermissionStatusMap,
  onRequestPermission: (String) -> Unit,
) {
  val scaleSetupType = SCALES.find { it.sku == sku }!!.setupType
  val permissionGroups = AppPermissionsHelper.getRequiredPermissionsForSetupType(sku, permissions)
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = spacing.sm, horizontal = spacing.md),
  ) {
    AppText(
      text = ScaleSetupStrings.ScalePermissions.Title,
      textType = TextType.ListTitle1,
      modifier = Modifier.padding(bottom = spacing.xs),
    )
    AppText(
      text = ScaleSetupStrings.ScalePermissions.Subtitle(setupType = scaleSetupType),
      textType = TextType.Body,
      modifier = Modifier.padding(bottom = spacing.lg),
    )
    PermissionSettings(
      permissionGroups = permissionGroups,
      onRequestPermission = onRequestPermission,
    )
  }
}

@PreviewTheme
@Composable
fun PreviewScalePermissions() {
  MeAppTheme {
    ScalePermissions(
      sku = "0412",
      permissions = mutableMapOf(
        "BLUETOOTH_SWITCH" to PermissionState.ENABLED,
        "NEARBY_DEVICE" to PermissionState.DISABLED,
        "LOCATION_SWITCH" to PermissionState.ENABLED,
        "LOCATION" to PermissionState.DISABLED,
        "NETWORK" to PermissionState.ENABLED,
        "CAMERA" to PermissionState.NOT_REQUESTED,
        "NOTIFICATION" to PermissionState.ENABLED,
        "NETWORK" to PermissionState.ENABLED,
      ),
      onRequestPermission = { /* Preview - no action */ },
    )
  }
}
