# Compose UI Testing Pattern (Instrumented)

## Overview

Compose UI tests are **instrumented tests** that run on a real Android device or emulator. They live in `androidTest/`, not `test/`, because they require the Compose rendering pipeline and an Android `Context`.

**Key principle**: Test **behavior**, not implementation. Use semantic matchers (text, content descriptions, test tags) — never rely on internal View IDs or class names.

## MeApp dependencies (already configured)

```kotlin
// In app/build.gradle.kts
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.ui.test.junit4)     // ComposeTestRule, matchers, actions
androidTestImplementation(libs.truth)                       // Truth assertions
debugImplementation(libs.androidx.ui.tooling)
debugImplementation(libs.androidx.ui.test.manifest)          // Test activity for createComposeRule()
```

> **No MockK in Compose UI tests** — use callback flags/counters to verify interactions. MockK introduces complexity that's unnecessary for UI behavior testing.

## Complete test file structure

```kotlin
package com.dmdbrands.gurus.weight.features.{feature}.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dmdbrands.gurus.weight.features.common.theme.MeAppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class {ComponentName}Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // Shared helper — reduce duplication across tests
    // -------------------------------------------------------------------------

    /**
     * Set up the composable under test with configurable props.
     * Default values match the most common happy-path scenario.
     */
    private fun setContent(
        title: String = "Default Title",
        isEnabled: Boolean = true,
        showSecondary: Boolean = false,
        onPrimaryClick: () -> Unit = {},
        onSecondaryClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MeAppTheme {
                {ComponentName}(
                    title = title,
                    isEnabled = isEnabled,
                    showSecondary = showSecondary,
                    onPrimaryClick = onPrimaryClick,
                    onSecondaryClick = onSecondaryClick,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Display / Rendering
    // -------------------------------------------------------------------------

    @Test
    fun displaysTitle() {
        setContent(title = "My Title")
        composeTestRule.onNodeWithText("My Title").assertIsDisplayed()
    }

    @Test
    fun secondaryButton_hiddenByDefault() {
        setContent(showSecondary = false)
        composeTestRule.onNodeWithText("Secondary Action").assertDoesNotExist()
    }

    @Test
    fun secondaryButton_visibleWhenEnabled() {
        setContent(showSecondary = true)
        composeTestRule.onNodeWithText("Secondary Action").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // User interactions
    // -------------------------------------------------------------------------

    @Test
    fun primaryClick_invokesCallback() {
        var clicked = false
        setContent(onPrimaryClick = { clicked = true })

        composeTestRule.onNodeWithText("Primary Action").performClick()

        assertThat(clicked).isTrue()
    }

    @Test
    fun disabledButton_doesNotInvokeCallback() {
        var clicked = false
        setContent(isEnabled = false, onPrimaryClick = { clicked = true })

        composeTestRule.onNodeWithText("Primary Action").performClick()

        assertThat(clicked).isFalse()
    }

    // -------------------------------------------------------------------------
    // State changes
    // -------------------------------------------------------------------------

    @Test
    fun disabledState_rendersButtonAsDisabled() {
        setContent(isEnabled = false)
        composeTestRule.onNodeWithText("Primary Action").assertIsNotEnabled()
    }
}
```

## Test patterns

### Pattern A: Display verification

Test that all expected UI elements are rendered:

```kotlin
@Test
fun allLabelsAreDisplayed() {
    setContent()

    composeTestRule.onNodeWithText("Title").assertIsDisplayed()
    composeTestRule.onNodeWithText("Subtitle").assertIsDisplayed()
    composeTestRule.onNodeWithText("Save").assertIsDisplayed()
}

// Conditional rendering
@Test
fun errorMessage_displayedWhenErrorIsNotNull() {
    setContent(errorMessage = "Something went wrong")
    composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
}

@Test
fun errorMessage_hiddenWhenNull() {
    setContent(errorMessage = null)
    composeTestRule.onNodeWithText("Something went wrong").assertDoesNotExist()
}
```

