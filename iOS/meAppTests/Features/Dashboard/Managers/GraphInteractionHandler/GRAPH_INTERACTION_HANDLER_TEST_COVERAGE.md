# GraphInteractionHandler Test Coverage

## Overview

Unit tests for `GraphInteractionHandler`, covering scroll buffering, cache reuse/invalidation, selection resolution, and corrected-scroll logic.

Current measured coverage (as of 2026-03-10): **96.52%**

**File:** `GraphInteractionHandlerTests.swift`
**Target Class:** `GraphInteractionHandler` (`iOS/meApp/Features/Dashboard/Managers/Graph/GraphInteractionHandler.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **5** |
| **Measured Coverage** | **96.52%** |

### Covered Areas

- scroll-position buffering and consume/reset behavior
- visible-operations cache hits and invalidation thresholds
- X-axis cache reuse and invalidation
- nearest-point selection and interpolated-value resolution
- corrected scroll-position behavior when latest data is or is not already visible

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/GraphPipelineCoverage.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/GraphDataPreparerTests -only-testing:meAppTests/YAxisCalculatorTests -only-testing:meAppTests/GraphRenderingConfigurationTests -only-testing:meAppTests/GraphInteractionHandlerTests -only-testing:meAppTests/GraphAnimationManagerTests
xcrun xccov view --report /tmp/GraphPipelineCoverage.xcresult | rg 'GraphDataPreparer.swift|YAxisCalculator.swift|GraphRenderingConfiguration.swift|GraphInteractionHandler.swift|GraphAnimationManager.swift'
```
