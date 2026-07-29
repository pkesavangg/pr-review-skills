# SKU → setup type

Resolve the customer's scale model **before** advising, so you don't ask a Bluetooth-only owner
about Wi-Fi networks (or the reverse). Each wrong guess costs a full round-trip through CS to the
customer.

**Source of truth on the released line:** `main:iOS/meApp/Features/Common/Constants/Scales.swift`
— `SCALES: [ScaleItemInfo]`. Read it at the ref you're answering for (`code-lookup.md`):

```bash
git show main:iOS/meApp/Features/Common/Constants/Scales.swift
```

## Released catalogue (`main` / v5.0.3) — 24 models

| Family | SKUs | `setupType` | Body comp |
|---|---|---|---|
| **AppSync** | 0340, 0341, 0343, 0345, 0346, 0347, 0364, 0369, 0370 | `.appSync` | yes |
| **AppSync** (weight only) | 0342, 0358, 0359, 0371 | `.appSync` | no |
| **Bluetooth** | 0375, 0376, 0380 | `.bluetooth` | no |
| **Bluetooth** | 0382 | `.bluetooth` | yes |
| **Bluetooth (LCBT)** | 0378, 0383 | `.lcbt` | yes |
| **Wi-Fi** | 0385 | `.wifi` | yes |
| **Wi-Fi** | 0396 | `.wifi` | no |
| **Wi-Fi (ESP-Touch)** | 0384 | `.espTouchWifi` | yes |
| **Wi-Fi (ESP-Touch)** | 0397 | `.espTouchWifi` | no |
| **BT + Wi-Fi (R4)** | **0412** — "AccuCheck Verve Smart Scale" | `.btWifiR4` | yes |

## Not in the shipped app

**Baby scales `0220` / `0222` (`.babyScale`) exist only on `develop`** — Phase 2. The working tree
lists 26 models; `main` lists 24. If CS reports a baby scale against a 5.0.x build, that product
isn't in their app; check the report before investigating.

## Reading the setup type

| `setupType` | What triage should assume |
|---|---|
| `.appSync` | **No BLE pairing at all** — sync is by scanning a code on the scale with the camera. Pairing checklists don't apply; camera/scan issues do. |
| `.bluetooth`, `.lcbt` | BLE pairing only. **No Wi-Fi** — never ask about networks or routers. Holds a connection to one phone at a time (K-02). |
| `.wifi`, `.espTouchWifi` | Wi-Fi provisioning. Two different provisioning paths — **confirm the mechanics in code at the customer's ref before advising steps**, don't assume they behave alike. |
| `.btWifiR4` (0412 only) | Both transports. BLE when the app is open and connected, Wi-Fi otherwise (K-04). Network list is scanned **by the scale**, 2.4 GHz only (K-01). Profile management needs an active BLE connection (K-12). |

## Two caveats

1. **This array backs the help-screen model list.** Absence from it is *not* proof a SKU is
   unsupported — confirm against the pairing/device code before telling CS a scale isn't supported.
2. **The file is named differently across refs.** `Scales.swift` / `ScaleItemInfo` on `main`;
   `Devices.swift` / `DeviceItemInfo` in the working tree (Phase 2 Scale→Device rename). Quote the
   released name in anything customer-facing.
