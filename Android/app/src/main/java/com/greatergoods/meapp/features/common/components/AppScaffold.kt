package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greatergoods.meapp.theme.MeAppTheme

/**
 * AppScaffold composable that provides a top app bar using AppBar and a content slot.
 *
 * @param title The title to display in the AppBar.
 * @param onLeftIconClick Callback for left icon click.
 * @param onRightIconClick Callback for right icon click (optional).
 * @param modifier Modifier for the Scaffold.
 * @param rightIcon Optional composable for right icon.
 * @param leftIcon Optional composable for left icon.
 * @param content The main content below the AppBar.
 */
@Composable
fun AppScaffold(
    title: String?,
    onLeftIconClick: (() -> Unit)? = null,
    onRightIconClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    rightIcon: (@Composable () -> Unit)? = null,
    leftIcon: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AppBar(
                title = title,
                onLeftIconClick = onLeftIconClick,
                onRightIconClick = onRightIconClick,
                leftIcon = leftIcon ?: {},
                rightIcon = rightIcon ?: {}
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
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
            onLeftIconClick = {},
            onRightIconClick = {},
        ) { modifier ->
            Box(modifier = modifier) {
                Text(
                    text = "Scaffold content goes here",
                    style = MeAppTheme.typography.body1
                )
            }
        }
    }
}
