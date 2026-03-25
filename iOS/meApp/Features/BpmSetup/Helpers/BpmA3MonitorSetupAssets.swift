///
///  BpmA3MonitorSetupAssets.swift
///  meApp
///
///  Bundled media for A3-series BPM setup (SKUs 0603, 0634, 0661), sourced from the Balance Health BPM app.
///  Files are stored under `Resources/BpmMonitors/A3/<sku>/` and prefixed with the device type
///  (e.g. `A3_Cuff.gif`) to avoid name collisions across device type folders.

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
    }

    /// Returns the device-type-prefixed resource name, e.g. `"A3_Cuff"`.
    static func resourceName(_ file: String) -> String {
        "\(deviceType)_\(file)"
    }

    /// All A3 setup GIFs live under the `0603` asset folder.
    static func resolvedAssetSku(_ sku: String) -> String {
        "0603"
    }

    /// Subdirectory for `GifView` / bundle lookups, e.g. `Gifs/BpmMonitors/A3/0634` when `sku` is `0661`.
    static func gifBundleSubdirectory(for sku: String) -> String {
        "Gifs/BpmMonitors/A3/\(resolvedAssetSku(sku))"
    }

    /// GIF name (no extension) for user confirmation animations.
    /// All A3 SKUs share the same `A3_User_1` / `A3_User_2` GIFs under 0603.
    static func userGifName(sku: String, slot: Int) -> String {
        "\(deviceType)_User_\(slot)"
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
