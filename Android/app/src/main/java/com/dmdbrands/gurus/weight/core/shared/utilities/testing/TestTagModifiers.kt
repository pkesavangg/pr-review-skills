package com.dmdbrands.gurus.weight.core.shared.utilities.testing

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.dmdbrands.gurus.weight.BuildConfig

/**
 * Exposes every descendant [androidx.compose.ui.platform.testTag] as an Android `resource-id`
 * in the rendered view hierarchy, so UiAutomator / Appium can select Compose nodes by id
 * (e.g. `toast_card`, `dialog_card`, `modal_card`).
 *
 * Gated to debug builds: this is test scaffolding for UiAutomator/Appium automation (which runs
 * against the debug build), so the internal tag names are not exposed in the production view
 * hierarchy. In release builds the modifier is returned unchanged — a no-op.
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
  if (BuildConfig.DEBUG) semantics { testTagsAsResourceId = true } else this

/**
 * Applies a derived per-row [testTag] of the form `<base>_<stableId>` so each repeated row /
 * per-row control resolves to exactly one node — e.g.
 * `Modifier.rowTestTag(TestTags.Landing.AccountCardRow, account.id)` -> `account_card_row_<id>`.
 *
 * Convenience wrapper over [TestTags.rowTag]; use it wherever a row tag is applied via a
 * [Modifier]. Do not hand-write the `_` join at the call site.
 */
fun Modifier.rowTestTag(base: String, stableId: Any): Modifier = testTag(TestTags.rowTag(base, stableId))
