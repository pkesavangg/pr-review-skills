// BPM catalog: one row per product line (same grouping as Balance Health “My Monitors” model list).
// Primary `sku` is used for setup, BLE, and asset lookup; alternates map here for manual entry / broadcast names.

/// Alternate stamped model numbers → primary SKU used for pairing and artwork.
private let bpmAlternateToPrimarySku: [String: String] = [
    "0664": "0604",
    "0665": "0663",
    "0667": "0661",
    "0639": "0636"
]

/// Resolves a user-entered or broadcast code to the canonical BPM row SKU.
func primaryBpmSetupSku(for code: String) -> String {
    bpmAlternateToPrimarySku[code] ?? code
}

/// Model line shown in My Scales (Bluetooth) and BPM setup model grid — matches BH monitor list order and grouping.
let BPMS: [ScaleItemInfo] = [
    ScaleItemInfo(
        productName: "Smart Wrist Blood Pressure Monitor",
        sku: "0603",
        imgPath: AppAssets.bpm0603,
        setupType: .bpm,
        bodyComp: false,
        hasNumericUsers: true,
        broadcastName: "gG BPM 0603"
    ),
    ScaleItemInfo(
        productName: "Smart Blood Pressure Monitor",
        sku: "0604",
        imgPath: AppAssets.bpm0604,
        setupType: .bpm,
        bodyComp: false,
        toggleButton: true,
        broadcastName: "1490BT"
    ),
    ScaleItemInfo(
        productName: "Smart Pro-Series Blood Pressure Monitor",
        sku: "0634",
        imgPath: AppAssets.bpm0634,
        setupType: .bpm,
        bodyComp: false,
        broadcastName: "gG BPM 0634"
    ),
    ScaleItemInfo(
        productName: "All-In-One Bluetooth Blood Pressure Monitor",
        sku: "0636",
        imgPath: AppAssets.bpm0636,
        setupType: .bpm,
        bodyComp: false,
        hasStartButton: false,
        broadcastName: "gG BPM 0636"
    ),
    ScaleItemInfo(
        productName: "Smart Blood Pressure Monitor",
        sku: "0663",
        imgPath: AppAssets.bpm0663,
        setupType: .bpm,
        bodyComp: false,
        broadcastName: "gG BPM 0663"
    ),
    ScaleItemInfo(
        productName: "Smart Blood Pressure Monitor",
        sku: "0661",
        imgPath: AppAssets.bpm0604,
        setupType: .bpm,
        bodyComp: false,
        toggleButton: true,
        broadcastName: "gG BPM 0661"
    )
]

/// Label shown next to the product image (grouped model numbers).
func bpmListModelLabel(primarySku: String) -> String {
    switch primarySku {
    case "0603": return "0603"
    case "0604": return "0604/0664"
    case "0634": return "0634"
    case "0636": return "0636/0664"
    case "0663": return "0663/0665"
    case "0661": return "0661/0667"
    default: return primarySku
    }
}

/// Looks up the catalog row for a typed or broadcast SKU (accepts alternates such as 0664 → 0604).
func bpmCatalogItem(forEnteredCode code: String) -> ScaleItemInfo? {
    let primary = primaryBpmSetupSku(for: code)
    return BPMS.first { $0.sku == primary }
}

/// All valid 4-digit BPM codes (primaries + alternates) for validation and name matching.
let bpmSkus: Set<String> = {
    var set = Set(BPMS.map(\.sku))
    set.formUnion(bpmAlternateToPrimarySku.keys)
    return set
}()

// A3 BPM monitor SKUs (primary and alternates) — Bluetooth + Location permission flow.
let a3BpmSkus: Set<String> = ["0603", "0604", "0634", "0636", "0664", "0639"]

// A6 BPM monitor SKUs — Bluetooth + Location permission flow.
let a6BpmSkus: Set<String> = ["0663", "0665", "0661", "0667"]
