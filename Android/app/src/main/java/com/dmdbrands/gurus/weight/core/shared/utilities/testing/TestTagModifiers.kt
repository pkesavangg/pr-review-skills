package com.dmdbrands.gurus.weight.core.shared.utilities.testing

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Exposes every descendant [androidx.compose.ui.platform.testTag] as an Android `resource-id`
 * in the rendered view hierarchy, so UiAutomator / Appium can select Compose nodes by id
 * (e.g. `toast_card`, `dialog_card`, `modal_card`).
 *
 * Compose resolves `testTagsAsResourceId` per window: the root `setContent`, and every
 * `Dialog` / `Popup` / bottom sheet each own a separate semantics tree, and the flag does
 * NOT cross those window boundaries. Apply this at the root of every window whose tagged
 * nodes must be Appium-visible — applying it only at `setContent` will not expose tags that
 * live inside dialogs (such as the toast, which renders in its own `Dialog` window).
 *
 * See MOB-1099.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.exposeTestTagsAsResourceId(): Modifier =
  semantics { testTagsAsResourceId = true }
