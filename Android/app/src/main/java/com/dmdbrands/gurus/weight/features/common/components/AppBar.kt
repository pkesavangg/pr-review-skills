// AppBar.kt
// Defines a customizable top app bar for Jetpack Compose with navigation and action icons.

package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.resources.AppIcons
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme.colorScheme
import com.dmdbrands.gurus.weight.theme.MeTheme.typography

/**
 * AppBar composable for top app bars with optional navigation and action icons.
 *
 * @param title The title text to display.
 * @param modifier Modifier for styling.
 * @param navigationIcon Composable for left icon (optional).
 * @param actions Composable for right icon(s) (optional).
 * @param centerTitle When true, the title is centered across the full bar width
 *   (CenterAlignedTopAppBar) instead of left-aligned next to the navigation icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    topBarContent: (@Composable (() -> Unit))? = null,
    borderColor: Color = colorScheme.utility,
    containerColor: Color = colorScheme.primaryBackground,
    enable: Boolean = false,
    centerTitle: Boolean = false,
    onClick: (() -> Unit)? = null,
    navigationIcon: (@Composable (() -> Unit))? = null,
    actions: (@Composable (() -> Unit))? = null,
) {
    val barModifier =
        Modifier
            .drawBehind {
                val borderSize = 1.dp.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = borderSize,
                )
            }
            .clickable(enabled = enable) {
                onClick?.invoke()
            }
    val barColors: TopAppBarColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = colorScheme.textHeading,
            navigationIconContentColor = colorScheme.primaryAction,
            actionIconContentColor = colorScheme.primaryAction,
        )
    val titleContent: @Composable () -> Unit = {
        if (topBarContent != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                topBarContent()
            }
        } else {
            Text(
                text = title ?: "",
                style = typography.heading5,
                color = colorScheme.textHeading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // TalkBack: mark the title as a heading so users can navigate by heading.
                // The Text's own text is the spoken name — previously a fixed
                // contentDescription overrode it, making TalkBack read "AppBarTitle".
                modifier = Modifier.semantics { heading() },
            )
        }
    }
    if (centerTitle) {
        CenterAlignedTopAppBar(
            modifier = barModifier,
            colors = barColors,
            title = titleContent,
            navigationIcon = { navigationIcon?.invoke() },
            actions = { actions?.invoke() },
        )
    } else {
        TopAppBar(
            modifier = barModifier,
            colors = barColors,
            title = titleContent,
            navigationIcon = { navigationIcon?.invoke() },
            actions = { actions?.invoke() },
        )
    }
}

// --- Preview Section ---
// Shows AppBar with and without actions for design review.
@OptIn(ExperimentalMaterial3Api::class)
@PreviewTheme
@Composable
fun AppBarPreview() {
    MeAppTheme {
        Column {
            AppBar(
                title = "Label",
                navigationIcon = { AppIconButton(AppIcons.Default.Close) { } },
                actions = { AppIconButton(AppIcons.Outlined.Help) { } },
            )
            Spacer(Modifier.height(24.dp))
            AppBar(
                title = "Label",
                navigationIcon = { AppIconButton(AppIcons.Default.Close) { } },
            )
            Spacer(Modifier.height(24.dp))
            AppBar(
                title = "Label",
            )
            Spacer(Modifier.height(24.dp))
            AppBar(
                navigationIcon = { AppIconButton(AppIcons.Default.Close) { } },
                actions = { AppIconButton(AppIcons.Outlined.Help) { } },
            )
            Spacer(Modifier.height(24.dp))

            AppBar(
                containerColor = Color.Transparent,
                navigationIcon = { AppIconButton(AppIcons.Default.Close) { } },
                actions = { AppIconButton(AppIcons.Outlined.Help) { } },
            )
            Spacer(Modifier.height(24.dp))
            AppBar(
                containerColor = Color.Transparent,
                navigationIcon = { AppIconButton(AppIcons.Default.Close) { } },
                actions = {
                    AppButton("Save", type = ButtonType.InlineTextPrimary, size = ButtonSize.Small) {}
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
