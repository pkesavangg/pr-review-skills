package com.greatergoods.meapp.features.common.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.features.common.components.AppBarIconDefaults
import com.greatergoods.meapp.features.common.components.AppScaffold
import com.greatergoods.meapp.features.common.components.PreviewTheme
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * Sample screen demonstrating usage of AppScaffold with working icon handlers and content.
 */
@Composable
fun SampleAppScaffoldScreen() {
    var leftClicked by remember { mutableStateOf(false) }
    var rightClicked by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    AppScaffold(
        title = "Sample App Scaffold",
        onLeftIconClick = null,
        onRightIconClick = { rightClicked = true },
        leftIcon = null,
        rightIcon = { AppBarIconDefaults.Help() },
        modifier = Modifier.Companion.statusBarsPadding()
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(MeAppTheme.spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Companion.CenterHorizontally
        ) {
            Text("This is a sample screen using AppScaffold.", style = MeAppTheme.typography.body1)
            if (leftClicked) {
                Text("Left icon clicked!", color = MeAppTheme.colorScheme.primaryAction)
            }
            if (rightClicked) {
                Text("Right icon clicked!", color = MeAppTheme.colorScheme.primaryAction)
            }
        }
    }
}

@PreviewTheme
@Composable
fun SampleAppScaffoldScreenPreview() {
    MeAppTheme {
        SampleAppScaffoldScreen()
    }
}
