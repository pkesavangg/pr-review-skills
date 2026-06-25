---
name: visual-regression
description: Manage screenshot snapshots for UI tests and detect visual regressions in SwiftUI components
---

# Visual Regression Testing Skill

Manages screenshot comparisons for XCUITest-based UI tests to catch unintended visual changes in SwiftUI screens and components.

## When to Use

- After modifying SwiftUI component appearance (colors, spacing, fonts)
- When updating theme tokens or design system values
- Before merging UI-heavy PRs to catch visual regressions
- To establish baseline screenshots for regression testing

## Workflow

### 1. Establish Baseline Screenshots

```bash
# Run UI tests on physical device to generate baseline snapshots
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppUITests \
  -destination "id={DEVICE_ID}" \
  -only-testing:meAppUITests
```

Baseline snapshots are stored in:
```
meAppUITests/Assets/Snapshots/{TestClass}/{testMethod}.png
```

### 2. Compare Against New Changes

After making UI changes, re-run the tests. The framework will:
- Capture new screenshot
- Compare pixel-by-pixel to baseline
- Generate diff image highlighting changes
- Report pass/fail

### 3. Review & Update Baselines

If changes are intentional:
```bash
# Replace old baseline with new (after review)
cp meAppUITests/BuildArtifacts/{testName}_new.png \
   meAppUITests/Assets/Snapshots/{TestClass}/{testName}.png
```

## Test Structure

### Example UI Test

```swift
import XCTest

final class DashboardScreenTests: XCTestCase {
    private let app = XCUIApplication()
    
    func testDashboardLayout() throws {
        // Arrange
        app.launch()
        let dashboard = app.windows.firstMatch
        
        // Act & Assert - captures screenshot
        XCTAssertSnapshot(matching: dashboard, as: .image)
    }
    
    func testDashboardDarkMode() throws {
        app.launch()
        app.setPreferredColorScheme(.dark)
        
        let dashboard = app.windows.firstMatch
        XCTAssertSnapshot(matching: dashboard, as: .image)
    }
}
```

## Setup (One-time)

### 1. Install SnapshotTesting (if not already)

Add to `Package.swift` or SPM dependencies:
```swift
.package(url: "https://github.com/pointfreeco/swift-snapshot-testing.git", 
         from: "1.11.0")
```

### 2. Create Baseline Directory

```bash
mkdir -p meAppUITests/Assets/Snapshots
```

### 3. Generate Initial Baselines

Run tests with record mode:
```bash
# First run - records baselines
SNAPSHOT_TESTING_DEFAULT_RECORD=true \
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppUITests \
  -destination "id={DEVICE_ID}"
```

## CI/CD Integration

### GitHub Actions

```yaml
- name: Run UI Tests (Visual Regression)
  run: |
    xcodebuild test \
      -project iOS/meApp.xcodeproj \
      -scheme meAppUITests \
      -destination "generic/platform=iOS" \
      -configuration Dev
```

### Pre-commit Hook

Add to `.claude/settings.json` to warn on snapshot changes:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "if git diff --cached --name-only | grep -q 'Snapshots/.*\\.png'; then echo '⚠️  Screenshot baseline changed. Verify visual changes are intentional.'; fi"
          }
        ]
      }
    ]
  }
}
```

## Common Tasks

### Add New Screen to Regression Tests

1. Create test file in `meAppUITests/Screens/`
2. Add snapshot assertion for main layout
3. Add assertions for dark mode variant
4. Add assertions for different device sizes (iPhone 14, 15, etc.)
5. Run tests in record mode to establish baseline

### Update Baseline After Intentional Changes

```bash
# Review diff to ensure changes are correct
ls -la meAppUITests/BuildArtifacts/*_new.png | head -5

# Accept changes
for f in meAppUITests/BuildArtifacts/*_new.png; do
  base="${f%_new.png}"
  mv "$f" "$base.png"
done
```

### Detect Unexpected Regressions

```bash
# Run tests and fail on any differences
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppUITests \
  -destination "id={DEVICE_ID}" \
  -failOnUndeclaredDifferences
```

## Snapshot Strategy by Component

### Full Screens
- Test main layout (all elements visible)
- Test dark mode variant
- Test with content loading states
- Test empty states

### Component Library
- Test each component variant (size, state, style)
- Test with different content lengths
- Test accessibility (large text, high contrast)

### Avoid Snapshots For

- Time-dependent content (dates, timers)
- Animated content (use timing assertion instead)
- Dynamic network-dependent content (mock data)
- Platform UI elements (system sheets, alerts)

## Troubleshooting

### Snapshots Failing on CI but Passing Locally

**Cause**: Device/OS differences or rendering inconsistencies

**Solution**:
- Use consistent device in CI (e.g., iPhone 15 simulator)
- Run tests on physical device (more reliable)
- Ignore minor pixel diffs with fuzzy matching (1-2% threshold)

### Snapshot Size Growing

**Cause**: Recording new snapshots for each test variant

**Solution**:
- Only snapshot critical layouts, not every variation
- Use parameter-based testing for variants (don't snapshot each)
- Archive old snapshots in git LFS if disk space is concern

## References

- [swift-snapshot-testing](https://github.com/pointfreeco/swift-snapshot-testing)
- [XCTest UI Testing](https://developer.apple.com/documentation/xctest/user_interface_tests)
- [meApp UI Test Guide](../../../meAppUITests/docs/UI_TESTING.md)
