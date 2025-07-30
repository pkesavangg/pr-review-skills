package com.dmdbrands.gurus.weight.features.common.components

import androidx.compose.ui.tooling.preview.Devices.FOLDABLE
import androidx.compose.ui.tooling.preview.Devices.PHONE
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL

/**
 * Annotation for generating Compose previews in both light and dark mode,
 * and for mobile and tablet portrait configurations.
 *
 * Usage:
 * ```
 * @PreviewTheme
 * @Composable
 * fun MyComposablePreview() {
 *     MyComposable()
 * }
 * ```
 * This will show previews for:
 * - Light mode (mobile)
 * - Dark mode (mobile)
 * - Light mode (tablet portrait)
 * - Dark mode (tablet portrait)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Preview(
    name = "Phone -  Light",
    uiMode = Configuration.UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL,
    device = PHONE,
    showSystemUi = true,
)
@Preview(name = "Phone - Dark", uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL, device = PHONE, showSystemUi = true)
@Preview(
    name = "Foldable -  Light",
    device = FOLDABLE,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO or UI_MODE_TYPE_NORMAL,
)
@Preview(
    name = "Foldable - Dark",
    device = FOLDABLE,
    showSystemUi = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
@Preview(
    name = "Tablet -  Light",
    device = "spec:width=1280dp,height=800dp,dpi=240,orientation=portrait",
    showSystemUi = true,
)
@Preview(
    name = "Tablet - Dark",
    device = "spec:width=1280dp,height=800dp,dpi=240,orientation=portrait",
    showSystemUi = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
annotation class PreviewTheme

// Usage pattern for Compose Previews:
// Place the following in your composable preview file:
//
// @Preview(name = "Light - Mobile", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=360,height=800")
// @Preview(name = "Dark - Mobile", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=360,height=800")
// @Preview(name = "Light - Tablet", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=800,height=1280")
// @Preview(name = "Dark - Tablet", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=800,height=1280")
// @Composable
// fun MyComposablePreview() {
//     MyComposable()
// }
//
// You can use the @PreviewTheme annotation as a marker for code search or documentation, but Compose does not support meta-annotation expansion for previews.
