package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme

/**
 * AppScaffold composable that provides a top app bar using AppBar and a content slot.
 * Handles window insets properly for status bar, navigation bar, and keyboard.
 *
 * @param title The title to display in the AppBar.
 * @param onLeftIconClick Callback for left icon click.
 * @param onRightIconClick Callback for right icon click (optional).
 * @param modifier Modifier for the Scaffold.
 * @param actions Optional composable for actions.
 * @param navigationIcon Optional composable for left icon.
 * @param content The main content below the AppBar.
 */
@Composable
fun AppScaffold(
    title: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = colorScheme.primaryBackground,
    contentColor: Color = colorScheme.secondaryBackground,
    actions: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppBar(
                title = title,
                navigationIcon = navigationIcon,
                actions = actions,
                containerColor = containerColor,
                modifier = Modifier.padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Top)
                        .asPaddingValues(),
                ),
            )
        },
        containerColor = containerColor,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    WindowInsets.safeDrawing
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                )
                .background(contentColor),
        ) {
            content(Modifier)
        }
    }
}

@PreviewTheme
@Composable
fun AppScaffoldPreview() {
    MeAppTheme {
        AppScaffold(
            title = "App Scaffold Title",
        ) { modifier ->
            Box(modifier = modifier) {
                Text(
                    text = "Scaffold content goes here",
                    style = MeTheme.typography.body1,
                )
            }
        }
    }
}
