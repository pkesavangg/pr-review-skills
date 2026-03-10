# GraphRenderingConfiguration Test Coverage

## Overview

Unit tests for `GraphRenderingConfiguration`, covering visible-domain sizing, tick generation, scroll math, range formatting, and sampling helpers across periods.

Current measured coverage (as of 2026-03-10): **91.22%**

**File:** `GraphRenderingConfigurationTests.swift`
**Target Class:** `GraphRenderingConfiguration` (`iOS/meApp/Features/Dashboard/Managers/Graph/GraphRenderingConfiguration.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **7** |
| **Measured Coverage** | **91.22%** |

### Covered Areas

- visible-domain lengths and sample-date generation across week, month, year, and total
- X-axis value generation for empty, fixed-domain, rolling-range, and total-period inputs
- weekly, monthly, yearly, and total tick generation
- optimal scroll positioning, snapping, clamping, and cached-bounds anchor behavior
- selected-date formatting, range formatting, fallback labels, and X-axis label helpers

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/GraphPipelineCoverage.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/GraphDataPreparerTests -only-testing:meAppTests/YAxisCalculatorTests -only-testing:meAppTests/GraphRenderingConfigurationTests -only-testing:meAppTests/GraphInteractionHandlerTests -only-testing:meAppTests/GraphAnimationManagerTests
xcrun xccov view --report /tmp/GraphPipelineCoverage.xcresult | rg 'GraphDataPreparer.swift|YAxisCalculator.swift|GraphRenderingConfiguration.swift|GraphInteractionHandler.swift|GraphAnimationManager.swift'
```
