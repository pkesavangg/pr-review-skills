---
date: 2026-04-07
topic: bp-graph-view
ticket: MA-3487
---

# BP Graph View — Implementation Plan

## What We're Building

Refactor GraphViewModel to be product-reactive so the same 4 VM instances (one per segment) serve weight, BP, and baby charts. When the user switches product via productSelectionManager, each VM cancels old data, subscribes to new product's data via adapter, and pushes into the same CartesianChartModelProducer via runTransaction.

## Architecture Decisions

1. **4 VMs total** — one per segment (week/month/year/total), NOT per product
2. **Same CartesianChartModelProducer** — product switch uses runTransaction, no recreation
3. **VM observes productSelectionManager.selectedProduct** in onDependenciesReady()
4. **GraphDataAdapter** converts product-specific GraphData to line series
5. **Per-product chart builders** (WeightChart, BpChart) selected in GraphView
6. **Per-product headers** (WeightChartHeader, BpChartHeader) selected in GraphPagerView
7. **Vico ScrollAwareRangeProvider.buildCache** updated to accept List<List<Entry>> for multi-series Y range

## Files to Change

### 1. GraphViewModel.kt — Remove @Assisted product, observe productSelectionManager

**Changes:**
- Remove `@Assisted val product: ProductSelection` from constructor
- Remove `product` from `Factory.create()`
- Add `private var adapter: GraphDataAdapter` (mutable)
- Add `private var productDataJob: Job?` to cancel on product switch
- Move product-dependent data subscription to `onDependenciesReady()`
- Observe `productSelectionManager.selectedProduct.collectLatest`:
  - Update adapter via `GraphDataAdapter.forProduct(newProduct)`
  - If Weight → subscribe to entryService.daywiseBodyScaleAverages / monthlyBodyScaleAverages
  - If BP/Baby → subscribe to historyService.getDailyGraphData(product) / getMonthlyGraphData(product)
  - Push data via same modelProducer.runTransaction
- Add `GraphIntent.UpdateProduct` to store current product in GraphState
- Keep weight-specific logic (goal, secondary key, weightless) gated behind `product is MyWeight`

### 2. GraphState.kt — Add product field

**Changes:**
- Add `val product: ProductSelection = ProductSelection.MyWeight`

### 3. GraphIntent.kt — Add UpdateProduct intent

**Changes:**
- Add `data class UpdateProduct(val product: ProductSelection) : GraphIntent`

### 4. GraphReducer.kt — Handle UpdateProduct

**Changes:**
- Add `is GraphIntent.UpdateProduct -> state.copy(product = intent.product)`

### 5. GraphPagerView.kt — Remove product from VM key, read from state

**Changes:**
- Remove `selectedProduct` param (VM gets it from productSelectionManager)
- Remove product from hiltViewModel key — back to `"GraphViewModel-$page"`
- Remove product from factory.create() call
- Remove `key(selectedProduct)` wrapper
- Read product from `graphState.product` for header/chart switching
- Keep `selectedProduct` param for non-VM uses (or remove entirely)

### 6. GraphView.kt — Read product from state

**Changes:**
- Remove `product` param
- Read `product` from `state.product` (GraphState field)
- Keep the `when(product)` switch for chart builder selection

### 7. DashboardScreen.kt — Remove selectedProduct pass to GraphPagerView

**Changes:**
- Remove `selectedProduct` param from GraphPagerView call (VM handles it internally)

### 8. Vico ScrollAwareRangeProvider.kt — Multi-series buildCache + callback signature

**Changes:**
- `buildCache` stores per-series entries, builds merged X array for binary search
- `computeVisibleEntries` returns `List<List<Pair<Double, Double>>>` (per series)
- `onVisibleEntries` callback receives `List<List<Pair<Double, Double>>>`
- Single-series (weight) consumers use `.firstOrNull()`
- Multi-series (BP) consumers use `.flatMap` for Y range spanning all lines

### 9. WeightChart.kt — Update callback signature

**Changes:**
- Callback receives `visibleSeriesEntries: List<List<Pair<Double, Double>>>`
- Uses `visibleSeriesEntries.firstOrNull()` for single-series Y range

### 10. BpChart.kt — Update callback signature

**Changes:**
- Callback receives `visibleSeriesEntries: List<List<Pair<Double, Double>>>`
- Uses `visibleSeriesEntries.flatMap { it.map { p -> p.second } }` for all-series Y range

### 11. GraphChart.kt (old) — Update callback signature for compatibility

**Changes:**
- Same as WeightChart — uses `.firstOrNull()`

### 12. HistoryRepository.kt — Sample data (already done)

- Daily BP data: 24 entries, local timestamps (00:00:00), for week/month
- Monthly BP data: 12 entries, start-of-month timestamps, for year/total

## Data Flow

```
productSelectionManager.selectedProduct emits new product
  ↓
GraphViewModel.onDependenciesReady() collector fires
  ↓
Cancel productDataJob
  ↓
adapter = GraphDataAdapter.forProduct(newProduct)
handleIntent(UpdateProduct(newProduct))
  ↓
Subscribe to appropriate flow:
  Weight → entryService.daywiseBodyScaleAverages (WEEK/MONTH)
           entryService.monthlyBodyScaleAverages (YEAR/TOTAL)
  BP     → historyService.getDailyGraphData(BP) (WEEK/MONTH)
           historyService.getMonthlyGraphData(BP) (YEAR/TOTAL)
  ↓
Data arrives → adapter.toLineSeries(graphData) → List<SeriesData>
  ↓
Same modelProducer.runTransaction { lineSeries { series per adapter } }
  ↓
CartesianChartHost redraws
  ↓
GraphView reads state.product → picks rememberWeightChart or rememberBpChart
GraphPagerView reads state.product → picks WeightChartHeader or BpChartHeader
```

## Edge Cases

- Product switch while scroll animation in progress — cancel scroll debounce job
- Empty data for new product — show empty graph state
- Weight-specific features (goal, secondary key) — gated behind product check
- Initial load — productSelectionManager may emit before historyService.accountId is set
