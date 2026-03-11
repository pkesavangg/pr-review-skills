---
name: add-endpoint
description: Add a new backend endpoint end-to-end across the iOS stack. Use when the user says "add an API call", "wire a new endpoint", "connect this screen to backend", or when a ticket introduces a new server interaction.
---

Add a backend endpoint or API-backed operation across the relevant layers.

The endpoint or API change is: $ARGUMENTS

## Instructions

### 1 — Find the Existing Vertical Slice

Inspect the relevant files before editing:
- `meApp/Domain/Models/API/EndPoints.swift`
- The relevant `meApp/Domain/Repositories/*RepositoryAPIProtocol.swift`
- The concrete `meApp/Data/API/*RepositoryAPI.swift`
- The calling service in `meApp/Data/Services/`
- Any store/view model that triggers the flow

Use repo-relative search:
```bash
rg -n "{keyword}" meApp -g '*.swift'
```

### 2 — Plan the Layer Changes

Decide which layers need updates:
- `Endpoint` case and URL request builder
- Request / response DTOs in `Domain/Models/API/`
- Repository API protocol method
- Concrete API repository implementation
- Service method or existing service flow
- Store / view model action if user-facing

### 3 — Implement Consistently

Apply the project pattern:
- Add the `Endpoint` case in `meApp/Domain/Models/API/EndPoints.swift`
- Add or update DTOs in `meApp/Domain/Models/API/`
- Add the method to the relevant `*RepositoryAPIProtocol`
- Implement the method in the concrete `*RepositoryAPI`
- Thread the call through the relevant service
- Update the triggering store or view model if needed

Follow existing conventions:
- All networking goes through `HTTPClientProtocol`
- Avoid embedding raw URLs outside `Endpoint`
- Prefer constructor injection in repositories/services that are directly unit tested
- Preserve `Dev` / `Production` environment behavior

### 4 — Check Integration Impact

Before finishing, verify:
- No duplicate endpoint already exists
- Request path/query construction is encoded safely
- DTO mapping is isolated to API/repository layer
- Any new dependency introduced into a store/service is compatible with current DI setup

If the API change also introduces a new service dependency, follow up with `/wire-service`.

### 5 — Recommend Follow-Up

Report:
- files changed by layer
- any new tests that should be added
- whether `/gen-test-file`, `/gen-mock`, or `coverage-gap-finder` should be run next
