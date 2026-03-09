Scaffold a new feature module with the standard folder structure and boilerplate Swift files.

The feature name is: $ARGUMENTS

## Instructions

### 1 — Validate Input

`$ARGUMENTS` must be a PascalCase feature name (e.g. `Onboarding`, `WeightHistory`, `GoalSetting`).
If it is not provided or is not PascalCase, ask the user for the feature name before continuing.

---

### 2 — Create Production Files

Create the following files under `meApp/Features/<FeatureName>/`:

**`Routes/<FeatureName>Route.swift`**
```swift
import Foundation

enum <FeatureName>Route: Routable {
    case root
}
```

**`Stores/<FeatureName>Store.swift`**
```swift
import Foundation
import Combine

@MainActor
final class <FeatureName>Store: ObservableObject {

    // MARK: - Published State

    @Published var isLoading = false
    @Published var errorMessage: String?

    // MARK: - Dependencies

    // @Injector var <featureName>Service: <FeatureName>ServiceProtocol

    // MARK: - Init

    init() {}

    // MARK: - Actions

}
```

**`Views/Screens/<FeatureName>Screen.swift`**
```swift
import SwiftUI

struct <FeatureName>Screen: View {

    @ObservedObject var store: <FeatureName>Store

    var body: some View {
        Text("<FeatureName>")
    }
}
```

**`Views/Components/`** — create as empty directory (add a `.gitkeep` placeholder if needed)

**`Strings/<FeatureName>Strings.swift`**
```swift
import Foundation

struct <FeatureName>Strings {
    static let title = "<FeatureName>"
}
```

---

### 3 — Create Test Folder

Create the empty directory:
```
meAppTests/Features/<FeatureName>/
```

No files needed — they will be added via `/gen-test-file` and `/gen-mock`.

---

### 4 — Report and Checklist

Confirm all files created, then print this checklist:

```
✅ Feature scaffold created: meApp/Features/<FeatureName>/

Manual next steps:
□ Add <FeatureName>Route to the parent feature or app-level router
□ Wire <FeatureName>Screen into the calling view/store navigation flow
□ Uncomment and wire @Injector dependencies in <FeatureName>Store once services exist
□ Register <FeatureName>Store in DependencyContainer if it needs to be resolved via @Injector
□ Register any new services in ServiceRegistry (essential vs. session-scoped)
□ Run: /gen-test-file meApp/Features/<FeatureName>/Stores/<FeatureName>Store.swift
□ Run: /gen-mock for each protocol dependency once defined
```
