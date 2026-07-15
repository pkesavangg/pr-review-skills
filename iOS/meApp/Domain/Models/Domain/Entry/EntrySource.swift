//
//  EntrySource.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

enum EntrySource: String, Codable, Equatable {
    case manual
    case bluetooth
    case bluetoothMonitor = "bluetooth monitor"
    case lcbtScale = "lcbt scale"
    case wifiScale = "wifi scale"
    case appsyncScale = "appsync scale"

    /// Classifies a raw source string (from a DTO/snapshot) as a manually-typed entry
    /// vs a device-synced reading (BT / Wi-Fi / AppSync scale / BP monitor / baby-scale).
    ///
    /// Per MOB-1172 the edit behaviour depends on *how* an entry was recorded: manual
    /// entries are fully editable (values + note); device-synced entries are note-only.
    /// A nil/empty source is treated as manual — legacy manually-entered records carried
    /// no source string, and any baby graduation code (e.g. "0220") counts as device-synced.
    static func isManualEntry(_ rawSource: String?) -> Bool {
        guard let raw = rawSource?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return true
        }
        return raw.caseInsensitiveCompare(EntrySource.manual.rawValue) == .orderedSame
    }
}
