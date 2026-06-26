# Sheets & navigation patterns (SwiftUI)

## Sheets
```swift
.sheet(item: $selected) { item in DetailView(item: item) }      // prefer item: over isPresented for data-carrying sheets
.sheet(isPresented: $showSheet) { SheetView() }
```
- **Detents (iOS 16+):** `.presentationDetents([.medium, .large])`, `.presentationDragIndicator(.visible)`.
- **iOS 16.4+:** `.presentationBackground`, `.presentationContentInteraction`, `.presentationCornerRadius`.
- One sheet modifier per view level — stacking multiple `.sheet` on the same view is unreliable; drive from an enum `item:` instead.
- This project routes alerts/toasts/loaders/modals through its notification layer — see `notification-guide` before hand-rolling overlays.

## NavigationStack (iOS 16+)
```swift
NavigationStack(path: $path) {
    Root()
        .navigationDestination(for: Item.self) { ItemView(item: $0) }
}
NavigationLink("Open", value: item)   // pushes by value; type-safe
```
- Programmatic: mutate `path` (`path.append`, `path.removeLast`). Use a typed `NavigationPath` or `[Route]`.
- This app uses a **custom stack-based `Router<Route>` + `RoutingView`** (per-feature `Route` enums). Match that pattern — see `wire-navigation`; don't introduce raw `NavigationStack` into a feature that already uses the Router.

## NavigationSplitView (iPad / multi-column, iOS 16+)
```swift
NavigationSplitView { Sidebar() } detail: { Detail() }
```
Use `.navigationSplitViewColumnWidth`, `columnVisibility` binding for control.

## Inspector (iOS 17+)
```swift
.inspector(isPresented: $showInspector) { InspectorContent() }
```
Trailing/contextual panel; pairs with `.presentationDetents` on compact widths.

## Pitfalls
- No nested vertical `ScrollView`s (project rule — breaks gestures; see `scroll-patterns`).
- Don't mix `NavigationView` and `NavigationStack` in one hierarchy.
- Sheet content gets its own environment — re-inject anything it needs.
