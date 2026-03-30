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

    /// Subdirectory for `GifView` / PNG lookups, e.g. `Gifs/BpmMonitors/A6/0663` or `…/0665`.
    static func gifBundleSubdirectory(for sku: String) -> String {
        "Gifs/BpmMonitors/A6/\(sku)"
    }

    /// A6 monitors use `User_A` / `User_B` GIFs.
    static func userGifName(slot: Int) -> String {
        let letter = slot == 1 ? "A" : "B"
        return "\(deviceType)_User_\(letter)"
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
