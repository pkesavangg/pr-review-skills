package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmdbrands.gurus.weight.features.common.components.strings.AppLoaderStrings
import com.dmdbrands.gurus.weight.theme.MeAppTheme
import com.dmdbrands.gurus.weight.theme.MeTheme

enum class LoaderStyle {
    CIRCULAR,
    DASHED,
    DOT,
}

sealed class LoaderConfig(
    open val color: Color,
) {
    data class Circular(
        override val color: Color,
        val strokeWidth: Dp,
        val size: Float,
    ) : LoaderConfig(color)

    data class Dashed(
        override val color: Color,
        val size: Dp,
        val strokeWidth: Float,
        val dashLength: Float,
        val gapLength: Float,
        val sweepAngle: Float,
    ) : LoaderConfig(color)

    data class Dot(
        override val color: Color,
        val minRadius: Float,
        val maxRadius: Float,
        val durationMillis: Int,
    ) : LoaderConfig(color)
}

object LoaderDefaults {

    // In-progress loaders use the WG brand blue (Figma "Loading": wg-primary #1565C0). (MOB-1259)
    val baseDotConfig: LoaderConfig.Dot
        @Composable get() = LoaderConfig.Dot(
            color = MeTheme.colorScheme.wgPrimary,
            minRadius = 6f,
            maxRadius = 12f,
            durationMillis = 700,
        )

    val baseDashedConfig: LoaderConfig.Dashed
        @Composable get() = LoaderConfig.Dashed(
            color = MeTheme.colorScheme.wgPrimary,
            size = 22.dp,
            strokeWidth = 4f,
            dashLength = 10f,
            gapLength = 8f,
            sweepAngle = 270f,
        )

    val baseCircularConfig: LoaderConfig.Circular
        @Composable get() = LoaderConfig.Circular(
            color = MeTheme.colorScheme.wgPrimary,
            strokeWidth = 2.dp,
            size = 22f,
        )

    @Composable
    fun defaultFor(style: LoaderStyle): LoaderConfig =
        when (style) {
            LoaderStyle.CIRCULAR -> baseCircularConfig
            LoaderStyle.DASHED -> baseDashedConfig
            LoaderStyle.DOT -> baseDotConfig
        }
}


/**
 * Displays a loader based on the given style and config. If config is not provided, uses LoaderDefaults.
 */
@Composable
fun AppLoader(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    message: String? = null,
    labelComposable: @Composable (() -> Unit)? = null,
    style: LoaderStyle = LoaderStyle.DASHED,
) {
    val config: LoaderConfig = LoaderDefaults.defaultFor(style)
    // TalkBack: when the spinner has no visible message/label it is otherwise silent, so
    // announce "Loading". When a message/label IS shown, that text is read instead (no
    // duplicate announcement).
    val announceLoading = isLoading && labelComposable == null && message == null
    Row(
        modifier = modifier.loadingAnnouncement(announceLoading),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            when (style) {
                LoaderStyle.CIRCULAR -> {
                    val circularConfig = config as LoaderConfig.Circular
                    CircularProgressIndicator(
                        color = circularConfig.color,
                        strokeWidth = circularConfig.strokeWidth,
                        modifier = Modifier.size(circularConfig.size.dp),
                    )
                }

                LoaderStyle.DASHED -> {
                    val dashedConfig = config as LoaderConfig.Dashed
                    DashedCircularLoader(
                        color = dashedConfig.color,
                        modifier = Modifier.size(dashedConfig.size),
                        strokeWidth = dashedConfig.strokeWidth,
                        dashLength = dashedConfig.dashLength,
                        gapLength = dashedConfig.gapLength,
                        sweepAngle = dashedConfig.sweepAngle,
                    )
                }

                LoaderStyle.DOT -> {
                    val dotConfig = config as LoaderConfig.Dot
                    PulsingDotLoader(
                        color = dotConfig.color,
                        modifier = Modifier.size((dotConfig.maxRadius * 2).dp),
                        minRadius = dotConfig.minRadius,
                        maxRadius = dotConfig.maxRadius,
                        durationMillis = dotConfig.durationMillis,
                    )
                }
            }
        }
        if (labelComposable != null) {
            Spacer(modifier = Modifier.width(8.dp))
            labelComposable()
        } else if (message != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MeTheme.typography.heading5,
                color = MeTheme.colorScheme.textBody,
            )
        }
    }
}

/**
 * TalkBack: announce "Loading" via a polite live region when the loader has no visible
 * message/label (otherwise the spinner is silent). When text is shown, that is read instead.
 */
private fun Modifier.loadingAnnouncement(announce: Boolean): Modifier =
    if (announce) {
        semantics {
            contentDescription = AppLoaderStrings.accLoadingLabel
            liveRegion = LiveRegionMode.Polite
        }
    } else {
        this
    }

@PreviewTheme
@Composable
private fun AppLoaderPreview() {
    MeAppTheme {
        AppLoaderPreviewContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLoaderPreviewContent() {
    // State for dropdown
    var expanded by remember { mutableStateOf(false) }
    var selectedStyle by remember { mutableStateOf(LoaderStyle.DASHED) }
    val styles = LoaderStyle.entries.toTypedArray()
    styles.map { it.name }

    Column(modifier = Modifier.padding(24.dp)) {
        // Dropdown for style selection
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = selectedStyle.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Loader Style") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                styles.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.name) },
                        onClick = {
                            selectedStyle = style
                            expanded = false
                        },
                    )
                }
            }
        }
        // Spacer
        Spacer(modifier = Modifier.size(24.dp))
        // AppLoader with selected style, loading true, with message
        AppLoader(
            isLoading = true,
            message = "Loading...",
            style = selectedStyle,
        )
        Spacer(modifier = Modifier.size(120.dp))
    }
}
