# YAxisCalculator Test Coverage

## Overview

Unit tests for `YAxisCalculator`, covering fallback scales, small datasets, weightless domains, tick generation, and helper scale calculations.

Current measured coverage (as of 2026-03-10): **90.52%**

**File:** `YAxisCalculatorTests.swift`
**Target Class:** `YAxisCalculator` (`iOS/meApp/Features/Dashboard/Models/YAxisCalculator.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **12** |
| **Measured Coverage** | **90.52%** |

### Covered Areas

- empty-state fallback, goal-centered fallback, and last-scale reuse behavior
- small-dataset and large-dataset Y-axis calculation paths
- weightless and non-weightless negative-domain handling
- nice tick generation, dense tick compaction, sparse tick recovery, and readable step selection
- goal fallback helper behavior and edge-buffer expansion around plotted data

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -resultBundlePath /tmp/GraphPipelineCoverage.xcresult -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/GraphDataPreparerTests -only-testing:meAppTests/YAxisCalculatorTests -only-testing:meAppTests/GraphRenderingConfigurationTests -only-testing:meAppTests/GraphInteractionHandlerTests -only-testing:meAppTests/GraphAnimationManagerTests
xcrun xccov view --report /tmp/GraphPipelineCoverage.xcresult | rg 'GraphDataPreparer.swift|YAxisCalculator.swift|GraphRenderingConfiguration.swift|GraphInteractionHandler.swift|GraphAnimationManager.swift'
```
