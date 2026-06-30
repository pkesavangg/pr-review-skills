---
name: analyze-coverage
description: Parse XCTest coverage reports and identify uncovered code paths, regressions, and test gaps
---

# Coverage Analysis Skill

Analyzes XCTest coverage reports to identify uncovered lines, branches, and regressions across the iOS project.

## When to Use

- After running unit tests to identify coverage gaps
- When investigating coverage regressions between builds
- Before finalizing a PR to ensure new code has adequate test coverage
- When planning test prioritization for high-impact areas

## Inputs

Ask Claude with:
- Path to coverage report (`.json`, `.xml`, or raw Xcode output)
- Target coverage thresholds (default: 80% for services, 85% for forms)
- Focus area (e.g., `Features/Dashboard`, `Data/Services`, `Domain/Models`)

## Workflow

### 1. Parse Coverage Data

```bash
# Generate coverage report from Xcode
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -enableCodeCoverage YES \
  -destination "id={DEVICE_ID}" \
  -resultBundlePath coverage.xcresult
```

### 2. Analyze Results with `coverage-gap-finder` (Optional but Recommended)

**For comprehensive gap analysis**, invoke the `coverage-gap-finder` agent to automatically identify coverage gaps and suggest specific test cases. This subagent will:
- Parse coverage report and identify all uncovered lines and branches
- Rank gaps by impact (most-used code first)
- Suggest specific test case names and scenarios for each gap
- Provide exact line numbers and method names for targeted testing
- Identify patterns in uncovered code (error paths, edge cases, guard conditions)

**Invocation:**
```
Spawn coverage-gap-finder agent with:
- Coverage report path: meAppTests/Reports/coverage-report.md (or .json)
- Threshold: 80% (or appropriate layer minimum)
- Focus area: Features/Dashboard, Data/Services, etc. (optional)
- Output: Prioritized list of test cases with file:line references
```

The agent will return:
- List of uncovered methods with exact line numbers
- Suggested test case names for each gap
- Priority ranking (blocks threshold, high impact, nice-to-have)
- Code snippets showing the uncovered branches

**Alternatively, manual analysis:**

Claude will:
- Extract uncovered lines and branch conditions
- Identify high-impact areas below threshold
- Flag recent regressions (compare to baseline)
- Suggest specific test cases to add

### 3. Generate Report

Output includes:
- **By Layer**: Service, Store, Form coverage with thresholds
- **Uncovered Paths**: Specific lines with context (file:line)
- **Branch Gaps**: Conditional branches missing test coverage
- **Regression Alert**: Modules that dropped below threshold
- **Priority Queue**: Ranked by impact (most-used code first)

## Example Output

```
## Coverage Summary

### Data/Services (85% threshold)
✅ EntryService: 87% (↑2% from baseline)
⚠️  BluetoothService: 79% (↓3% regression)
  - Missing: Bluetooth error recovery paths
  - Uncovered: lines 142-156 (connection timeout handling)

### Features/Dashboard (80% threshold)
✅ DashboardStore: 82%
🔴 DashboardScreen: 71% (UI layer excluded - expected)

## Test Prioritization

1. **Critical** (blocks threshold)
   - BluetoothService: error handling paths
   - WifiScaleService: reconnection logic

2. **High Impact** (frequently used)
   - EntryService: validation edge cases
   - AccountService: session management

3. **Nice to Have** (error paths)
   - Retry logic for API timeouts
   - Fallback UI states
```

## Coverage Thresholds by Layer

| Layer | Threshold | Why |
|-------|-----------|-----|
| `Data/Services` | 80% | Critical business logic |
| `Data/Services` (Auth/Account/Sync) | 85% | Security-sensitive |
| Stores / ViewModels | 80% | State management |
| Forms / Validation | 85% | UX-critical |
| `Data/API` adapters | 75% | Lower priority (network layer) |
| UI Views | Excluded | SwiftUI hard to test |

## Commands to Generate Reports

### Full Test Coverage
```bash
# Run all tests with coverage
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -enableCodeCoverage YES \
  -destination "id={DEVICE_ID}"

# Export to JSON
xcodebuild -resultBundlePath coverage.xcresult -json
```

### By Feature
```bash
# Test single feature
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -only-testing:meAppTests/DashboardStoreTests \
  -enableCodeCoverage YES \
  -destination "id={DEVICE_ID}"
```

## After Analysis

Ask Claude to:
- Generate a list of missing test cases with file:line references
- Suggest specific test scenarios for uncovered branches
- Create a prioritized PR comment with coverage gaps
- Track coverage trend over time (baseline vs. current)

## References

- [Xcode Code Coverage](https://developer.apple.com/documentation/xcode/configuring_code_coverage)
- [meApp Coverage Guide](../../../docs/COVERAGE_REPORTING.md)
- [Unit Testing Best Practices](../../../meAppTests/docs/UNIT_TESTING.md)
