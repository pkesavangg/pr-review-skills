---
name: phase2-context
description: Authoritative brief on Me.Health 2.0 ("Mega App" / Phase 2) — the multi-product merge of Weight + Blood Pressure (Balance) + Baby, the new unified v3 APIs, the productTypes/measurementUnits account model, and backward-compat rules. Use whenever a task touches Phase 2, "2.0", the Mega App, multi-product, productTypes, measurementUnits, unified entries, /v3/entries, /v3/paired-device, baby profiles/permissions, blood pressure / BPM, or when someone asks "what's new in Phase 2 / what changed vs the shipped app".
---

# Me.Health 2.0 (Phase 2) — context

## What it is

Phase 1 shipped **Weight Gurus** (weight + body composition) as `v5.0.0`–`v5.0.2` on the `main` / MA line. **Phase 2 merges three products into one app and one server (wgServer3 `/v3`):**

| Product | `category` (entries) | `deviceType` | `productTypes` value |
|---------|----------------------|--------------|----------------------|
| Weight Gurus | `weight` | `weight_scale` | `weight` |
| Balance (blood pressure) | `bp` | `bpm` | `blood_pressure` |
| Baby | `baby` | `baby_scale` | `baby` |

**Current branch:** work happens on `develop` (Phase 2 `phase2-dev` was merged into it). `main` is 5.0.x hotfixes only.

## Account / product model

- **`productTypes`**: array of `weight` | `blood_pressure` | `baby`. Auto-added when a device is paired / a baby is created / an entry is made, **and** directly settable via `PATCH /v3/account/products`. Default `["weight"]`.
- **`measurementUnits`**: `metric` | `imperialLbOz` | `imperialLbDecimal`. Required for baby; settable via `PATCH /v3/account/measurement-units`.
- **Conditional signup validation:** `gender`/`dob`/`height` are required only when `productTypes` includes `weight`/`blood_pressure` (height: weight only). Baby-only accounts skip them. Returned on signup/login/`GET`/`PUT /v3/account/` and all 8 `PATCH /account/*`.
- **`POST /v3/account/email-check`** (no auth) → `{ isAvailable }`.

## Unified APIs (new app) — legacy stays live for old apps

| New unified | Replaces (legacy, still live) |
|-------------|-------------------------------|
| `POST/GET/PATCH/DELETE /v3/paired-device/` (`deviceType`) | `/v3/paired-scale/` |
| `POST /v3/review/` (`reviewType`: app/scale/monitor) | `/v3/review/app`, `/v3/review/scale` |
| `POST /v3/entries/` (raw array, `category` routes) | `/v3/operation/`, `/v3/operation/r4/` |
| `GET /v3/entries/` (sync + cursor) | `GET /v3/operation/*` |
| `GET /v3/entries/csv` | `/v3/operation/csv`, `/v3/operation/r4/csv` |

> **Backward compatibility is a hard requirement.** Never remove or break the legacy endpoints.

### `POST /v3/entries/` (write)
Body is a **raw array** (single entry = array of 1), **atomic** (any invalid entry fails the whole request). Common fields: `category`, `operationType` (`create` | `delete`; baby also `edit`), `entryTimestamp`. Per-category required on create:
- `weight`: `weight`
- `bp`: `systolic`, `diastolic`, `pulse`, `source`
- `baby`: `babyId`, `entryId`, `entryType` + per-`entryType` fields

Baby `entryType`s: `weight`, `feedingBottle` (`feedingMilliliters`), `feedingNursing` (`feedingTimeSecondsLeft/Right`), `measureLength` (`babyLengthMillimeters`), `sleep` (`sleepTimeMinutes`), `diaperChange` (`diaperType`: wet/dirty/both), `snapshot` (`photo`).

### `GET /v3/entries/` (read) — two modes
- **Sync:** `?start=<iso>` → all entries with `serverTimestamp > start`, no limit. Response `{ entries, timestamp }`; save `timestamp` for next sync.
- **Cursor:** `?cursor=<iso>&limit=<n>` (default 20, max 100) → entries with `entryTimestamp < cursor`. Response `{ entries, nextCursor, hasMore }`.
- Filters: `?category=`, `?babyId=`. `cursor` wins if both given. Responses are **flat** (no nested `data`), nulls stripped.

## Baby specifics
- CRUD: `POST/GET/PUT/DELETE /v3/baby/`, `GET /v3/baby/:id` , `GET /v3/baby/:id/accounts`.
- **Permission levels:** 1 = view, 2 = view + create entries, 3 = owner. Writing baby entries needs level ≥ 2; edit/delete profile needs owner.
- **Invitations:** `POST /v3/invitation/:babyId` (caregiver email + permission level 1/2).
- Pregnancy support via `dueDate` + `isBorn`.

## iOS mapping (where this lives in code)
- Snapshots: `EntrySnapshot` → `BathScaleEntrySnapshot`, `BPMEntrySnapshot`, `BabyEntrySnapshot`; `DeviceSnapshot`. (See `iOS/CLAUDE.md` → Snapshots.)
- Endpoints: `meApp/Domain/Models/API/EndPoints.swift`.
- For the technical how-to, use the iOS skills: `unified-entries`, `paired-device`, `baby-profile`, `product-selection`.

## Authoritative sources
- **API spec:** [Me App 2.0 — API Changes Specification](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1458962434/Me+App+2.0+API+Changes+Specification) (Confluence; 15 modified + 19 new = 34 endpoints).
- **Design:** [Me.Health Mega App 2.0 Figma](https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=8-2145) — see the iOS `phase2-design-system` skill.
- **Tracking:** MOB board (GGT-Mobile, board 1088); Phase 2 epics `MOB-376`–`386` (iOS), `MOB-377`–`381` (Android).
