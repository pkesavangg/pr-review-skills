# Reading the code the customer is actually running

The customer runs a **released** build. `develop` carries Phase 2 (Me.Health 2.0) — multi-product
Weight + Blood Pressure + Baby — which **no customer has**. Feature branches are further still.
Answering a customer question from the working tree describes an app that doesn't exist yet.

## APP VERSION → ref

| Reported APP VERSION | Read this ref |
|---|---|
| **5.0.3** (current shipped) | `main` — verified `versionName = "5.0.3"`, `versionCode = 800004` |
| 5.0.2 | tag `v5.0.2` (2026-05-25) |
| 5.0.1 | tag `v5.0.1` |
| 5.0.0 | tag `v5.0.0` |
| 5.0.4 (in flight) | `origin/release/5.0.4` — not in customers' hands yet |
| "2.0" / Phase 2 / multi-product | `develop` — **not a customer-facing answer** |

Tags `v5.0.0`–`v5.0.3` exist (`v5.0.3` → 2026-06-29), plus `origin/release/5.0.1`…`5.0.4`.
Confirm before relying on it:

```bash
git tag --list | tail -5
git show main:Android/app/build.gradle.kts | grep -E "versionName|versionCode"
```

## Read without checking out

Never `git checkout` to investigate — it disturbs in-progress work and the tree may be dirty. Read
directly from the ref:

```bash
git show main:iOS/meApp/Features/Common/Constants/Scales.swift        # a whole file at a ref
git grep -n "0412" v5.0.2 -- Android/app/src/main                    # search at a ref
git ls-tree main -- iOS/meApp/Features/ScaleSetup/ --name-only       # what exists at a ref
git diff v5.0.2..main -- iOS/meApp/Features/Dashboard/               # changed since their build?
git log --oneline v5.0.2..main -- <path>                             # which commits touched it
```

The last two settle outcome **E** ("already fixed in a newer build") with evidence instead of memory:
diff the customer's version against the version claimed to contain the fix.

## Why this is a hard rule — a worked example

The iOS scale catalogue **does not have the same name on the released ref as in the working tree**:

```
$ git show v5.0.3:iOS/meApp/Features/Common/Constants/Devices.swift
fatal: path 'iOS/meApp/Features/Common/Constants/Devices.swift' exists on disk, but not in 'v5.0.3'
```

| Ref | File | Type |
|---|---|---|
| `main`, `v5.0.3` (shipped) | `Scales.swift` | `SCALES: [ScaleItemInfo]` |
| `develop` (working tree) | `Devices.swift` | `SCALES: [DeviceItemInfo]` |

Same data, renamed in Phase 2 as part of the Scale→Device rename. Cite the working-tree path in a CS
reply and you are describing code the customer's app does not contain. Assume nothing about a path
until it's confirmed at the ref you're answering for.

## Where to look, by subsystem

Paths on the released line (`main`). Confirm with `git ls-tree` — Phase 2 moved several of these.

| Subsystem | iOS | Android |
|---|---|---|
| BLE pairing / scale setup | `iOS/meApp/Features/ScaleSetup/` | `Android/bleWrapper/`, `Android/app/.../features/DeviceSetup/` |
| Scale/device management | `iOS/meApp/Features/Settings/Scale/` | `Android/app/.../core/service/DeviceService.kt` |
| Wi-Fi setup | `iOS/meApp` + `gWifiScalePackage` (SPM) | `Android/app/wificonnect/` |
| Entry sync | `iOS/meApp/Data/Services/EntryService*` | `Android/app/.../core/service/` |
| Health integration | `iOS/meApp/Data/Services/HealthKitService*` | `Android/app/healthconnect/` |
| AppSync (QR) | `iOS/meApp/Features/AppSync/` | `Android/app/appsync/` |
| Logging | `iOS/meApp/Core/Services/LoggerService.swift` | `Android/app/.../core/shared/utilities/logging/AppLog.kt` |

## State the ref

Every investigation summary names the ref that was read — e.g. *"read at `main` (5.0.3, versionCode
800004)"*. A wrong assumption must be visible to the reviewer, not silent.
