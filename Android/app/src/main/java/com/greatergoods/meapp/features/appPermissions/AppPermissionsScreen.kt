package com.greatergoods.meapp.features.appPermissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.shared.utilities.logging.AppLog
import com.greatergoods.meapp.features.appPermissions.strings.AppPermissionsScreenStrings
import com.greatergoods.meapp.features.appPermissions.viewmodel.AppPermissionsIntent
import com.greatergoods.meapp.features.appPermissions.viewmodel.AppPermissionsState
import com.greatergoods.meapp.features.appPermissions.viewmodel.AppPermissionsViewModel
import com.greatergoods.meapp.features.appPermissions.viewmodel.AppPermissionStatus
import com.greatergoods.meapp.features.common.components.AppIconButton
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.AppText
import com.greatergoods.meapp.features.common.components.PermissionItem
import com.greatergoods.meapp.features.common.components.PermissionItemStatus
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.features.common.components.TextType
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * App Permissions screen that displays the status of various Android permissions
 * and allows users to manage them.
 */
@Composable
fun AppPermissionsScreen() {
    val viewModel: AppPermissionsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    AppPermissionsContent(state, viewModel::handleIntent)
}

/**
 * Content composable for the App Permissions screen.
 */
@Composable
fun AppPermissionsContent(
    state: AppPermissionsState,
    handleIntent: (AppPermissionsIntent) -> Unit,
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }

    // Launch effect to refresh permissions when screen is opened or refreshed
    LaunchedEffect(refreshTrigger) {
        handleIntent(AppPermissionsIntent.RefreshPermissions)
    }

    // Permission launcher for requesting permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        AppLog.i("AppPermissions", "Permission result: $permissions")
        handleIntent(AppPermissionsIntent.RefreshPermissions)
    }

    AppScaffold(
        navigationIcon = {
            AppIconButton(AppIcons.Default.Close) {  }
        },
        title = AppPermissionsScreenStrings.Title
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = MeTheme.spacing.md, horizontal = MeTheme.spacing.sm),
        ) {
            // Essential Permissions Section
            AppText(
                text = AppPermissionsScreenStrings.BluetoothPermissions,
                textType = TextType.Title,
                modifier = Modifier.padding(bottom = MeTheme.spacing.sm),
            )

            Column(
                modifier = Modifier
                    .clip(shape = RoundedCornerShape(MeTheme.borderRadius.md))
                    .background(color = MeTheme.colorScheme.primaryBackground)
            ) {
                PermissionItem(
                    checked = true,
                    onCheckedChange = { },
                    permissionStatus = PermissionItemStatus.Granted,
                    enabled = true,
                    required = true,
                    content = {
                        Column {
                            AppText(
                                text = "Camera Permission",
                                textType = TextType.Subtitle,
                            )
                        }
                    }
                ) {
                }

                HorizontalDivider(color = MeTheme.colorScheme.utility)

                PermissionItem(
                    checked = false,
                    onCheckedChange = { },
                    permissionStatus = PermissionItemStatus.NotRequested,
                    enabled = true,
                    required = true,
                    content = {
                        Column {
                            AppText(
                                text = "Bluetooth Permission",
                                textType = TextType.Subtitle,
                            )
                        }
                    }
                ) {
                }

                HorizontalDivider(color = MeTheme.colorScheme.utility)

                PermissionItem(
                    checked = false,
                    onCheckedChange = { },
                    permissionStatus = PermissionItemStatus.Denied,
                    enabled = true,
                    required = false,
                    content = {
                        Column {
                            AppText(
                                text = "Location Permission",
                                textType = TextType.Subtitle,
                            )
                        }
                    }
                ) {
                }
            }
        }
    }
}

/**
 * Helper function to get permission status text.
 */
private fun getPermissionStatusText(status: AppPermissionStatus): String {
    return when (status) {
        AppPermissionStatus.Granted -> AppPermissionsScreenStrings.Granted
        AppPermissionStatus.Denied -> AppPermissionsScreenStrings.Denied
        AppPermissionStatus.NotRequested -> AppPermissionsScreenStrings.NotRequested
    }
}

/**
 * Helper function to convert PermissionStatus to PermissionItemStatus.
 */
private fun getPermissionItemStatus(status: AppPermissionStatus): PermissionItemStatus {
    return when (status) {
        AppPermissionStatus.Granted -> PermissionItemStatus.Granted
        AppPermissionStatus.Denied -> PermissionItemStatus.Denied
        AppPermissionStatus.NotRequested -> PermissionItemStatus.NotRequested
    }
}

/**
 * Helper function to open app settings.
 */
private fun openAppSettings(context: android.content.Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        AppLog.e("AppPermissions", "Failed to open app settings", e.toString())
    }
}

@PreviewTheme
@Composable
fun AppPermissionsScreenPreview() {
    MeAppTheme {
        AppPermissionsContent(
            state = AppPermissionsState(
                healthConnectPermission = AppPermissionStatus.Granted,
                bluetoothPermission = AppPermissionStatus.Granted,
                notificationsPermission = AppPermissionStatus.Denied,
                cameraPermission = AppPermissionStatus.NotRequested,
                locationPermission = AppPermissionStatus.Granted,
            ),
            handleIntent = {},
        )
    }
}
