---
name: wire-service
description: Add or update a service/protocol through the app's dependency graph. Use when the user says "add a service", "register this in DI", "inject this dependency", or when a change touches ServiceRegistry, DependencyContainer, or @Injector usage.
---

Wire a service or injectable dependency through the app's DI system.

The service or dependency change is: $ARGUMENTS

## Instructions

### 1 — Inspect the Existing DI Pattern

Read the relevant files first:
- `meApp/Core/DI/DependencyContainer.swift`
- `meApp/Core/Services/ServiceRegistry.swift`
- The target protocol in `meApp/Domain/Services/` or `meApp/Domain/Repositories/`
- The concrete implementation in `meApp/Data/Services/` or `meApp/Data/Storage/`
- Any planned `@Injector` call sites

### 2 — Classify the Dependency

Decide whether this is:
- essential at launch
- session-scoped after login
- concrete-only registration
- protocol + concrete dual registration
- test-only dependency that also needs `TestDependencyContainer` support

### 3 — Implement the Wiring

Apply the project conventions:
- Register concrete and protocol forms when both are used
- Keep launch-scoped dependencies in `registerEssentialServices()`
- Keep session-scoped dependencies in `registerSessionServices()`
- Update deregistration if the dependency has session lifecycle
- If tests depend on it via `@Injector`, update `meAppTests/Support/DI/TestDependencyContainer.swift`

### 4 — Check Injection Sites

Search for usage sites:
```bash
rg -n "@Injector|DependencyContainer.shared.resolve|{TypeName}" meApp meAppTests -g '*.swift'
```

Verify:
- all injected types can resolve
- protocol vs concrete usage is intentional
- no caller expects a dependency that was only partially registered

### 5 — Report

Summarize:
- where the dependency is registered
- whether test DI was updated
- any remaining callers that still need to be migrated

If the service introduces a new backend call, follow up with `/add-endpoint`.
