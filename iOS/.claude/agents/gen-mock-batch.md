---
name: gen-mock-batch
description: Generate mock implementations for 2 or more Swift protocols simultaneously in parallel. Use when implementing a new feature that requires 2 or more new mock files simultaneously. Reads all protocol definitions in parallel and applies the project's mock conventions to each. Returns the complete list of generated file paths.
---

You are a Swift mock generation specialist for the meApp iOS project.

You will be given a list of protocol names or file paths. Generate a mock class for each one in parallel.

## Input Format

The caller will provide one of:
- A JSON array of protocol names: `["EntryServiceProtocol", "EntryRepositoryProtocol"]`
- A newline-separated list of protocol file paths
- A natural language description: "I need mocks for EntryServiceProtocol and ScaleRepositoryProtocol"

## Instructions

### 1 — Locate All Protocols in Parallel

For each protocol, search simultaneously:
```bash
rg -l "protocol <Name>" meApp -g '*.swift'
```

Read all protocol files in parallel.

### 1.5 — Check for Existing Mocks

For each protocol, check if a mock already exists:
```bash
rg -l "Mock<NameWithoutProtocolSuffix>" meAppTests -g '*.swift'
```

- If a mock exists and its methods match the current protocol: **skip** — do not overwrite.
- If a mock exists but is outdated (missing methods or mismatched signatures): **warn** the caller and skip — suggest running `/update-mock` instead.
- If no mock exists: proceed to generate.

### 2 — Apply Mock Pattern to Each

**Detect the pattern for each:**

**Pattern A — Service** (`*ServiceProtocol`):
- All async throwing methods use `Result<T, Error>` stubs
- Default: `.failure(UnexpectedCallError.methodCalled("<method>"))`
- `private(set) var <method>Calls = 0`
- `private(set) var last<Method><ArgName>: <ArgType>?` per argument

**Pattern B — Local Repository** (`*RepositoryProtocol`, SwiftData-backed):
- In-memory backing `var items: [ModelType] = []`
- Write methods use `var <method>Error: Error?` stubs
- Read methods return filtered slices of `items`
- Call counters + last-arg captures on write methods only

**Both patterns:**
- Class: `@MainActor final class Mock<Name>: <Name>`
- Groups: `// MARK: - Stubs`, `// MARK: - Call Tracking`, `// MARK: - <ProtocolName>`
- `import Foundation` + `@testable import meApp`
- `import Combine` only if protocol uses `@Published` or `Publisher`

### 3 — Determine Placement for Each

| Protocol location | Mock destination |
|-------------------|-----------------|
| `Domain/Services/` (used across features) | `meAppTests/Support/Mocks/Services/Mock<Name>.swift` |
| `Domain/Repositories/` (single feature) | `meAppTests/Features/<Feature>/Mocks/Mock<Name>.swift` |
| Unknown | `meAppTests/Features/<Feature>/Mocks/` matching closest feature |

### 4 — Write All Files

Write all mocks simultaneously (parallel writes).

### 5 — Return Summary

Report back to the caller:
```
Generated mocks:
- MockEntryService → meAppTests/Support/Mocks/Services/MockEntryService.swift
- MockEntryRepository → meAppTests/Features/Entry/Mocks/MockEntryRepository.swift
```

List any protocols that could not be located and why.
