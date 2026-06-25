# Latest SwiftUI APIs — deprecated → modern

Read first for every task. Prefer the modern API; flag and replace the deprecated one. Targets iOS 15+ through current. Always gate newer APIs with `if #available` (or raise the deployment target) — check the project's minimum iOS version first.

## Navigation
| Deprecated | Modern | Notes |
|------------|--------|-------|
| `NavigationView` | `NavigationStack` (stack) / `NavigationSplitView` (multi-column) | iOS 16+. `NavigationStack(path:)` for programmatic, type-safe paths. |
| `NavigationLink(destination:isActive:)` | `NavigationLink(value:)` + `.navigationDestination(for:)` | iOS 16+. |
| `.navigationBarTitle` / `.navigationBarItems` | `.navigationTitle` + `.toolbar { ToolbarItem(...) }` | |

## State & data flow
| Deprecated / older | Modern | Notes |
|--------------------|--------|-------|
| `ObservableObject` + `@Published` + `@StateObject`/`@ObservedObject` | `@Observable` macro + `@State`/`@Bindable` | iOS 17+. (This project uses `@MainActor` ObservableObject stores by convention — match existing code unless told to migrate.) |
| `@EnvironmentObject` | `@Environment(MyType.self)` (with `@Observable`) | iOS 17+. |

## onChange / events
| Deprecated | Modern | Notes |
|------------|--------|-------|
| `onChange(of:perform:)` (single-param closure) | `onChange(of:) { oldValue, newValue in }` or `onChange(of:) { }` | iOS 17+ changed the signature; the old one is deprecated. |
| `.onChange` for first-run work | `.task { }` | runs on appear, auto-cancels. |

## Presentation & layout
| Deprecated / older | Modern | Notes |
|--------------------|--------|-------|
| Manual `Spacer`/`GeometryReader` sizing hacks | `Grid` / `ViewThatFits` / `.containerRelativeFrame` | iOS 16/17+. |
| `.sheet` with manual detents | `.presentationDetents([.medium, .large])` | iOS 16+. See `sheet-navigation-patterns.md`. |
| `alert(isPresented:)` | `alert(_:isPresented:presenting:actions:message:)` | iOS 15+. |
| `.foregroundColor` | `.foregroundStyle` | iOS 17+ (supports gradients/hierarchical). |

## Lists & scrolling
See `scroll-patterns.md`. Key: `.scrollTargetBehavior`, `.scrollPosition`, `.scrollClipDisabled` (iOS 17+); `.listRowSeparator`, `.refreshable`.

## Project rule
Never hardcode colors/fonts/spacing — use `Theme/` tokens (`theme-guide`). Newer APIs must still respect the snapshot/`@MainActor` conventions in `iOS/CLAUDE.md`.
