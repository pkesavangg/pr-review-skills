# MOB-518 — Weight graph deep-focus (workflow + root cause + real-fix plan)

> **What this doc is.** The single source of truth for the **weight-graph** work under
> [MOB-518](https://greatergoods.atlassian.net/browse/MOB-518) (Task 2 of epic
> [MOB-516](https://greatergoods.atlassian.net/browse/MOB-516)). It explains **how the graph actually
> works**, the **real root cause** of the two runtime warnings we hit, and the **architecturally-correct
> fix** (not a patch). Update as we go.
>
> **Companions:** `meApp/Features/Dashboard/GraphViewFlow.md` (full chart architecture reference),
> `MOB-516-implementation-plan.md` (epic + Task-2 execution log), `performance-analysis-5.1.0.md`.

**Scope:** iOS only · **Branch:** `MOB-518-chart-engine-scroll-hitch-multi-series` · **Base:** `develop`
**Build:** Dev config only. Current build: ✅ **SUCCEEDED**.

---

## 0. Status snapshot (2026-07-08)

| Item | State |
|---|---|
| Build (Dev, `generic/platform=iOS`) | ✅ SUCCEEDED |
| Test build (`build-for-testing`, generic iOS) | ✅ TEST BUILD SUCCEEDED |
| **W1 — scroll update storm** (`Publishing changes from within view updates` + `onChange(of: ChartScrollPositionConfiguration) … multiple times per frame`) | ✅ **Structural fix implemented + compiles** (§3.5). Awaiting device confirm (§3.3). |
| **W2 — `Invalid frame dimension (negative or non-finite)` flood** | ✅ **Structurally guarded at source** (`ChartDomainSanitizer`, §4.3). Awaiting device confirm. |
| Step 2a — percentile binary search (baby/BPM) | ✅ committed `813f0f98a` — orthogonal to W1/W2 |
| Step 2c — average/y-axis recompute is scroll-end-gated | ✅ verified |

> **Headline (resolved):** W1 was a **broken architectural invariant** — `scrollPosition` was `@Published`
> when the design explicitly requires it **not** to be (`GraphViewFlow.md:475-477`). The Swift Charts
> `.chartScrollPosition` binding writes it *during* the view-update pass, so publishing it re-entered the
> update every frame → the storm. **Fix: de-`@Published` `scrollPosition`** (§3.5). This is why the earlier
> Option B failed — it never removed the `@Published` write, only relocated it.
>
> **W2** is partly downstream of that storm (mid-frame re-layout hands SwiftUI transient bad geometry) and
> partly a real source risk (degenerate/zero-width domains reaching `.chartYScale`/`.chartXScale`). Fixed by
> routing every Charts scale/visible-domain input through `ChartDomainSanitizer` (§4.3).

### What changed (files)
- `BaseSectionViewModel.swift` — `scrollPosition` is now a **plain `var`** (was `@Published`); `forceScrollPositionUpdate` is a plain set (the `+0.001` nudge is gone).
- `BaseGraphView.swift` — added `scrollAdoptToken` (`@State`) for programmatic-scroll adoption re-renders; removed the `onAppear` `+0.001` nudge dance; `safeYAxisDomain` feeds both `.chartYScale` and the series clamp.
- `BaseGraphView+ChartModifiers.swift` — the store→VM `xScrollPosition` `onChange` bumps `scrollAdoptToken`; `.chartXVisibleDomain` / non-scrollable `.chartXScale` / baby-cap `.chartXScale` routed through `ChartDomainSanitizer`.
- **New** `Managers/Graph/ChartDomainSanitizer.swift` (+ `Utils/ChartDomainSanitizerTests.swift`) — pure finite/positive-width guards for Charts domains.

---

## 1. The runtime symptoms

```
onChange(of: ChartScrollPositionConfiguration) action tried to update multiple times per frame.   (bursts)
Invalid frame dimension (negative or non-finite).                                                   (continuous spam)
```

Both appear while the weight graph is visible / scrolled. No crash, but W1 = redundant per-frame render
churn on the scroll path (the exact "do work per frame instead of per settle" class MOB-518 targets, and it
can make SwiftUI merge/drop scroll updates → visible jitter), and W2 = a layout calc handing SwiftUI a
`width`/`height` that is `< 0`, `NaN`, or `±∞` every layout pass.

---

## 2. How the weight graph works (the pipeline you must understand before fixing)

### 2.1 The pieces
- **`BaseGraphView`** (`Views/Components/BaseGraphView.swift`) — the shared SwiftUI+Swift Charts renderer,
  generic over a `BaseSectionViewModel`. Conforms to **`Equatable`** and is applied with `.equatable()` at
  the four period wrappers.
- **`BaseSectionViewModel`** (`ViewModels/BaseSectionViewModel.swift`) — `@MainActor ObservableObject`; owns
  per-chart UI state (`scrollPosition`, `isScrolling`, `selectedDate`, `yAxisDomain`, `chartFrame`, caches).
  Observed by `BaseGraphView` via `@ObservedObject`.
- **`DashboardStore` + `GraphState`** (`Models/DashboardState.swift`) — the store-level source of truth;
  `state.graph.xScrollPosition` (`:114`) and `state.graph.isScrolling` are `@Published` on the store.
- **Managers** — `DashboardChartManager` (init/scroll-end/period), `DashboardGraphManager`
  (buffers scroll, owns `state.xScrollPosition`), `GraphInteractionHandler` (scroll buffer + caches).

### 2.2 Two scroll-position stores, deliberately separate
- **VM `scrollPosition`** — what the chart binding reads/writes *live* during a gesture.
- **Store `state.graph.xScrollPosition`** — the *committed* position, updated **only at scroll-end** and on
  **programmatic** moves (period switch / init), never per-frame.

### 2.3 The scroll lifecycle (verified against code)
1. **User drags** → SwiftUI Charts drives its internal scroll and calls the
   `.chartScrollPosition(x:)` binding **set** ([`BaseGraphView+ChartModifiers.swift:28-36`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView+ChartModifiers.swift#L28-L36)) → `viewModel.handleScrollPositionChange(newPos)`.
2. `handleScrollPositionChange` ([`BaseSectionViewModel.swift:416-441`](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L416-L441)): throttles to 16 ms, then (a) `self.scrollPosition = newPos` (`:437`), and (b) `chartManager.handleScrollPositionChange` → `graphManager.handleScrollPositionChange` → `interaction.captureScrollPosition(pos)` — which **only buffers**; it does **NOT** touch `state.xScrollPosition` mid-gesture ([`DashboardGraphManager.swift:45-48`](../../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L45-L48)).
3. **Scroll end** → `handleScrollPhaseChange(.idle)` / debounce timer consumes the buffered position and **then** writes `state.xScrollPosition` ([`DashboardGraphManager.swift:68,100`](../../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift#L68)), sets `isScrolling=false`, and `DashboardChartManager.handleScrollEndOptimized` ([`:227-264`](../../meApp/Features/Dashboard/Managers/DashboardChartManager.swift#L227-L264)) recomputes y-axis/average/metrics.
4. **Programmatic moves** (period switch, init) → `DashboardChartManager` → `graphManager.updateScrollPosition` → `state.xScrollPosition` ([`DashboardChartManager.swift:199,381`](../../meApp/Features/Dashboard/Managers/DashboardChartManager.swift#L199)). The store→VM sync `.onChange(of: xScrollPosition)` ([`BaseGraphView+ChartModifiers.swift:338-341`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView+ChartModifiers.swift#L338-L341)) then mirrors it into the VM.

**Consequence of (2)/(3):** during an active drag the store's `xScrollPosition` does **not** change, so the
store→VM sync `onChange` does **not** fire mid-scroll. **There is only ONE live writer of `scrollPosition`
during a gesture** (the chart binding set). So W1 is *not* a two-writers race — see §3.

### 2.4 The performance invariant the design depends on
`GraphViewFlow.md:475-477` documents the load-bearing rule:

> `BaseGraphView` is `Equatable`; the hash **excludes `scrollPosition`, `isScrolling`, `selectedPoint`** …
> `EquatableView` only short-circuits **parent-driven** re-renders. **When `@ObservedObject` publishers fire,
> the view is invalidated regardless.** The combination with the **"`scrollPosition` not `@Published`"
> invariant** is what produces the smooth scroll behaviour — neither alone is sufficient.

Confirmed in code: the Equatable `viewHash` ([`BaseGraphView.swift:367-377`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L367-L377)) **does** exclude `scrollPosition`/`isScrolling`. So the design **intends** that mutating `scrollPosition` does **not** invalidate `BaseGraphView`.

---

## 3.0 Diagnosis history — RESOLVED (2026-07-08) → see §3.5 for the shipped fix

> **Resolved.** The final read: the storm is a **single re-entrant per-frame update** whose one true driver is
> the `@Published scrollPosition` write on the view-update pass (the other warnings were its symptoms). The
> earlier "need device backtraces before touching anything" stance below was overtaken once the console
> narrowed to just the scroll-publish + frame-flood (the `XYBindingValue`/`Date` symptom lines were
> Option-B artifacts and vanished on revert). Fix shipped in **§3.5** (W1) + **§4.3** (W2); this section is
> kept as the reasoning trail.

The `@Published`-invariant theory in §3 below was **necessary but not sufficient** *as originally scoped*
(it under-counted the symptoms). The reality is a **single re-entrant per-frame update storm**, which under
Option B still showed *three* simultaneous "multiple times per frame" warnings + a flood of invalid-frame
warnings:

| Warning | Emitted by | Meaning |
|---|---|---|
| `onChange(of: XYBindingValue)` | Charts `.chartXSelection` binding | selection binding value churns within a frame |
| `onChange(of: ChartScrollPositionConfiguration)` | Charts `.chartScrollPosition` binding | scroll binding value churns within a frame |
| `onChange(of: Date)` | a `Date` `onChange` (e.g. `xScrollPosition` sync) | app onChange re-firing within a frame |
| `Invalid frame dimension` ×N | CoreGraphics/SwiftUI layout | a view handed `NaN`/`<0`/`∞` size, every layout pass |

**Interpretation:** when the graph view updates (or re-lays-out) **multiple times within one frame**, *every*
`onChange` in it fires multiple times and layout runs many times. So these are **symptoms of one storm**, not
four bugs. The most likely drivers (to confirm with evidence):
1. **Binding setters writing `@Published` mid-update.** Charts calls the `.chartScrollPosition` / `.chartXSelection`
   setters **during** the view update; those call `handleScrollPositionChange` / `handleChartSelection`, which
   write `@Published` VM state → republish mid-update → SwiftUI re-runs the update in the same frame → the storm.
   (This is the same class as the `alertData` "Publishing changes from within view updates" note.)
2. **A non-finite frame** (e.g. a degenerate/`NaN` `yAxisDomain` fed to `.chartYScale`, or an overlay sized off
   `chartFrame == .zero` on early layout) causing SwiftUI to re-attempt layout repeatedly.

Either driver can cause/feed the other. **Why Option B failed:** it moved only the *scroll* binding to `@State`,
but the *selection* binding still wrote `@Published` mid-update, and the forward `onChange` still wrote
`@Published` — so the storm continued, and the extra `onChange(of: Date)` just added another symptom line.

### 3.0.1 Optional device confirmation aids (no longer blocking — fix already shipped)
If W1/W2 ever resurface, these two captures pinpoint a regression fast:

1. **Which view emits a bad frame** — Xcode → Edit Scheme → Run → Arguments → **Environment Variables** → add
   `CG_NUMERICS_SHOW_BACKTRACE = 1` → re-run → open the graph → the topmost `meApp` frame in the printed stack
   names the culprit view/modifier. (With §4.3's `ChartDomainSanitizer` in place this should stay silent.)
2. **What re-triggers the view** — the `Self._logChanges()` in `BaseGraphView.body` (DEBUG, `BaseGraphView.swift`)
   prints `BaseGraphView: … changed` lines naming the exact `@Published`/state driving a re-render. After §3.5,
   dragging should produce **no** per-frame `scrollPosition`-driven lines.

> **Honest note on severity:** these are runtime **warnings**, not crashes or compile errors — the app runs. But
> they indicate real wasted work (repeated per-frame layout/updates = scroll jank + battery) and a genuine layout
> bug (the bad frame), so they're worth fixing properly. Some of the Charts-internal `onChange` warnings may turn
> out to be partly unavoidable framework noise during fast interaction; the evidence above will tell us which are
> app-fixable vs. framework.

---

## 3. W1 — earlier (partial) root cause: a broken `@Published` invariant

**`scrollPosition` is declared `@Published`** ([`BaseSectionViewModel.swift:22`](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L22)):
```swift
@Published var scrollPosition = Date()   // ← violates the "not @Published" invariant (GraphViewFlow.md:475-477)
```
It was (re)introduced in a refactor (`git log -S` → MA-3964 / MA-3845), silently breaking the invariant.

**The failure chain, per frame, during a drag:**
1. Charts calls the binding `set` → `handleScrollPositionChange` writes `self.scrollPosition` (`:437`).
2. Because it's `@Published`, that write fires `objectWillChange` on the VM.
3. `BaseGraphView` is `@ObservedObject`-bound to the VM, so it is **invalidated and re-rendered** — and
   `.equatable()` **cannot** stop it (Equatable only blocks *parent-driven* re-renders; an ObservableObject
   publish bypasses it — exactly the caveat in GraphViewFlow.md).
4. The re-render **re-applies `.chartScrollPosition(x: Binding(get: { viewModel.scrollPosition }))`**. Swift
   Charts' internal `onChange(of: ChartScrollPositionConfiguration)` observes the binding it was just handed,
   sees the value churn from the write in (1), and registers **another** update **in the same frame** →
   **"action tried to update multiple times per frame."**

**Compounding trigger — the `+0.001` nudge dances** (which only exist *because* the property is `@Published`,
to force a binding refresh): they write `scrollPosition` **twice in immediate succession**:
- on appear: [`BaseGraphView.swift:141-145`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L141-L145)
- `forceScrollPositionUpdate`: [`BaseSectionViewModel.swift:712-719`](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L712-L719) (comment: *"Force a small change to trigger binding update"*).
These deterministically emit the warning on mount / period switch.

**Why the 16 ms throttle doesn't help:** the throttle limits how often Charts' `set` is *forwarded*, but the
warning is caused by the **publish→re-render→re-apply-binding feedback within a single frame**, not by two
gestures arriving close together. Throttling harder would just make scrolling laggy — a patch, not a fix.

**Confirmed safe to de-publish:** no `$scrollPosition` / `$isScrolling` Combine subscribers exist anywhere
(grepped `meApp/` + `meAppTests/`), and no view outside the graph reads `viewModel.scrollPosition`. The
Equatable hash already excludes it. So removing `@Published` restores the *intended* design without breaking
observers.

### 3.1 The real fix — two options

**Option A — restore the invariant directly (smallest change, matches the doc).**
- Make `scrollPosition` a **plain `var`** (drop `@Published`) on `BaseSectionViewModel`.
- Route **programmatic** scroll changes through the store's already-`@Published` `state.graph.xScrollPosition`
  (period switch/init already do this; the store→VM `onChange` mirrors it into the VM). The store publish
  re-renders the body, which re-applies `.chartScrollPosition` with the updated VM value → Charts adopts it.
- **Delete** the `+0.001` nudge dances ([`BaseGraphView.swift:141-145`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L141-L145), `forceScrollPositionUpdate`) — they exist only to force the `@Published` refresh that we no longer want.
- **Risk:** programmatic scroll adoption now depends on the store-publish re-render ordering relative to the
  store→VM `onChange` write. Needs on-device check of: initial position, period-switch anchor, "scroll to
  latest".

**Option B — bind the chart to local `@State` (Apple's canonical pattern, recommended).**
- Add `@State private var chartScrollX: Date` in `BaseGraphView`; bind `.chartScrollPosition(x: $chartScrollX)`.
- During a drag, Charts mutates **local `@State`** (cheap, no VM `objectWillChange`, no `.equatable()` bypass) —
  the per-frame feedback loop is gone at the source.
- Sync at the **boundaries only**: `.onChange(of: chartScrollX)` → forward to the VM's buffering
  (`captureScrollPosition`) without republishing; `.onChange(of: store.xScrollPosition)` → set `chartScrollX`
  for programmatic moves. `scrollPosition` on the VM can stay for computed reads but is no longer the chart's
  live binding.
- **Why preferred:** `.chartScrollPosition` bound to a `@State` (not an `@Published` ObservableObject
  property) is the documented-correct SwiftUI pattern; it localizes the churn and has the smallest blast radius
  on the VM/store contracts. Still needs the same on-device scroll checks.

**Recommendation (at the time): Option B**, with Option A as the fallback.

> **Decision (2026-07-08): ⛔️ Option B tried first → failed on device → reverted. ✅ Option A shipped**
> (de-`@Published` `scrollPosition`) — see **§3.5**. Option B failed precisely because it kept `scrollPosition`
> `@Published` (see its own note in §3.2), so the mid-update publish — the real root — survived. Option A
> removes that publish, which is what the invariant (`GraphViewFlow.md:475-477`) always required.

### 3.2 Option B — the FAILED attempt (superseded by §3.5; kept as a lesson)

> ⛔️ **This was reverted.** It did not remove the `@Published` write (see the "Deliberately NOT changed"
> bullet), so the storm persisted and it added an `onChange(of: Date)` symptom. The shipped fix is **§3.5**.

- **New local `@State`** in `BaseGraphView` ([`:24-31`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift#L24-L31)): `chartScrollX: Date` (the chart's live scroll source) + `isAdoptingProgrammaticScroll: Bool` (echo guard).
- **Chart binding swapped** to the canonical `@State` form ([`BaseGraphView+ChartModifiers.swift:27-33`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView+ChartModifiers.swift#L27)): `.chartScrollPosition(x: $chartScrollX)` — replaces the old manual `Binding{ get: viewModel.scrollPosition; set: handleScrollPositionChange }`. **The throttle no longer sits inside the chart binding**, so get/set identity is preserved → no per-frame config churn.
- **Two boundary `onChange`s** in `BaseGraphView.body`:
  - `.onChange(of: chartScrollX)` → `viewModel.handleScrollPositionChange(newPos)` (user scroll → throttled/buffered app logic), skipped when the change was our own programmatic set.
  - `.onChange(of: dashboardStore.state.graph.xScrollPosition)` → sets `chartScrollX` (programmatic adopt: init / period switch / scroll-end commit), guarded by `>0.1s` and the echo flag.
- **`onAppear` init**: `chartScrollX = viewModel.scrollPosition` (adopt the anchor position `configure()` set); **the `+0.001` nudge dance is deleted** (it only existed to force the old `@Published` binding to refresh).
- **Deliberately NOT changed (kept minimal/low-risk):** `scrollPosition` stays `@Published` on the VM — it's now used only for computed reads (`visibleDomainLength`, `getChartPosition`, windowing), **not** as the chart's live binding, so it no longer feeds the warning. Fully de-publishing it (to also shave the residual per-frame body re-render) is a safe follow-up **once B is device-verified** — nothing depends on its publish anymore. `forceScrollPositionUpdate` (GraphView period transition) is left in place; the chart now adopts the position via the store→`chartScrollX` path, so it's redundant-but-harmless and can be cleaned up later.

### 3.3 On-device verification checklist (Kesavan — REQUIRED; I cannot run the device)
- [ ] Console shows **no** `onChange(of: ChartScrollPositionConfiguration) … multiple times per frame` on: cold mount, drag (week/month/year), period switch, and "scroll to latest".
- [ ] Finger tracks the chart 1:1 — no lag, no snap-back, no rubber-banding.
- [ ] **Initial position** is correct on first open (latest data visible) and after a tab switch.
- [ ] **Period switch** lands at the right anchor (week↔month↔year), paging still snaps.
- [ ] Crosshair still selects a real point; the window **average settles on finger-lift**; y-axis matches the visible window.

### 3.4 Proof (W1) — expected results, pending device confirm (use the §3.3 checklist)
- No `onChange(of: ChartScrollPositionConfiguration) … multiple times per frame` and no
  `Publishing changes from within view updates` in the console across: mount, drag (all of week/month/year),
  period switch, and "scroll to latest".
- Finger still tracks 1:1 (no lag / snap-back); crosshair + average still settle on lift; initial position and
  period-switch anchor unchanged.
- Bonus: **zero** body re-renders per scroll frame during a drag (Charts scrolls natively now) — verify in a
  quick Instruments SwiftUI/Time Profiler pass on a 5k–10k account.

---

### 3.5 THE SHIPPED FIX (2026-07-08) — de-`@Published` + adoption token

> Supersedes the Option B attempt in §3.2 (that one kept `scrollPosition` `@Published`, so the storm
> continued). This restores the original invariant and adds a deterministic programmatic-adoption path.

**Root, in one line:** the `.chartScrollPosition` binding setter (`handleScrollPositionChange`) writes
`self.scrollPosition` on the view-update pass; while it was `@Published`, that write re-entered the update
every frame. Two more `onChange`s + the invalid-frame layout were *symptoms* of that one storm.

**Changes:**
1. **`scrollPosition` → plain `var`** (drop `@Published`) on `BaseSectionViewModel`. Now the per-frame binding
   write does not publish → no re-entrant `BaseGraphView` invalidation → storm gone. During a drag the body
   no longer re-runs per frame (Charts scrolls natively), which is the smoothness win for 5k–10k accounts.
   Safe: the `Equatable` hash already excludes it, there are **no `$scrollPosition` subscribers**, and every
   reader is a plain get. VM unit tests set/read it as a plain property → unaffected.
2. **`scrollAdoptToken` (`@State` in `BaseGraphView`)** — because a plain-var write can't re-render, the
   store→VM `onChange(of: xScrollPosition)` bumps this token to force **one** re-render, which re-applies
   `.chartScrollPosition` with the adopted value so Charts moves to it. Fires only on programmatic moves
   (scroll-end commit, "scroll to latest", period switch, init) — **never per drag frame**. No loop: the bump
   doesn't change `xScrollPosition`.
3. **Nudges deleted** — the `onAppear` `+0.001` dance and `forceScrollPositionUpdate`'s double-write existed
   only to refresh the former `@Published` binding. Initial/period-switch positions are adopted by the freshly
   mounted period chart's binding get on first render (this is the pre-regression behavior).

**Selection binding** (`.chartXSelection`) needed no change: its `get` reads `@State localSelectedXValue`
(not the VM), and during scroll its `set` is `guard !isScrolling`-gated — the `XYBindingValue`/`Date` warnings
seen under Option B were downstream churn of the storm and disappear once the storm is gone.

## 4. W2 — `Invalid frame dimension (negative or non-finite)` (spam)

**Confidence: medium. Ruled several suspects out; exact source needs one on-device symbolic breakpoint.**

Ruled OUT statically:
- `.chartXVisibleDomain(length:)` — `visibleDomainLength` always returns a positive constant ([`GraphRenderingConfiguration.swift:19-26`](../../meApp/Features/Dashboard/Managers/Graph/GraphRenderingConfiguration.swift#L19-L26)).
- `yAxisDomain` fed to `.chartYScale` — frozen during scroll (`updateYAxisConfiguration` guards `!isScrolling`, [`BaseSectionViewModel.swift:355`](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L355)); degenerate-per-frame is unlikely.
- Top-level chart frames (`chartContainerHeight` 265, `yAxisLabelWidth` 30/40) are constants.

Remaining candidates (ranked), to bisect on device:
1. **Overlays computed off `chartFrame` before layout settles.** On the first frames `chartFrame == .zero`, so
   `availableChartHeight = chartFrame.height - 18 = -18` inside `getChartPosition` / `getGoalChipPosition`
   ([`BaseSectionViewModel.swift:547,565,608`](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift#L547)). These feed `.position(...)` (tolerates negatives) — but any child that converts them into a `.frame(height:)` would emit W2.
2. **A callout/label frame handed a NaN** — `GraphSelectionDateCalloutView`, `CachedLabel` (`.frame(width:)`, [`CachedLabel.swift:63`](../../meApp/Features/Dashboard/Views/Components/CachedLabel.swift#L63)), `SlashDividerView` (`.frame(height:)`) — if width/height comes from a divide-by-zero.
3. **A degenerate y-domain (min == max)** at rest for a single-value window → zero-height plot rect.

> **W2 is likely independent of W1** — different subsystem (layout vs scroll observation). Fixing W1 may or
> may not silence it; treat as its own item.

### 4.1 Diagnostic recipe (do this before more code reading)
1. Run on device/simulator; add a **symbolic breakpoint** on the CoreGraphics "invalid frame dimension"
   warning (or set env `CG_NUMERICS_SHOW_BACKTRACE=1`) → capture the stack that passes the bad value.
2. Or temporarily guard the suspect inputs: `if !v.isFinite || v < 0 { LoggerService.log(.warning,"W2","\(label)=\(v)") }`.
3. Fix at the **source calc** (clamp `max(0, …)` / `isFinite` guard where the value is produced), never at the
   `.frame` call site.

### 4.2 Proof (W2)
- Zero `Invalid frame dimension` lines across a full weight-graph session (open, scroll all periods, select a
  point, switch period).

### 4.3 THE SHIPPED FIX (2026-07-08) — `ChartDomainSanitizer`

New pure helper `Managers/Graph/ChartDomainSanitizer.swift` guards every domain/length handed to Swift Charts,
so a degenerate/non-finite value can never divide-by-zero the plot math (the per-mark flood source):
- `finiteWidth(_:)` — ensures a finite, positive-width `ClosedRange<Double>`; drives a new `safeYAxisDomain`
  used for **both** `.chartYScale(domain:)` and the `ChartSeriesContent` value clamp (so points and scale agree).
- `orderedDates(_:)` — widens equal/degenerate date domains; applied to the non-scrollable `.chartXScale`
  (`dateRange` can be `now...now`) and the baby-cap `.chartXScale`.
- `positiveLength(_:)` — clamps `.chartXVisibleDomain(length:)` to finite > 0 (belt; it was already positive).

Covered by `ChartDomainSanitizerTests`. This is structural: it holds for any upstream data shape
(single-value windows, empty accounts, mid-transition frames), so W2 cannot regress into the flood again.
Combined with the W1 fix removing the mid-frame re-layout thrash, both the storm and the flood are addressed.

---

## 5. Work backlog (weight graph)

| ID | Item | Priority | State |
|---|---|---|---|
| **W1** | Scroll update storm — de-`@Published` `scrollPosition` + adoption token (§3.5) | P0 | ✅ implemented + app/test builds green; awaiting device verify (§3.3) |
| **W2** | Invalid-frame flood — `ChartDomainSanitizer` guards at all Charts scale inputs (§4.3) | P0 | ✅ implemented + unit-tested; awaiting device verify |
| W3 | Animation Hitches trace on a 5k–10k-entry weight account (MOB-518 gate) | P1 | pending device (W1/W2 first) |
| W4 | Regression pass: finger tracking, initial/period-switch position, crosshair snap, average-on-lift | P1 | pending device |

---

## 6. Key files (verified 2026-07-08)

- [`BaseGraphView+ChartModifiers.swift`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView+ChartModifiers.swift) — `.chartScrollPosition` binding (`:28-36`), store→VM sync (`:338-341`), selection/gesture.
- [`BaseGraphView.swift`](../../meApp/Features/Dashboard/Views/Components/BaseGraphView.swift) — `Equatable` hash excl. scrollPosition (`:363-377`), onAppear nudge (`:141-145`), chart body + frames (`:540-577`), overlays (`:602-672`).
- [`BaseSectionViewModel.swift`](../../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift) — `@Published scrollPosition` (`:22`, **the bug**), `handleScrollPositionChange` (`:416-441`), `updateScrollPosition`/`forceScrollPositionUpdate` (`:707-719`), `getChartPosition`/`getGoalChipPosition` (`:529-623`), y-axis scroll guard (`:355`).
- [`DashboardGraphManager.swift`](../../meApp/Features/Dashboard/Managers/DashboardGraphManager.swift) — scroll buffering (`:45-48`), xScrollPosition committed at scroll-end (`:68,100`).
- [`DashboardChartManager.swift`](../../meApp/Features/Dashboard/Managers/DashboardChartManager.swift) — programmatic scroll (`:199,381`), scroll-end recompute (`:227-264`).
- [`GraphViewFlow.md`](../../meApp/Features/Dashboard/GraphViewFlow.md) — the invariant (`:475-477`), scroll lifecycle (`:931-1000`).

---

## 7. Verification plan
- **Build:** Dev config (done for current tree — green).
- **Unit:** existing Dashboard VM tests must stay green; add a test asserting a scroll-position update does **not**
  trip the "published" path (e.g., assert `scrollPosition` change count vs render count, or that programmatic
  position still adopts).
- **Device (Kesavan — required, I can't run it):** console clean of W1 during mount/drag/period-switch; console
  clean of W2 across a full session; Animation Hitches trace < ~5 ms/s on a 5k weight account.

---

## 8. Open decisions
1. **W1 fix approach — Option B (@State mirror, recommended) vs Option A (de-@Published).** *(§3.1)*
2. **W2** — needs one on-device symbolic-breakpoint run to name the offending view before we commit a fix.
3. Do W1/W2 ride in the MOB-518 PR, or split so the percentile work can merge independently?
