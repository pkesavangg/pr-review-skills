---
name: baby-profile
description: Implement or modify Phase 2 baby profiles on iOS — baby CRUD against /v3/baby/, the Baby SwiftData model, BabyProfile/BabySnapshot, and the baby product-type auto-add. Use when working on baby onboarding, baby profile screens, baby growth data, or BabyService/BabyRepositoryAPI. Note iOS implements owner-CRUD only (no sharing/permissions/invitations yet). Pair with phase2-context.
---

# Baby profile (iOS) — `/v3/baby/`

> `/v3` lives in `API_BASE_URL`; `Endpoint` cases use bare paths (`/baby/`).

## Scope on iOS (important)

iOS implements **owner CRUD only**. The server spec also defines shared-account **permission levels (1/2/3)** and **invitations** (`/v3/invitation/:babyId`), but these are **intentionally excluded from the iOS app** (see the doc-comment in `BabyRepositoryAPIProtocol.swift`). Do **not** invent permission/invitation types in iOS code — there are none. Only the 4 owner endpoints are wired.

## Layer map (real files)

| Layer | Type | File |
|-------|------|------|
| Service protocol | `BabyServiceProtocol` (`@MainActor`) | `meApp/Domain/Services/BabyServiceProtocol.swift` |
| Service impl | `BabyService` (`ObservableObject`, `babiesPublisher`) | `meApp/Data/Services/BabyService.swift` |
| API repo protocol | `BabyRepositoryAPIProtocol` | `meApp/Domain/Repositories/BabyRepositoryAPIProtocol.swift` |
| API repo impl | `BabyRepositoryAPI` | `meApp/Data/API/BabyRepositoryAPI.swift` |
| DB model | `Baby` (`@Model`, `@Attribute(.unique) id`) + `Baby+Snapshot.swift` | `meApp/Domain/Models/DB/Baby.swift` |
| DTOs | `BabyRequest`, `BabyResponse` | `meApp/Domain/Models/API/Baby/` |
| Domain | `BabyProfile`, `BabySnapshot` | `meApp/Domain/Models/Domain/Product/` |
| Endpoints | `baby`, `babyId(_)` | `meApp/Domain/Models/API/EndPoints.swift` |

## API repo methods

- `listBabies() -> [BabyResponse]` — `GET /baby/`
- `createBaby(BabyRequest) -> BabyResponse` — `POST /baby/`
- `updateBaby(babyId, BabyRequest) -> BabyResponse` — `PUT /baby/{id}`
- `deleteBaby(babyId)` — `DELETE /baby/{id}` (204)

## Service surface

`currentBabies: [Baby]`, `babiesPublisher`, `saveBaby(name:accountId:deviceId:birthday:biologicalSex:birthLengthInches:birthWeightLbs:birthWeightOz:)`, `updateBabyProfile(...)`, `deleteBaby`, `loadBabies(for:)`. Private `mergeRemoteBabies`, `appendBabyProductTypeIfNeeded` — creating a baby **auto-adds `baby` to `productTypes`** (see `product-selection`).

- `BabyRequest.sex` ∈ `male` / `female` / `private`.
- Birth metrics: input in lbs/oz/inches at the service; baby entry storage is decigrams (weight) / mm (length) — see `BabyEntrySnapshot` in `unified-entries`.
- Pregnancy: profile supports `dueDate` + `isBorn` per the API.

## Snapshot boundary
Publish `BabySnapshot` / `BabyProfile`, never the `Baby` `@Model` (snapshot-boundary rule, `swiftdata` skill).

## Checklist
- New baby field → `Baby` `@Model` + `BabyRequest`/`Response` + `BabySnapshot` + migration (`storage-change`).
- Reuse `Features/Common/` components; previews + accessibility on new screens.
- Mock `BabyRepositoryAPIProtocol`; `meAppTests` coverage on a physical device.
