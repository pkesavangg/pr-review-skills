/// BPM device data for the device list in the help screen
let BPMS: [ScaleItemInfo] = [
    ScaleItemInfo(productName: "Blood Pressure Monitor", sku: "0603", imgPath: AppAssets.bpm0603, setupType: .bpm, bodyComp: false),
    ScaleItemInfo(productName: "Blood Pressure Monitor", sku: "0634", imgPath: AppAssets.bpm0634, setupType: .bpm, bodyComp: false),
    ScaleItemInfo(productName: "Blood Pressure Monitor", sku: "0661", imgPath: AppAssets.bpm0661, setupType: .bpm, bodyComp: false),
    ScaleItemInfo(productName: "Blood Pressure Monitor", sku: "0663", imgPath: AppAssets.bpm0663, setupType: .bpm, bodyComp: false)
]

/// Set of BPM SKU values for quick lookup.
// swiftlint:disable:next identifier_name
let BPM_SKUS: Set<String> = Set(BPMS.map { $0.sku })
