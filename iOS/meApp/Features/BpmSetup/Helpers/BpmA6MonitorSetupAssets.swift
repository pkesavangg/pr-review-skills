///
///  BpmA6MonitorSetupAssets.swift
///  meApp
///

import UIKit

/// Resolves bundle resource names for A6 BPM setup GIFs and images.
enum BpmA6MonitorSetupAssets {
    private static let deviceType = "A6"

    enum ImageFile {
        static let cuff = "Cuff"
        static let pulse = "Pulse"
        static let start = "Start"
        static let syncing = "Syncing"
        static let monitorOff = "Monitor_Off"
        static let setUser = "SetUser"
        static let monitorStartStop = "Monitor_StartStop"
    }

    /// Returns the device-type-prefixed resource name, e.g. `"A6_Start"`.
    static func resourceName(_ file: String) -> String {
        "\(deviceType)_\(file)"
    }

    /// Returns a per-SKU resource name, e.g. `"A6_0661_Cuff"`.
    static func perSkuResourceName(sku: String, _ file: String) -> String {
        "\(deviceType)_\(sku)_\(file)"
    }

    /// SKUs that carry their own full set of setup GIFs/images (separate from the shared 0663 set).
    private static let skusWithPerSkuAssets: Set<String> = ["0661"]

    /// Returns the resource name for the given SKU and file. Per-SKU assets use
    /// `A6_{sku}_{file}` naming; others use the shared `A6_{file}` naming.
    static func resolvedResourceName(sku: String, _ file: String) -> String {
        skusWithPerSkuAssets.contains(sku) ? perSkuResourceName(sku: sku, file) : resourceName(file)
    }

    /// Subdirectory for `GifView` / PNG lookups, e.g. `Gifs/BpmMonitors/A6/0663` or `…/0665`.
    static func gifBundleSubdirectory(for sku: String) -> String {
        "Gifs/BpmMonitors/A6/\(sku)"
    }

    /// A6 monitors use `{deviceType}_{sku}_User_A` / `…_User_B` GIFs.
    static func userGifName(sku: String, slot: Int) -> String {
        let letter = slot == 1 ? "A" : "B"
        return "\(deviceType)_\(sku)_User_\(letter)"
    }

    static func path(forResource name: String, extension ext: String, sku: String) -> String? {
        let prefixed = resourceName(name)
        return Bundle.main.path(forResource: prefixed, ofType: ext, inDirectory: gifBundleSubdirectory(for: sku))
    }

    static func loadUIImage(name: String, extension ext: String, sku: String) -> UIImage? {
        guard let path = path(forResource: name, extension: ext, sku: sku) else { return nil }
        return UIImage(contentsOfFile: path)
    }
}
