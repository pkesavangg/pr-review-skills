package com.dmdbrands.gurus.weight.features.scaleDetails.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.domain.model.storage.BLEStatus
import com.dmdbrands.gurus.weight.domain.model.storage.Device
import com.dmdbrands.gurus.weight.features.appPermissions.helper.AppPermissionsHelper
import com.dmdbrands.gurus.weight.features.common.components.AppIconButton
import com.dmdbrands.gurus.weight.features.common.components.AppScaffold
import com.dmdbrands.gurus.weight.features.common.components.AppScaleCard
import com.dmdbrands.gurus.weight.features.common.components.AppText
import com.dmdbrands.gurus.weight.features.common.components.PreviewTheme
import com.dmdbrands.gurus.weight.features.common.components.TextType
import com.dmdbrands.gurus.weight.features.common.helper.ScaleDataHelper.toScaleInfo
import com.dmdbrands.gurus.weight.features.common.helper.form.FormGroup
import com.dmdbrands.gurus.weight.features.permissionSettings.PermissionSettings
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsIntent
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleDetailsState
import com.dmdbrands.gurus.weight.features.scaleDetails.reducer.ScaleNameDialogFormControls
import com.dmdbrands.gurus.weight.features.scaleDetails.strings.BluetoothSettingStrings
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.spacing
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionState
import com.dmdbrands.library.ggbluetooth.enums.GGPermissionType
import com.dmdbrands.library.ggbluetooth.model.GGDeviceDetail
import com.dmdbrands.library.ggbluetooth.model.GGPermissionStatusMap
import android.os.Build

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
          .fillMaxSize().verticalScroll(rememberScrollState())
          .padding(vertical = spacing.md, horizontal = spacing.sm),
      verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
      val requiredPermissionTypes = buildList {
        add(GGPermissionType.BLUETOOTH_SWITCH)
        // NEARBY_DEVICE is only available on Android 12+ (API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          add(GGPermissionType.NEARBY_DEVICE)
        }
      }
      val permissions = AppPermissionsHelper
        .getRequiredPermissions(
          state.permissions,
          requiredPermissionTypes,
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
          horizontalSpacing = 0.dp,
          isSavedScale = true,
          canShowRightCaret = false,
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
fun BluetoothPermissionScreenPreviewLight() {
  MeAppTheme {
    val dummyDevice = Device(
      id = "preview-device-1",
      device = GGDeviceDetail(
        deviceName = "AccuCheck Verve Smart Scale",
        macAddress = "AA:BB:CC:DD:EE:FF",
        identifier = "preview-identifier-1",
      ),
      connectionStatus = BLEStatus.CONNECTED,
      nickname = "My Smart Scale",
      deviceType = "bluetooth",
      sku = "0375",
      alreadyPaired = true,
    )
    val dummyPermissions: GGPermissionStatusMap = mutableMapOf<String, String>().apply {
      put(GGPermissionType.BLUETOOTH_SWITCH, GGPermissionState.DISABLED)
      // NEARBY_DEVICE is only available on Android 12+ (API 31)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        put(GGPermissionType.NEARBY_DEVICE, GGPermissionState.DISABLED)
      }
    }
    val dummyScaleNameForm = FormGroup(ScaleNameDialogFormControls.create())
    val dummyState = ScaleDetailsState(
      scale = dummyDevice,
      scaleNameForm = dummyScaleNameForm,
      permissions = dummyPermissions,
    )
    BluetoothPermissionScreen(
      state = dummyState,
      handleIntent = {},
      onClose = {},
    )
  }
}

@PreviewTheme
@Composable
fun BluetoothPermissionScreenPreviewDark() {
  MeAppTheme {
    val dummyDevice = Device(
      id = "preview-device-2",
      device = GGDeviceDetail(
        deviceName = "Weight Gurus Smart Scale",
        macAddress = "11:22:33:44:55:66",
        identifier = "preview-identifier-2",
      ),
      connectionStatus = BLEStatus.CONNECTED,
      nickname = "Bathroom Scale",
      deviceType = "bluetooth",
      sku = "0412",
      alreadyPaired = true,
    )
    val dummyPermissions: GGPermissionStatusMap = mutableMapOf<String, String>().apply {
      put(GGPermissionType.BLUETOOTH_SWITCH, GGPermissionState.ENABLED)
      // NEARBY_DEVICE is only available on Android 12+ (API 31)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        put(GGPermissionType.NEARBY_DEVICE, GGPermissionState.DISABLED)
      }
    }
    val dummyScaleNameForm = FormGroup(ScaleNameDialogFormControls.create())
    val dummyState = ScaleDetailsState(
      scale = dummyDevice,
      scaleNameForm = dummyScaleNameForm,
      permissions = dummyPermissions,
    )
    BluetoothPermissionScreen(
      state = dummyState,
      handleIntent = {},
      onClose = {},
    )
  }
}
