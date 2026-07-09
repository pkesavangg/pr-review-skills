# MOB-518 — Weight graph v2: device observations / deferred-issues log

> **What this is.** A running punch-list of issues seen on device while building the new weight engine.
> We deliberately **defer** most of them because a planned step will address them — this log makes sure none
> is forgotten. **After all the architecture + parity work lands, we sweep this list and fix/verify each.**
> Append as new observations come in. Nothing here is committed-code — it's the checklist.

**Legend — Status:** `deferred` (a planned step will fix it) · `verify` (should be fixed by a step we did/plan
— confirm on device) · `investigate` (cause unconfirmed) · `fixed` (done + confirmed).

---

## Observations (as of 2026-07-09, after A3 + A1 + A2 + Phase 4 + V-A4)

| # | Observation (device) | Suspected cause | Planned fix / step | Status |
|---|----------------------|-----------------|--------------------|--------|
| 1 | **Vertical gridlines not shown** (old graph has vertical rules at period/week boundaries; new one only draws horizontal y-gridlines) | `WeightChartView` only adds `AxisGridLine()` on the y-axis; no `.chartXAxis { AxisGridLine()/AxisTick() }` with solid-at-boundary / dashed-between styling | **V3** (rendering parity) | deferred |
| 2 | **Section (period) switch feels heavy / stuck** | On every period switch `GraphView` configured all 4 section VMs + `forceScrollPositionUpdate` + `updateYAxisCache` (the legacy machinery) **even though the new engine was active** — wasted work alongside the new host's own `adopt()`+rebuild. Plus the chartManager's `.idle` 50 ms settle ran on every scroll-end. Dual ownership. | **V-A4 (done)** — GraphView's period-switch handler now `guard !usesNewWeightEngine`s out of the section-VM machinery; the host routes scroll phase straight to `graphManager` (no chartManager `.idle` settle). Single owner. | **verify on device** (switch should feel instant now) |
| 3 | **After a scroll stops, can't scroll again for ~1 s** | **Root cause found (A2's cascade theory was WRONG — removing it didn't help).** The scroll-END **model rebuild** re-emitted scroll-dependent x-geometry — `visibleDomainLength` (per-month for `.month`) and `xAxisTicks` (windowed for large spans) both change with scroll position — so Swift Charts **rebuilt its whole scroll view** every time a scroll stopped, blocking the next scroll. | **Phase 4 (done)** — scroll-end now calls `resettleWeightYAxis` → `ChartModel.withYAxis`, which recomputes ONLY the adaptive y-axis and leaves series + x-geometry byte-identical → no scroll-view rebuild, one y animation. | **verify on device** (re-scroll immediately after a stop should be instant now) |
| 4 | **Selection / crosshair doesn't work** (tap does nothing; no crosshair, header doesn't switch to the tapped point) | Not built yet — the new view has no `.chartXSelection` binding and no crosshair marks; A1 deliberately scoped to the scroll boundary only | **V4** (selection + crosshair + header value + goal) | deferred |

*(“…and some more” — append below as you hit them.)*

| # | Observation (device) | Suspected cause | Planned fix / step | Status |
|---|----------------------|-----------------|--------------------|--------|
| 5 | _(add)_ | | | |

---

## Sweep plan (do at the end, before sign-off)

1. After **A2** + **Phase 4** (single-event settle) → re-check **#3** (scroll-lock) and **#2** (switch heaviness).
2. After **V-A4/V6** (drop the legacy weight machinery) → re-confirm **#2**.
3. **V3** → **#1** (vertical gridlines + x-axis label parity).
4. **V4** → **#4** (selection/crosshair/header/goal).
5. Anything still open → investigate individually against the [feature spec](MOB-518-weight-graph-feature-spec.md).
