// package com.greatergoods.libs.healthconnect.ui
//
// import androidx.compose.foundation.layout.Arrangement
// import androidx.compose.foundation.layout.Column
// import androidx.compose.foundation.layout.Spacer
// import androidx.compose.material3.MaterialTheme
// import androidx.compose.material3.Scaffold
// import androidx.compose.runtime.Composable
// import androidx.compose.ui.unit.dp
// import android.widget.Button
//
// /**
//  * Onboarding screen for Health Connect permissions and consent.
//  * Shows rationale, permission status, and a button to request permissions.
//  *
//  * @param permissionStatus Current permission status.
//  * @param onRequestPermissions Callback when the user taps the request button.
//  */
// @Composable
// fun HealthConnectOnboardingScreen(
//     permissionStatus: String, // Replace with enum in real usage
//     onRequestPermissions: () -> Unit,
// ) {
//     Scaffold { padding ->
//         Surface(
//             modifier =
//                 Modifier
//                     .fillMaxSize()
//                     .padding(padding),
//         ) {
//             Column(
//                 modifier =
//                     Modifier
//                         .fillMaxSize()
//                         .padding(24.dp),
//                 verticalArrangement = Arrangement.Center,
//                 horizontalAlignment = Alignment.CenterHorizontally,
//             ) {
//                 _root_ide_package_.org.w3c.dom.Text(
//                     text = "Connect to Health Connect",
//                     style = MaterialTheme.typography.headlineMedium,
//                 )
//                 Spacer(modifier = Modifier.height(16.dp))
//                 _root_ide_package_.org.w3c.dom.Text(
//                     text = "To sync your health data, we need permission to access Health Connect.",
//                     style = MaterialTheme.typography.bodyLarge,
//                 )
//                 Spacer(modifier = Modifier.height(24.dp))
//                 _root_ide_package_.org.w3c.dom.Text(
//                     text = "Current permission status: $permissionStatus",
//                     style = MaterialTheme.typography.bodyMedium,
//                 )
//                 Spacer(modifier = Modifier.height(32.dp))
//                 Button(onClick = onRequestPermissions) {
//                     _root_ide_package_.org.w3c.dom
//                         .Text("Grant Permissions")
//                 }
//             }
//         }
//     }
// }
//
// @Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Light Mode")
// @Composable
// fun PreviewHealthConnectOnboardingScreenLight() {
//     MaterialTheme {
//         HealthConnectOnboardingScreen(
//             permissionStatus = "NONE",
//             onRequestPermissions = {},
//         )
//     }
// }
//
// @Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
// @Composable
// fun PreviewHealthConnectOnboardingScreenDark() {
//     MaterialTheme {
//         HealthConnectOnboardingScreen(
//             permissionStatus = "PARTIAL",
//             onRequestPermissions = {},
//         )
//     }
// }
