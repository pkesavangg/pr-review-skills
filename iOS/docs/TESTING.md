# Testing (iOS)

This file is the high-level testing entrypoint for `meApp`.

## Scope
- `meAppTests/`: Unit tests
- `meAppUITests/`: UI tests

## Detailed Guides
- Unit testing guide: `meAppTests/docs/UNIT_TESTING.md`
- UI testing guide: `meAppUITests/docs/UI_TESTING.md`
- Coverage export/reporting guide: `docs/COVERAGE_REPORTING.md`

## Quick Start
1. Open `meApp.xcodeproj`.
2. Run unit tests with scheme `meAppTests`.
3. Run UI tests with scheme `meAppUITests`.
4. Use `docs/COVERAGE_REPORTING.md` for shareable coverage artifacts.

## Unit Tests (Basic)
- Target: `meAppTests`
- Goal: validate logic in services/stores/forms/repositories in isolation.
- Style: Arrange / Act / Assert with protocol-based mocks.
- Isolation: reset shared DI state per suite (`TestDependencyContainer.reset()`).
- Runtime: fast, deterministic, no real backend dependency.
- **All tests must run on a connected physical device — never use a simulator.**

Find the connected device ID first:
```bash
xcodebuild -project iOS/meApp.xcodeproj -scheme meAppTests -showdestinations 2>&1 \
  | grep "platform:iOS," | grep -v Simulator | head -1
```

Run:
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

## UI Tests (Basic)
- Target: `meAppUITests`
- Goal: validate end-to-end screen behavior and user interactions.
- Style: launch app with scenario flags and interact through accessibility IDs.
- DI model: app startup overrides protocol dependencies for deterministic scenarios.
- Runtime: slower than unit tests, but verifies real navigation/rendering flows.
- **All tests must run on a connected physical device — never use a simulator.**

Find the connected device ID first:
```bash
xcodebuild -project iOS/meApp.xcodeproj -scheme meAppUITests -showdestinations 2>&1 \
  | grep "platform:iOS," | grep -v Simulator | head -1
```

Run:
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppUITests \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

## Testing Principles
- Prefer deterministic tests.
- Use mocks for unit tests and scenario-driven app-launch overrides for UI tests.
- Keep assertions behavior-first and stable via accessibility identifiers for UI flows.
