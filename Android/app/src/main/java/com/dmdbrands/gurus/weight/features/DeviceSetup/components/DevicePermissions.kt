package com.dmdbrands.gurus.weight.features.DeviceSetup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.dmdbrands.gurus.weight.domain.model.permission.PermissionState
import com.dmdbrands.gurus.weight.features.DeviceSetup.strings.DeviceSetupStrings
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.DeviceDataHelper
import com.dmdbrands.gurus.weight.features.permissionSettings.PermissionSettings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap

@Composable
fun DevicePermissions(
    sku: String,
    modifier: Modifier = Modifier,
    permissions: GGPermissionStatusMap,
    onRequestPermission: (String) -> Unit,
    wifiName: String? = null,
) {
    val scaleInfo = DeviceDataHelper.findScaleInfoBySku(sku) ?: return
    val scaleSetupType = scaleInfo.setupType
    val permissionGroups = AppPermissionsHelper.getRequiredPermissionsForSetupType(sku, permissions, null, wifiName)
    Column(
        modifier = modifier
          .fillMaxSize().verticalScroll(rememberScrollState())
          .padding( horizontal = spacing.sm, vertical = spacing.md),
    ) {
        AppText(
            text = DeviceSetupStrings.DevicePermissions.Title,
            textType = TextType.Title,
            // TalkBack: permissions title is the step heading.
            modifier = Modifier
                .padding(bottom = spacing.xs)
                .semantics { heading() },
        )
        AppText(
            text = DeviceSetupStrings.DevicePermissions.Subtitle(setupType = scaleSetupType),
            textType = TextType.Body,
            modifier = Modifier.padding(bottom = spacing.lg),
        )
        PermissionSettings(
            permissionGroups = permissionGroups,
            onRequestPermission = onRequestPermission,
            requiredPermissions = emptySet() // Scale setup shows grey for unauthorized permissions
        )
    }
}

@PreviewTheme
@Composable
fun PreviewScalePermissions() {
  MeAppTheme {
    DevicePermissions(
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