### Pattern B: Click actions with callback verification

Use boolean flags or counters — no MockK:

```kotlin
@Test
fun saveButton_invokesOnSave() {
    var saveCalled = false
    setContent(onSave = { saveCalled = true })

    composeTestRule.onNodeWithText("Save").performClick()

    assertThat(saveCalled).isTrue()
}

// Verify callback arguments
@Test
fun listItem_invokesOnSelectWithCorrectId() {
    var selectedId: String? = null
    setContent(onSelect = { id -> selectedId = id })

    composeTestRule.onNodeWithText("Item A").performClick()

    assertThat(selectedId).isEqualTo("item-a")
}

// Counter for repeated interactions
@Test
fun incrementButton_callsOnClickMultipleTimes() {
    var clickCount = 0
    setContent(onClick = { clickCount++ })

    composeTestRule.onNodeWithText("+").performClick()
    composeTestRule.onNodeWithText("+").performClick()

    assertThat(clickCount).isEqualTo(2)
}
```

### Pattern C: Text input

```kotlin
@Test
fun textField_acceptsInput() {
    setContent()

    composeTestRule.onNodeWithTag("email_input").performTextInput("john@example.com")

    composeTestRule.onNodeWithTag("email_input")
        .assertTextEquals("john@example.com")
}

@Test
fun textField_clearsOnClearClick() {
    setContent()

    composeTestRule.onNodeWithTag("email_input").performTextInput("test")
    composeTestRule.onNodeWithContentDescription("Clear").performClick()

    composeTestRule.onNodeWithTag("email_input").assertTextEquals("")
}
```

> **Requires `Modifier.testTag()`** in the composable source for non-text elements.

### Pattern D: List/collection rendering

```kotlin
@Test
fun list_displaysAllItems() {
    val items = listOf("Apple", "Banana", "Cherry")
    setContent(items = items)

    items.forEach { item ->
        composeTestRule.onNodeWithText(item).assertIsDisplayed()
    }
}

@Test
fun emptyList_showsEmptyState() {
    setContent(items = emptyList())

    composeTestRule.onNodeWithText("No items found").assertIsDisplayed()
}

@Test
fun list_itemCountIsCorrect() {
    setContent(items = listOf("A", "B", "C"))

    composeTestRule.onAllNodesWithTag("list_item").assertCountEquals(3)
}
```

### Pattern E: Enabled/disabled states

```kotlin
@Test
fun submitButton_enabledWhenFormIsValid() {
    setContent(isFormValid = true)
    composeTestRule.onNodeWithText("Submit").assertIsEnabled()
}

@Test
fun submitButton_disabledWhenFormIsInvalid() {
    setContent(isFormValid = false)
    composeTestRule.onNodeWithText("Submit").assertIsNotEnabled()
}
```

### Pattern F: Content descriptions (accessibility)

```kotlin
@Test
fun backButton_hasContentDescription() {
    setContent()
    composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
}

@Test
fun profileImage_hasContentDescription() {
    setContent(userName = "John")
    composeTestRule.onNodeWithContentDescription("Profile image for John").assertExists()
}
```

### Pattern G: Theme-dependent rendering

All Compose tests must wrap content in `MeAppTheme` to ensure correct theming:

```kotlin
private fun setContent(/* params */) {
    composeTestRule.setContent {
        MeAppTheme {
            // Your composable here — theme tokens will resolve correctly
            {ComponentName}(/* params */)
        }
    }
}
```

> **Never hardcode colors/fonts in tests** — they change with theme. Test behavior and content, not visual styling.

### Pattern H: Navigation-related composables

For composables that receive `LocalNavBackStack` or navigation callbacks:

