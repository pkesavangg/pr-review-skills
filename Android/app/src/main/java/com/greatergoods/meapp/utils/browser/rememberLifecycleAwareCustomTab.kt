package com.greatergoods.meapp.utils.browser

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
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
fun rememberLifecycleAwareCustomTab(): CustomTabLauncher {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember {
        CustomTabManager(context = context, eventListener = object : CustomTabEventListener {
            override fun onTabShown() {
            }

            override fun onTabHidden() {
            }

            override fun onNavigationStarted() {
                // Handle navigation start
            }

            override fun onNavigationFinished() {
                // Handle navigation finish
            }

            override fun onNavigationFailed() {
                // Handle navigation failure
            }
        })
    }
    val lifecycleAware = remember { LifecycleAwareCustomTabHelper(manager) }

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(lifecycleAware)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleAware)
        }
    }
    return lifecycleAware.launcher
}

@Composable
fun LinkButton(url: String) {
    val tabLauncher = rememberLifecycleAwareCustomTab()
    val context = LocalContext.current

    Button(onClick = {
        tabLauncher.openUrl(url, context)
    }) {
        Text("Open in Chrome Tab")
    }
}

@Composable
fun CustomTabScreen(
    viewModel: CustomTabViewModel = hiltViewModel(),
    urlToOpen: String
) {
    val navigationState by viewModel.navigationState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(navigationState) {
        when (navigationState) {
            is NavigationState.TabShown -> {
                Toast.makeText(context, "Tab Shown", Toast.LENGTH_SHORT).show()
            }

            is NavigationState.TabHidden -> {
                Toast.makeText(context, "Tab Hidden", Toast.LENGTH_SHORT).show()
                viewModel.resetNavigationState()
            }

            is NavigationState.Loading -> {
                Toast.makeText(context, "Loading URL...", Toast.LENGTH_SHORT).show()
            }

            is NavigationState.Finished -> {
                Toast.makeText(context, "Finished loading", Toast.LENGTH_SHORT).show()
                viewModel.resetNavigationState()
            }

            is NavigationState.Failed -> {
                val error = (navigationState as NavigationState.Failed).error
                Toast.makeText(context, "Error: ${error?.message}", Toast.LENGTH_LONG).show()
                viewModel.resetNavigationState()
            }

            NavigationState.Idle -> Unit
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
