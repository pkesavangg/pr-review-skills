---
name: wire-navigation
description: Wire a new screen into the feature's Route enum and RoutingView. Triggers: "push to X", "add route for Y", "wire this screen", "navigate to X", or after /feature-slice.
---

Wire a new screen into the feature's navigation stack.

The screen or flow to wire is: $ARGUMENTS

## Instructions

### 1 — Find the Feature Router

Locate the feature's Route enum and RoutingView:

```bash
rg -rn "enum.*Route.*Routable" meApp/Features -g '*.swift'
rg -rn "RoutingView\|Router<" meApp/Features -g '*.swift' | head -10
```

Read both the Route enum file and the RoutingView for this feature.

---

### 2 — Add the Route Case

Add a new case to the `*Route` enum. Match the exact style of adjacent existing cases:

```swift
enum <FeatureName>Route: Routable {
    case <newScreenName>               // no args
    case <newScreenName>(<ArgType>)    // with args
}
```

---

### 3 — Update the RoutingView Switch

Add the corresponding case in the RoutingView switch. Read an adjacent existing case to match the exact pattern used in this feature:

```swift
case .<newScreenName>:
    <NewScreen>Screen(store: router.store)

case .<newScreenName>(let arg):
    <NewScreen>Screen(item: arg)
```

---

### 4 — Add Navigation Trigger to the Calling Store

In the store that should navigate to the new screen, add a method:

```swift
func navigateTo<NewScreen>() {
    router.push(.<newScreenName>)
}

// Or with data:
func navigateTo<NewScreen>(_ item: <ArgType>) {
    router.push(.<newScreenName>(item))
}
```

---

### 5 — Build Verify

```bash
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'generic/platform=iOS' \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

Fix all build errors before reporting.

---

### 6 — Report

```
Route added: <FeatureName>Route.<newCaseName>
RoutingView updated: <file path>
Navigation trigger added in: <StoreName>.<methodName>()
```

If the new screen requires a new service dependency, follow up with `/wire-service`.
If the screen has interactive elements that need UI tests, follow up with `/gen-ui-test-file`.
