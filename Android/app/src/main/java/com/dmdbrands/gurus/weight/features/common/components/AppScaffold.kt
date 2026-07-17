package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme

/**
 * AppScaffold composable that provides a top app bar using AppBar and a content slot.
 * Handles window insets properly for status bar, navigation bar, and keyboard.
 * Supports optional pull-to-refresh if [onRefresh] is provided.
 *
 * @param title The title to display in the AppBar.
 * @param modifier Modifier for the Scaffold.
 * @param actions Optional composable for actions.
 * @param navigationIcon Optional composable for left icon.
 * @param isRefreshing Whether pull-to-refresh is active.
 * @param onRefresh Callback when pull-to-refresh is triggered.
 * @param content The main content below the AppBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String?,
    modifier: Modifier = Modifier,
    borderColor: Color = colorScheme.utility,
    containerColor: Color = colorScheme.secondaryBackground,
    appBarColor: Color = colorScheme.primaryBackground,
    enable: Boolean = false,
    centerTitle: Boolean = false,
    actions: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    topBarContent: (@Composable () -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    appBarOnclick: () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize(),
        topBar = {
            if (title != null || navigationIcon != null || actions != null || topBarContent != null) {
                AppBar(
                    title = title,
                    topBarContent = topBarContent,
                    enable = enable,
                    centerTitle = centerTitle,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    borderColor = borderColor,
                    containerColor = appBarColor,
                    onClick = {
                        appBarOnclick()
                    }
                )
            }
        },
        containerColor = appBarColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(containerColor),
        ) {
            if (onRefresh != null) {
                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullRefreshState,
                    indicator = {
                        Indicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter),
                            isRefreshing = isRefreshing,
                            containerColor = colorScheme.primaryBackground,
                            color = colorScheme.primaryAction,
                            state = pullRefreshState,
                        )
                    },
                ) {
                    content(Modifier)
                }
            } else {
                content(Modifier)
            }
        }
    }
}

@PreviewTheme
@Composable
fun AppScaffoldPreview() {
    MeAppTheme {
        AppScaffold(
            title = "App Scaffold Title",
            containerColor = colorScheme.secondaryBackground,
            navigationIcon = { AppIconButton(AppIcons.Default.Close) {} },
            actions = { AppIconButton(AppIcons.Outlined.Help) {} },
            isRefreshing = false,
            onRefresh = {},
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
