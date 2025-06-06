package com.greatergoods.libs.healthconnect.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import android.content.res.Configuration

/**
 * Onboarding screen for Health Connect permissions and consent.
 * Shows rationale, permission status, and a button to request permissions.
 *
 * @param permissionStatus Current permission status.
 * @param onRequestPermissions Callback when the user taps the request button.
 */
@Composable
fun HealthConnectOnboardingScreen(
    permissionStatus: String, // Replace with enum in real usage
    onRequestPermissions: (result: Set<String>) -> Unit,
) {
    val requestPermissionActivityContract =
        PermissionController.createRequestPermissionResultContract()
    val launcher =
        rememberLauncherForActivityResult(requestPermissionActivityContract) { result ->
            onRequestPermissions(result)
        }
    Scaffold { padding ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Connect to Health Connect",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "To sync your health data, we need permission to access Health Connect.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Current permission status: $permissionStatus",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        launcher.launch(
                            setOf(
                                HealthPermission.getReadPermission(WeightRecord::class),
                            ),
                        )
                    },
                ) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
@Composable
fun PreviewHealthConnectOnboardingScreenLight() {
    MaterialTheme {
        HealthConnectOnboardingScreen(
            permissionStatus = "NONE",
            onRequestPermissions = {},
        )
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun PreviewHealthConnectOnboardingScreenDark() {
    MaterialTheme {
        HealthConnectOnboardingScreen(
            permissionStatus = "PARTIAL",
            onRequestPermissions = {},
        )
    }
}
