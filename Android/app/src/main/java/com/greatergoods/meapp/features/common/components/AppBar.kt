package com.greatergoods.meapp.features.common.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.greatergoods.meapp.R
import com.greatergoods.meapp.theme.MeAppTheme


object AppBarIconDefaults {
    /**
     * Default left icon composable using ic_close.
     */
    @Composable
    fun Close() {
        Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = null,
            tint = MeAppTheme.colorScheme.primaryAction,
            modifier = Modifier.size(20.dp)
        )
    }

    /**
     * Placeholder right icon composable. Replace with actual help/info icon asset when available.
     */
    @Composable
    fun Help() {
        Icon(
            painter = painterResource(id = R.drawable.ic_info), // Placeholder
            contentDescription = null,
            tint = MeAppTheme.colorScheme.primaryAction,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * AppBar composable for top app bars with left/right icons and a title.
 *
 * @param title The title text to display (optional).
 * @param onLeftIconClick Callback for left icon click.
 * @param onRightIconClick Callback for right icon click (optional).
 * @param modifier Modifier for styling.
 * @param rightIcon Composable for right icon (optional, shown if provided).
 * @param leftIcon Composable for left icon (required).
 */
@Composable
fun AppBar(
    title: String?,
    onLeftIconClick: (() -> Unit) ? = null,
    onRightIconClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    rightIcon: (@Composable () -> Unit)? = null,
    leftIcon: (@Composable () -> Unit)? = null,
) {
    val spacing = MeAppTheme.spacing
    val colors = MeAppTheme.colorScheme
    val typography = MeAppTheme.typography
    val layoutDirection = LocalLayoutDirection.current

    Surface(
        color = colors.primary,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Icon
            if (leftIcon != null && onLeftIconClick != null) {
                IconButton(
                    onClick = onLeftIconClick,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(end = 15.dp)
                        .semantics { contentDescription = "Close" }
                ) {
                    leftIcon()
                }
            }
            // Title (centered, but start-aligned in RTL)
            Box(
                modifier = Modifier
                    .weight(1f),
                contentAlignment = if (layoutDirection == LayoutDirection.Ltr) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = typography.heading5,
                        color = colors.heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { contentDescription = "AppBarTitle" }
                    )
                }
            }
            // Right Icon (optional)
            if (rightIcon != null && onRightIconClick != null) {
                IconButton(
                    onClick = onRightIconClick,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(start = 15.dp)
                        .semantics { contentDescription = "Help" }
                ) {
                    rightIcon()
                }
            }
        }
    }
}

// --- Previews ---

@PreviewTheme
@Composable
fun AppBarPreview_BothIcons() {
    MeAppTheme {
        AppBar(
            title = "Label",
            onLeftIconClick = {},
            onRightIconClick = {},
            leftIcon = { AppBarIconDefaults.Close() },
            rightIcon = { AppBarIconDefaults.Help() }
        )
    }
}

@PreviewTheme
@Composable
fun AppBarPreview_OnlyLeftIcon() {
    MeAppTheme {
        AppBar(
            title = "Label",
            onLeftIconClick = {},
            leftIcon = { AppBarIconDefaults.Close() },
            rightIcon = null,
            onRightIconClick = null
        )
    }
}

@PreviewTheme
@Composable
fun AppBarPreview_OnlyIcons() {
    MeAppTheme {
        AppBar(
            title = null,
            onLeftIconClick = {},
            onRightIconClick = {},
            leftIcon = { AppBarIconDefaults.Close() },
            rightIcon = { AppBarIconDefaults.Help() }
        )
    }
}

@PreviewTheme
@Composable
fun AppBarPreview_OnlyTitle() {
    MeAppTheme {
        AppBar(
            title = "Title Only",
            onLeftIconClick = {},
            leftIcon = {},
            rightIcon = null,
            onRightIconClick = null
        )
    }
}
