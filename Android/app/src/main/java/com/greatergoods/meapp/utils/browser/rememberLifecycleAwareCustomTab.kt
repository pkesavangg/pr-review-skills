package com.greatergoods.meapp.utils.browser

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel


@Composable
fun CustomTabScreen(
    viewModel: CustomTabViewModel = hiltViewModel(),
    urlToOpen: String
) {
    val navigationState by viewModel.chromeTabState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(navigationState) {
        when (navigationState) {
            is ChromeTabState.TabShown -> {
                Toast.makeText(context, "Tab Shown", Toast.LENGTH_SHORT).show()
            }

            is ChromeTabState.TabHidden -> {
                Toast.makeText(context, "Tab Hidden", Toast.LENGTH_SHORT).show()
            }

            is ChromeTabState.Loading -> {
                Toast.makeText(context, "Loading URL...", Toast.LENGTH_SHORT).show()
            }

            is ChromeTabState.Finished -> {
                Toast.makeText(context, "Finished loading", Toast.LENGTH_SHORT).show()
            }

            is ChromeTabState.Failed -> {
                val error = (navigationState as ChromeTabState.Failed).error
                Toast.makeText(context, "Error: ${error?.message}", Toast.LENGTH_LONG).show()
            }

            ChromeTabState.Idle -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Open Link in Custom Tab", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.launchTab( urlToOpen)
            }
        ) {
            Text("Launch Custom Tab")
        }
    }
}
