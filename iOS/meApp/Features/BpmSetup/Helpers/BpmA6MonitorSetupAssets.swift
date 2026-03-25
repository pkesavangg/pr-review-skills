///
///  BpmA6MonitorSetupAssets.swift
///  meApp
///
///  Bundled media for A6-series BPM setup (SKUs 0663, 0665).
///  Files are stored under `Resources/BpmMonitors/A6/<sku>/` and prefixed with the device type
///  (e.g. `A6_Start.gif`) to avoid name collisions across device type folders.

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
    }

    /// Returns the device-type-prefixed resource name, e.g. `"A6_Start"`.
    static func resourceName(_ file: String) -> String {
        "\(deviceType)_\(file)"
    }

    /// BPM has no `0665` monitor folder; bundle files live under `0663`.
    static func resolvedAssetSku(_ sku: String) -> String {
        sku == "0665" ? "0663" : sku
    }

    /// Subdirectory for `GifView`, e.g. `Gifs/BpmMonitors/A6/0663` when `sku` is `0665`.
    static func gifBundleSubdirectory(for sku: String) -> String {
        "Gifs/BpmMonitors/A6/\(resolvedAssetSku(sku))"
    }

    /// A6 monitors use `User_A` / `User_B` GIFs.
    static func userGifName(slot: Int) -> String {
        let letter = slot == 1 ? "A" : "B"
        return "\(deviceType)_User_\(letter)"
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
