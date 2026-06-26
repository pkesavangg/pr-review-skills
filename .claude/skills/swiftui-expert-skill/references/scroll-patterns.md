# ScrollView patterns (SwiftUI)

## Modern scroll APIs (iOS 17+)
| API | Use |
|-----|-----|
| `.scrollPosition(id:)` | Read/programmatically set the top-most visible item |
| `.scrollTargetLayout()` + `.scrollTargetBehavior(.viewAligned)` | Snap/paged scrolling |
| `.scrollTargetBehavior(.paging)` | Full-page paging |
| `.scrollClipDisabled()` | Let content draw outside the scroll bounds (e.g. shadows, charts) |
| `.scrollIndicators(.hidden)` | Hide indicators |
| `.scrollBounceBehavior(.basedOnSize)` | No bounce when content fits |
| `ScrollViewReader { proxy in … proxy.scrollTo(id, anchor:) }` | Imperative scroll (iOS 14+) |

## Lists
- `.listRowSeparator(.hidden)`, `.listRowInsets(...)`, `.listRowBackground(...)`.
- `.refreshable { await reload() }` for pull-to-refresh (iOS 15+).
- Large/lazy content: `LazyVStack`/`LazyHStack` inside `ScrollView`, or use `List` (cell reuse). Prefer `List` for long homogeneous data.

## Charts scrolling (Swift Charts, iOS 17+)
- `.chartScrollableAxes(.horizontal)`, `.chartScrollPosition(x:)`, `.chartXVisibleDomain(length:)`.
- The dashboard graph layer has its own paging/scroll machinery — see the `graph` skill before changing chart scrolling.

## Project rules / pitfalls
- **No nested vertical `ScrollView`s** — vertical scroll nesting breaks gesture handling (let the parent scroll). This is a hard project rule (`iOS/CLAUDE.md`).
- Mixing horizontal `ScrollView` inside a vertical one is fine (orthogonal axes).
- Avoid `GeometryReader` inside scroll content for sizing — it forces layout passes; prefer `.containerRelativeFrame` (iOS 17+) or fixed frames.
- Gate iOS 17+ scroll APIs with `if #available`; provide a `ScrollViewReader` fallback where needed.
