---
name: paired-device
description: Implement or modify the Phase 2 unified paired-device path on iOS — /v3/paired-device/ for any deviceType (weight scale, BP monitor, baby scale) plus unified review. Use when working on device pairing across products, DeviceService, DeviceAPIRepository, PairedDeviceRequest/Response, DeviceType mapping, or the deviceType -> productTypes auto-add. Pair with phase2-context.
---

# Paired device (iOS) — `/v3/paired-device/`

> `/v3` lives in `API_BASE_URL`; `Endpoint` cases use bare paths (`/paired-device/`).

## Layer map (real files)

| Layer | Type | File |
|-------|------|------|
| API repo protocol | `DeviceRepositoryAPIProtocol` | `meApp/Domain/Repositories/DeviceRepositoryAPIProtocol.swift` |
| API repo impl | `DeviceAPIRepository` | `meApp/Data/API/DeviceAPIRepository.swift` |
| Service protocol | `PairedDeviceServiceProtocol: DeviceServiceProtocol` | `meApp/Domain/Services/PairedDeviceServiceProtocol.swift` |
| Service impl | `DeviceService` (publishes `scales: [DeviceSnapshot]`) | `meApp/Data/Services/DeviceService.swift` |
| DTOs | `PairedDeviceRequest`, `PairedDeviceUpdateRequest`, `PairedDeviceResponse` | `meApp/Domain/Models/API/Scale/` |
| Device type | `enum DeviceType { scale, bpm, babyScale }` | `meApp/Domain/Models/Domain/Entry/DeviceType.swift` |
| Endpoints | `pairedDevice(deviceType:)`, `pairedDeviceId(_)` | `meApp/Domain/Models/API/EndPoints.swift` |

## Unified methods (replace, don't remove, the legacy `/paired-scale/` ones)

- `listPairedDevices(deviceType: String?) -> [PairedDeviceResponse]` — `GET /paired-device/?deviceType=`
- `createPairedDevice(PairedDeviceRequest) -> PairedDeviceResponse` — `POST`
- `updatePairedDevice(deviceId, PairedDeviceUpdateRequest) -> PairedDeviceResponse` — `PATCH`
- `deletePairedDevice(deviceId)` — `DELETE` (204)
- `submitReview(ReviewRequest)` — unified `POST /review/`
- Service helper: `DeviceService.pairDevice(_ device: Device, deviceType: DeviceType) -> PairedDeviceResponse` builds the `PairedDeviceRequest`.
- Legacy still present: `listScales`/`createScale`/`editScale`/`deleteScale`/`patchScaleMeta` (`/paired-scale/`). Keep them for old apps.

## `DeviceType` — mind the wire mapping

| case | `.serverValue` (wire) |
|------|------------------------|
| `.scale` | `weight_scale` |
| `.bpm` | `bpm` |
| `.babyScale` | `baby_scale` |

- `.fromServerValue(_:)` (inverse) and `.fromSku(_:)` (derive from product SKU) exist — use them, don't hand-roll string maps.
- Pairing a device **auto-adds the matching product** to `account.productTypes` (server-side; reflected via `AccountSnapshot.productTypes`). See `product-selection`.

## Snapshots
`DeviceService` publishes `DeviceSnapshot` (`Domain/Models/Domain/Scale/DeviceSnapshot.swift`) — never the `Device` `@Model`. Convert on the main actor before `await` (snapshot-boundary rule, `swiftdata` skill).

## Checklist
- New device field → `PairedDeviceRequest`/`Response` + `DeviceSnapshot` + mapping.
- Filter unsynced devices by `accountId` (cross-account MAC/SKU collisions — see iOS/CLAUDE.md gotchas).
- Mock `DeviceRepositoryAPIProtocol`; add `meAppTests` coverage (physical device).
