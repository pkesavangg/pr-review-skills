// AppBar.kt
// Defines a customizable top app bar for Jetpack Compose with navigation and action icons.

package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greatergoods.meapp.features.common.strings.AppBarStrings
import com.greatergoods.meapp.resources.AppIcons
import com.greatergoods.meapp.theme.MeAppTheme
import com.greatergoods.meapp.theme.MeTheme.colorScheme
import com.greatergoods.meapp.theme.MeTheme.typography

/**
 * AppBar composable for top app bars with optional navigation and action icons.
 *
 * @param title The title text to display.
 * @param modifier Modifier for styling.
 * @param navigationIcon Composable for left icon (optional).
 * @param actions Composable for right icon(s) (optional).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = colorScheme.primaryBackground,
    borderColor: Color = Color.Transparent,
    navigationIcon: (@Composable (() -> Unit))? = null,
    actions: (@Composable (() -> Unit))? = null,
) {
    TopAppBar(
        modifier = Modifier.drawBehind{
            val borderSize = 1.dp.toPx()
            drawLine(
                color = borderColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = borderSize
            )
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = containerColor,
                titleContentColor = colorScheme.textHeading,
                navigationIconContentColor = colorScheme.primaryAction,
                actionIconContentColor = colorScheme.primaryAction,
            ),
        title = {
            Text(
                text = title ?: "",
                style = typography.heading5,
                color = colorScheme.textHeading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { contentDescription = AppBarStrings.AppBarTitleContentDescription },
            )
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = { actions?.invoke() },
    )
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