```kotlin
import androidx.compose.runtime.CompositionLocalProvider

private fun setContent(
    onNavigateToDetails: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    composeTestRule.setContent {
        MeAppTheme {
            {ScreenName}(
                onNavigateToDetails = onNavigateToDetails,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}

@Test
fun itemClick_triggersNavigateToDetails() {
    var navigatedId: String? = null
    setContent(onNavigateToDetails = { id -> navigatedId = id })

    composeTestRule.onNodeWithText("Item 1").performClick()

    assertThat(navigatedId).isEqualTo("item-1")
}
```

### Pattern I: Loading/error states

```kotlin
@Test
fun loadingState_showsSpinner() {
    setContent(isLoading = true)

    composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    composeTestRule.onNodeWithTag("content").assertDoesNotExist()
}

@Test
fun errorState_showsErrorMessageAndRetry() {
    setContent(error = "Network error")

    composeTestRule.onNodeWithText("Network error").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
}

@Test
fun retryButton_invokesOnRetry() {
    var retryCalled = false
    setContent(error = "Network error", onRetry = { retryCalled = true })

    composeTestRule.onNodeWithText("Retry").performClick()

    assertThat(retryCalled).isTrue()
}
```

### Pattern J: Synchronization for async operations

Compose tests auto-synchronize with the composition. For animations or delayed operations:

```kotlin
// Wait for idle (recomposition complete)
composeTestRule.waitForIdle()

// Wait for a condition
composeTestRule.waitUntil(timeoutMillis = 5000) {
    composeTestRule.onAllNodesWithText("Loaded").fetchSemanticsNodes().isNotEmpty()
}

// Wait for at least one node
composeTestRule.waitUntilAtLeastOneExists(
    hasText("Loaded"),
    timeoutMillis = 5000,
)

// Advance virtual clock (for animations)
composeTestRule.mainClock.advanceTimeBy(1000)
```

### Pattern K: Debugging — print semantics tree

When tests fail with "node not found", print the tree to see what's actually rendered:

```kotlin
@Test
fun debugTest() {
    setContent()

    // Print full semantics tree to logcat
    composeTestRule.onRoot().printToLog("COMPOSE_TEST")

    // Use unmerged tree to see individual children
    composeTestRule.onRoot(useUnmergedTree = true).printToLog("COMPOSE_TEST_UNMERGED")
}
```

## Semantic matchers reference

### Finders (single node)

| Finder | Use when |
|--------|---------|
| `onNodeWithText("text")` | Text is unique and visible |
| `onNodeWithText("text", substring = true)` | Partial text match |
| `onNodeWithText("text", ignoreCase = true)` | Case-insensitive match |
| `onNodeWithTag("tag")` | Element has `Modifier.testTag()` |
| `onNodeWithContentDescription("desc")` | Accessibility label |
| `onNode(hasText("A") and hasClickAction())` | Combined matchers |

### Finders (multiple nodes)

| Finder | Use when |
|--------|---------|
| `onAllNodesWithText("text")` | Multiple matching elements |
| `onAllNodesWithTag("tag")` | Multiple tagged elements |
| `onAllNodesWithContentDescription("desc")` | Multiple accessible elements |

### Assertions

| Assertion | Verifies |
|-----------|----------|
| `assertIsDisplayed()` | Node exists AND is visible |
| `assertExists()` | Node exists (may be off-screen) |
| `assertDoesNotExist()` | Node not in the semantics tree |
| `assertIsEnabled()` | Node is enabled (clickable) |
| `assertIsNotEnabled()` | Node is disabled |
| `assertIsSelected()` | Node is selected |
| `assertIsFocused()` | Node has focus |
| `assertTextEquals("text")` | Node text matches exactly |
| `assertTextContains("sub")` | Node text contains substring |
| `assertHasClickAction()` | Node responds to clicks |
| `assertContentDescriptionEquals("desc")` | Accessibility label matches |
| `.assertCountEquals(n)` | (on multi-node) Exactly n nodes match |

