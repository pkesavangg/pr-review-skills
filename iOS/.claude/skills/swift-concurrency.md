---
name: swift-concurrency
description: Apply the project's Swift concurrency rules when a task touches async flows, actor isolation, Task usage, @MainActor types, Sendable boundaries, or background work. Use when the user says "concurrency", "async issue", "actor bug", or when editing async services, repositories, or stores.
---

Handle a change involving Swift concurrency using the repo's established isolation patterns.

The concurrency-related task is: $ARGUMENTS

## Instructions

### 1 — Inspect The Concurrency Boundary

Read the affected code plus the nearest existing patterns in:
- `meApp/Data/Services/`
- `meApp/Data/Storage/DB/`
- `meApp/Core/Services/SwiftDataWorker.swift`
- any involved `Domain/Services/*Protocol.swift`

Search for the relevant mechanisms:
```bash
rg -n "@MainActor|Task\\s*\\{|Task\\.detached|actor\\b|Sendable|nonisolated|MainActor.run|withTaskGroup|withThrowingTaskGroup|await " meApp meAppTests -g '*.swift'
```

### 2 — Classify The Concurrency Problem

Determine which category applies:
- UI/store isolation on `@MainActor`
- service-to-repository async flow
- actor boundary crossing
- detached/background task execution
- in-flight task deduplication or cancellation
- Sendable/value-transfer safety
- async work touching SwiftData models
- **chart/graph rendering** — throttled `@State` updates, `DispatchWorkItem` debouncing, scroll-driven Tasks

### 3 — Apply The Repo Rules

Use the existing project patterns:
- Keep stores and UI state mutations on `@MainActor`
- Extract primitive/value data before crossing async or actor boundaries
- Do not pass SwiftData `@Model` objects across executors when a DTO/value type is safer
- Use `MainActor.run` only when a small read/write must happen back on the main actor
- Use `Task.detached` sparingly and only when captured state is safe and deliberate
- Preserve or add cancellation checks for long-running/background work
- Reuse the "active task" pattern when concurrent callers should await shared work instead of duplicating it

### 3b — Chart/Graph-Specific Patterns (apply when editing `BaseGraphView` or `BaseSectionViewModel`)

The Dashboard chart layer has its own established concurrency idioms. Match them exactly:

**Throttled `DispatchWorkItem` (cache updates during scroll):**
```swift
// Pattern from BaseGraphView.updateCachedChartDataThrottled()
private let throttleInterval: TimeInterval = 0.05

private func updateThrottled() {
    let now = Date()
    guard now.timeIntervalSince(lastUpdateTime) > throttleInterval else {
        scheduleDelayed()
        return
    }
    lastUpdateTime = now
    doUpdate()
}

private func scheduleDelayed() {
    workItem?.cancel()
    let item = DispatchWorkItem { self.doUpdate() }
    workItem = item
    DispatchQueue.main.asyncAfter(deadline: .now() + throttleInterval, execute: item)
}
```
Cancel `workItem` in `onDisappear` to prevent memory leaks.

**Short `Task.sleep` for animation timing** (not for real delays):
```swift
// Used to let SwiftUI commit a state change before the next frame
Task { @MainActor in
    try? await Task.sleep(nanoseconds: 5_000_000)  // 5ms — one-frame settle
    isInScrollEndTransition = false
}
```
Keep durations ≤ 100ms. Longer means a design problem, not a timing fix.

**`Task { @MainActor in }` for deferred `@State` mutation** (avoid "publishing during view update"):
```swift
// Correct — deferred to next run loop cycle
Task { @MainActor in enableYAxisAnimation = true }

// Wrong — mutates @State synchronously inside a view render
enableYAxisAnimation = true
```

**Cancellable period-change task** (avoid stale config after rapid tab switches):
```swift
@State private var periodChangeTask: Task<Void, Never>?

// On period change:
periodChangeTask?.cancel()
periodChangeTask = Task { @MainActor in
    try? await Task.sleep(nanoseconds: 50_000_000)
    guard !Task.isCancelled else { return }
    // ... configure active VM
}
```

### 4 — Risk Checklist

Before finishing, explicitly check:
- Is any non-Sendable or actor-bound state escaping its isolation?
- Is any `@MainActor` type being mutated off-main?
- Is `Task.detached` capturing `self`, a model object, or mutable shared state unsafely?
- Should the code pass a value type / notification wrapper / DTO instead of a live model?
- Should the task be cancellable or deduplicated?

### 5 — Testing And Follow-Up

Recommend focused verification:
- state transition tests for stores/services
- cancellation or repeated-call tests when concurrency coordination changed
- waiting/polling helpers instead of arbitrary sleeps

If the issue is mainly about SwiftData executor crossing, follow up with `/swiftdata`.
