---
name: unified-entries
description: Implement or modify the Phase 2 unified entries path on iOS — the single /v3/entries write (mixed weight/bp/baby array) and read (sync + cursor pagination) endpoints. Use when working on entry create/sync/history/CSV across products, EntryService, EntryRepositoryAPI, UnifiedEntryRequest/Response, or BPM/baby entry creation. Pair with phase2-context for the API model.
---

# Unified entries (iOS) — `/v3/entries/`

> `/v3` lives in `API_BASE_URL`; `Endpoint` cases use bare paths (`/entries/`). See `phase2-context` for the API contract.

## Layer map (real files)

| Layer | Type | File |
|-------|------|------|
| Service protocol | `EntryServiceProtocol` (`@MainActor`) | `meApp/Domain/Services/EntryServiceProtocol.swift` |
| Service impl | `EntryService` (+ `EntryServiceSupport.swift`) | `meApp/Data/Services/EntryService.swift` |
| API repo protocol | `EntryRepositoryAPIProtocol` | `meApp/Domain/Repositories/EntryRepositoryAPIProtocol.swift` |
| API repo impl | `EntryRepositoryAPI` | `meApp/Data/API/EntryRepositoryAPI.swift` |
| Endpoints | `submitEntries`, `entries(start:cursor:limit:category:babyId:)`, `entriesCSV(...)` | `meApp/Domain/Models/API/EndPoints.swift` |
| DTOs | `UnifiedEntryRequest`, `UnifiedEntryResponse`, `UnifiedEntryResult`, `EntryCategory`, `EntriesCSVRequest`, `ExportResponse`, `BpmOperationDTO`, `BabyEntryRequest` | `meApp/Domain/Models/API/Entry/` |
| Page value | `EntriesPage { entries:[BathScaleOperationDTO], nextCursor:String?, hasMore:Bool }`, `EntriesPagination.defaultLimit = 20` | `meApp/Data/Services/EntryServiceSupport.swift` |

## Write — `submitEntries(_ entries: [UnifiedEntryRequest]) async throws -> UnifiedEntryResponse`

- Raw array, **mixed categories**, **atomic** (one invalid entry fails the batch).
- `UnifiedEntryRequest` carries `EntryCategory` (`weight` / `bp` / `baby`); `UnifiedEntryResult` is flat (all category fields optional) with a `.toOperationDTO()` bridge.
- **Current live write categories on iOS are `weight` + `bp`.** `UnifiedEntryRequest.init?(from dto:)` returns `nil` for `.baby` (baby write is a later iOS milestone). Don't assume baby goes through `submitEntries`; baby uses `EntryService.createBabyEntry(...)`.
- Service entry points: `saveNewEntry`, `saveNewEntries([Entry])`, `createBpmEntry(_:BpmOperationDTO)`, `syncAllEntriesWithRemote()`.

## Read — `fetchEntries(start:cursor:limit:category:babyId:) async throws -> BathScaleOperationListResponse`

One method, **two modes** (don't pass both — `cursor` wins server-side):
- **Sync:** `start` (ISO) → everything with `serverTimestamp > start`. Save the response `timestamp` for the next sync.
- **Cursor:** `cursor` + `limit` (default 20, max 100) → `entryTimestamp < cursor`; use `nextCursor` / `hasMore`. Service wrapper: `fetchEntriesPage(cursor:limit:category:babyId:) -> EntriesPage`.
- Filters: `category` (`weight`/`bp`/`baby`), `babyId`. Responses are flat, nulls stripped.

## Snapshots (what crosses the service boundary — never the `@Model`)

`EntrySnapshot` (`Domain/Models/Domain/Entry/EntrySnapshot.swift`) holds `scaleEntry`, `scaleEntryMetric`, `bpmEntry`, `babyEntry` (`EntryChildSnapshots.swift`):
- `BPMEntrySnapshot { systolic:Int, diastolic:Int, meanArterial:String, pulse:Int }`
- `BabyEntrySnapshot { babyId:String, length:Int /*mm*/, weight:Int /*decigrams*/, source:String? }`

Follow the snapshot-boundary rule (`swiftdata` skill): convert `@Model` → snapshot on the main actor before any `await`.

## Checklist
- New entry field → update the DTO (`Entry/`), the snapshot, and the `.toOperationDTO()`/merge bridge.
- Keep legacy `/v3/operation/*` untouched (old apps).
- Add/extend tests in `meAppTests` (physical device) for the category you touch; mock `EntryRepositoryAPIProtocol`.