### Actions

| Action | Does |
|--------|------|
| `performClick()` | Single tap |
| `performTextInput("text")` | Type text into field |
| `performTextClearance()` | Clear text field |
| `performTextReplacement("new")` | Replace all text |
| `performScrollTo()` | Scroll to make node visible |
| `performScrollToIndex(5)` | Scroll to index in LazyColumn |
| `performTouchInput { swipeLeft() }` | Gesture |

### Selectors (traversal)

| Selector | Does |
|----------|------|
| `.onChildren()` | Get child nodes |
| `.onParent()` | Get parent node |
| `.filter(matcher)` | Filter multi-node results |
| `.onFirst()` | First matching node |
| `.onLast()` | Last matching node |
| `[index]` | Node at index |

## Test file placement

Mirror the source package path in `androidTest/`:

```
Source:  app/src/main/java/com/dmdbrands/gurus/weight/features/settings/components/SettingsCard.kt
Test:    app/src/androidTest/java/com/dmdbrands/gurus/weight/features/settings/components/SettingsCardTest.kt

Source:  app/src/main/java/com/dmdbrands/gurus/weight/features/common/components/DialogQueueHost.kt
Test:    app/src/androidTest/java/com/dmdbrands/gurus/weight/features/common/components/DialogQueueHostTest.kt

Source:  app/src/main/java/com/dmdbrands/gurus/weight/features/dashboard/views/screens/DashboardScreen.kt
Test:    app/src/androidTest/java/com/dmdbrands/gurus/weight/features/dashboard/views/screens/DashboardScreenTest.kt
```

## Run commands

```bash
# Compile check (no emulator needed)
./gradlew :app:compileDebugAndroidTestKotlin

# Single Compose test class (requires emulator)
./gradlew :app:connectedDebugAndroidTest --tests "*.{ComponentName}Test"

# All instrumented tests
./gradlew :app:connectedDebugAndroidTest
```

## Common errors & fixes

| Error | Fix |
|---|---|
| `IllegalStateException: No compose views found` | Ensure `debugImplementation(libs.androidx.ui.test.manifest)` is in `build.gradle.kts` |
| `ComposeNotIdleException` | Add `composeTestRule.waitForIdle()` or `waitUntil { }` for async operations |
| `AssertionError: Expected 1 node, found 0` | Check text spelling, try `substring = true` or `useUnmergedTree = true` |
| `AssertionError: Expected 1 node, found 2+` | Use `onAllNodesWithText()` and pick specific node, or add `Modifier.testTag()` for uniqueness |
| `No Activity set` | Ensure using `createComposeRule()` (needs `ui-test-manifest`) or `createAndroidComposeRule<Activity>()` |
| `Theme tokens not resolving` | Wrap content in `MeAppTheme { }` in `setContent()` helper |

## Compose UI test success criteria

- [ ] Uses `createComposeRule()` (standalone, no Activity dependency)
- [ ] Content wrapped in `MeAppTheme { }` (supports light/dark themes)
- [ ] Private `setContent()` helper with default params
- [ ] All visible text labels verified with `onNodeWithText().assertIsDisplayed()`
- [ ] Click actions tested: `performClick()` → callback flag verified with Truth
- [ ] Conditional rendering tested (show/hide based on props)
- [ ] Loading/error states tested if applicable
- [ ] Disabled state tested if applicable (`assertIsNotEnabled()`)
- [ ] `Modifier.testTag()` used for non-text elements, tested via `onNodeWithTag()`
- [ ] No MockK — use callback flags/counters for interaction verification
- [ ] No hardcoded theme values tested (colors, fonts) — test content/behavior only
- [ ] `./gradlew :app:compileDebugAndroidTestKotlin` passes
- [ ] `./gradlew :app:connectedDebugAndroidTest --tests "*.{ComponentName}Test"` passes
- [ ] File placed in `androidTest/` mirroring source package
