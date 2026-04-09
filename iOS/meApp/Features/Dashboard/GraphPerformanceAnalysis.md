# Graph & Dashboard Performance Analysis

## Current Status

Latest baseline: `Untitled2.trace`

- Recorded on `2026-04-09` from `17:07:01 +05:30` to `17:09:34 +05:30`
- Device: `iPhone 11`, iOS `18.7.3`
- Duration: `152.71s`
- Total hang time: `130.49s`
- Hung share of session: `85.5%`
- Total hang events: `76`
- Microhangs: `21`
- Hangs: `28`
- Severe hangs (`> 2s`): `27`
- Longest hang: `4.81s`
- FPS samples: `151`
- Zero-FPS samples: `128` (`84.8%`)
- Average FPS: `3.57`
- Average FPS when rendering: `23.43`
- Max FPS: `47`
- FPS samples `>= 55`: `0`
- FPS samples `< 20`: `137`
- Average GPU utilization: `0.19%`
- Max GPU utilization: `13%`

Current conclusion:

1. The app is still primarily blocked by main-thread work, not GPU saturation.
2. The worst user-visible problem is still loading-time and post-loading responsiveness, especially around the snapshot overview and the transition after all three snapshot cards appear.
3. Scrolling is still below target because long blocked periods prevent frame production.
4. The loading screen is better than before, but it is still not at an acceptable level for hang frequency or smoothness.

Most visible remaining loading issue:

1. The snapshot overview still has a risk of hitching during the transition from skeleton cards to fully rendered cards.
2. The baby snapshot remains the riskiest of the three cards because it also computes percentile-curve data after reveal.

## Remediations

Already applied:

1. `EntryService.loadDashboardData(entryType:)` coalesces duplicate callers so concurrent dashboard refreshes share one in-flight load.
2. Product switching no longer forces an immediate duplicate dashboard load before dashboard initialization.
3. `EntryService.performSync()` skips dashboard recomputation when sync does not change dashboard data.
4. `DashboardLifecycleManager.initializeDashboard()` defers sync so first render can settle before sync work starts competing with UI work.
5. `DashboardLifecycleManager.onAppearActions()` no longer stacks another near-immediate sync on top of initialization.
6. `DashboardDisplayManager.updateMetricsForCurrentView()` coalesces repeated metric refreshes and now skips metric recomputation while the graph is actively scrolling.
7. `DashboardDisplayManager.getOperationsForLabelDateRange()` caches filtered label-range operations so repeated display and metric reads do not keep re-filtering the dashboard timeline on the main thread.
8. `MultiDeviceSnapshotView` now reveals snapshot cards sequentially instead of mounting all three chart-heavy cards in the same frame after skeleton loading finishes.
9. `WeightSnapshotCard`, `BpmSnapshotCard`, and `BabySnapshotCard` now run cache/chart preprocessing at `.utility` priority instead of `.userInitiated`.
10. `BabySnapshotCard` now caches grouped percentile-curve points by baby, week window, and unit so repeated reveals do not regenerate WHO percentile data on the loading transition.
11. `MultiDeviceSnapshotViewModel` now marks snapshot cards ready independently, so the overview no longer waits for every dataset to finish before showing the first finished card.
12. `EntryService.loadBabyDashboardData(babyId:)` now coalesces overlapping loads for the same baby profile and runs aggregation work at `.utility` priority.

Next remediations to focus on:

1. Keep targeting the loading screen first, especially the hang after all three snapshots appear.
2. Keep monitoring the baby snapshot specifically, but the next percentile-curve step is now to validate whether the new per-window cache materially reduces the post-reveal hang.
3. Avoid any full dashboard reloads triggered from snapshot-loading or metric-update notifications when a local refresh is enough.
4. Continue moving remaining `EntryService` DTO filtering, signature work, and summary reducers behind a dedicated background actor instead of a `@MainActor` service shell.
5. Re-profile specifically around app launch, loading screen, snapshot reveal, and the first transition into dashboard content.

Validation still required:

1. Re-run Instruments after the latest snapshot-loading changes.
2. Confirm whether hang frequency during loading and right after snapshot reveal drops materially.
3. Confirm whether scrolling improves after the loading-screen bottleneck is reduced.
