package com.greatergoods.meapp.features.integration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.greatergoods.meapp.core.service.InvalidIntegrationAlert
import com.greatergoods.meapp.core.shared.utilities.browser.ChromeTabState
import java.util.Locale
import android.util.Log
import android.widget.Toast

@Composable
fun IntegrationScreen(
    viewModel: IntegrationViewModel = hiltViewModel(),
    onNavigateToIntegrations: () -> Unit = {},
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    // Handle navigation to integrations page
    LaunchedEffect(Unit) {
        viewModel.navigateToIntegrationsEvent.collect {
            onNavigateToIntegrations()
        }
    }

    // Handle Chrome tab state changes
    LaunchedEffect(Unit) {
        viewModel.chromeTabState.collect { event ->
            when (event) {
                is ChromeTabState.TabHidden -> {
                    Toast.makeText(context, "Tab closed", Toast.LENGTH_SHORT).show()
                    // Optionally refresh integrations after OAuth completion
                }

                is ChromeTabState.TabShown -> Log.d("CustomTab", "Tab is now shown")
                else -> {}
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Integrations",
            style = MaterialTheme.typography.headlineMedium,
        )

        // Show loading indicator
        if (isLoading) {
            CircularProgressIndicator()
        }
        // Add Integrations Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Add Integration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(16.dp))

                val availableProviders = listOf("fitbit", "mfPal")
                availableProviders.forEach { provider ->
                    Button(
                        onClick = { viewModel.addIntegration(provider) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Add ${getProviderDisplayName(provider)}")
                        }
                    }
                    if (provider != availableProviders.last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun getProviderDisplayName(provider: String): String =
    when (provider) {
        "fitbit" -> "Fitbit"
        "mfPal" -> "MyFitnessPal"
        "googleFit" -> "Google Fit"
        "uArmor" -> "Under Armour"
        else -> provider.capitalize(Locale.ROOT)
    }

@Composable
private fun ReintegrateAlertDialog(
    alert: InvalidIntegrationAlert,
    onDisable: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Integration Issue")
        },
        text = {
            Text(alert.message)
        },
        confirmButton = {
            TextButton(onClick = onOpenIntegrations) {
                Text(alert.openIntegrationsButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDisable) {
                Text(alert.disableButtonText)
            }
        },
    )
}
