///
///  BpmA3MonitorSetupAssets.swift
///  meApp
///

import UIKit

/// Resolves bundle resource names for A3 BPM setup GIFs and images.
///
/// Product hero stills are in the asset catalog under `Images/Monitors/A3/` (see ``AppAssets``).
enum BpmA3MonitorSetupAssets {
    private static let deviceType = "A3"

    enum ImageFile {
        static let cuff = "Cuff"
        static let memButton = "MEM_Button"
        static let monitorStartStop = "Monitor_StartStop"
        static let start = "Start"
        static let setUser = "SetUser"
        static let syncing = "Syncing"
        static let monitorOff = "Monitor_Off"
        static let pulse = "Pulse"
        static let powerSwitch = "Power_Switch"
    }

    /// Returns the device-type-prefixed resource name, e.g. `"A3_Cuff"`.
    static func resourceName(_ file: String) -> String {
        "\(deviceType)_\(file)"
    }

    /// Returns a per-SKU resource name, e.g. `"A3_0634_Cuff"`.
    static func perSkuResourceName(sku: String, _ file: String) -> String {
        "\(deviceType)_\(sku)_\(file)"
    }

    /// SKUs that carry their own full set of setup GIFs/images.
    private static let skusWithPerSkuAssets: Set<String> = ["0634", "0636"]

    /// Returns the folder SKU for non-user GIFs. SKUs in ``skusWithPerSkuAssets``
    /// resolve to themselves; all others fall back to the shared `0603` folder.
    static func resolvedAssetSku(_ sku: String) -> String {
        skusWithPerSkuAssets.contains(sku) ? sku : "0603"
    }

    /// Subdirectory for general setup GIFs (Cuff, Start, etc.), e.g. `Gifs/BpmMonitors/A3/0603`.
    static func gifBundleSubdirectory(for sku: String) -> String {
        "Gifs/BpmMonitors/A3/\(resolvedAssetSku(sku))"
    }

    /// Subdirectory for per-SKU user GIFs, e.g. `Gifs/BpmMonitors/A3/0604`.
    static func userGifBundleSubdirectory(for sku: String) -> String {
        "Gifs/BpmMonitors/A3/\(sku)"
    }

    /// GIF name (no extension) for user confirmation animations.
    /// 0603 uses numeric `User_1` / `User_2`; all others use `{deviceType}_{sku}_User_A` / `…_User_B`.
    static func userGifName(sku: String, slot: Int) -> String {
        if sku == "0603" {
            return "\(deviceType)_User_\(slot)"
        }
        let letter = slot == 1 ? "A" : "B"
        return "\(deviceType)_\(sku)_User_\(letter)"
    }

    static func path(forResource name: String, extension ext: String) -> String? {
        let prefixed = resourceName(name)
        return Bundle.main.path(forResource: prefixed, ofType: ext)
    }

    static func loadUIImage(name: String, extension ext: String) -> UIImage? {
        guard let path = path(forResource: name, extension: ext) else { return nil }
        return UIImage(contentsOfFile: path)
    }
}
