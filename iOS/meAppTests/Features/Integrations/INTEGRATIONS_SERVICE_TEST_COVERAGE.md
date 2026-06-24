# IntegrationsService Test Coverage Guide

## Purpose
This document explains what `IntegrationsService` flows are covered and how to run tests for connect/disconnect regression safety.

## Files
- Service under test: `meApp/Data/Services/IntegrationsService.swift`
- Protocols:
  - `meApp/Domain/Services/IntegrationServiceProtocol.swift`
  - `meApp/Domain/Services/HealthKitServiceProtocol.swift`
- Test suite: `meAppTests/Features/Integrations/IntegrationsServiceTests.swift`
- Test fixtures/mocks:
  - `meAppTests/Features/Integrations/Fixtures/IntegrationTestFixtures.swift`
  - `meAppTests/Features/Integrations/Mocks/MockIntegrationRepository.swift`
  - `meAppTests/Features/Integrations/Mocks/MockIntegrationsAPIRepository.swift`
  - `meAppTests/Features/Integrations/Mocks/MockHealthKitServiceForIntegrations.swift`

## Covered Flows
- Connect URL generation:
  - URL mapping by provider
  - no-active-account error
- Disconnect flow:
  - API remove success
  - API remove failure
  - local state clear
- Stored integration data:
  - get stored integration info
  - set stored integration info
  - clear stored integration info (`nil`)
  - local repository errors
  - account update side-effect and swallow-on-error behavior
- Utility/status:
  - is-integration-already-used
  - clear integration status (de-integrated state + account deleteHealthIntegration)
- Entry/health forwarding:
  - `syncNewEntry`
  - `deleteEntry`
  - `clearIntegration`
  - `logHealthEntry` (permissions present/absent)

## Run
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:meAppTests/IntegrationsServiceTests
```

Open Xcode coverage report and verify `IntegrationsService.swift` coverage is at least **85%**.
