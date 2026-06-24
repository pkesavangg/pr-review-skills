// ProtocolConversionTools.swift
// Utility class for protocol-specific conversion (e.g., int <-> hex using protocol types)

import Foundation

final class ProtocolConversionTools {
    // MARK: - Protocol Conversion
    /// Converts integer to hex string for protocol (R4 or other)
    static func convertIntToHex(_ value: Int, protocolType: ProtocolType) -> String {
        // Devices' broadcastIds and passwords are stored as integers and need to be
        // converted to a Hex string before being sent to the app
        var convertedValue = String(value, radix: 16)

        if protocolType == .R4 {
            convertedValue = ("000000000000" + convertedValue).suffix(12).description
        } else {
            if convertedValue.count < 8 {
                convertedValue = ("0000000" + convertedValue).suffix(8).description
            } else if convertedValue.count > 8 && convertedValue.count < 12 {
                convertedValue = ("0000000" + convertedValue).suffix(12).description
            }
        }

        // Split into pairs, reverse, join, and uppercase
        guard let regex = try? NSRegularExpression(pattern: ".{2}") else {
            return convertedValue.uppercased()
        }
        let nsrange = NSRange(convertedValue.startIndex..<convertedValue.endIndex, in: convertedValue)
        let matches = regex.matches(in: convertedValue, options: [], range: nsrange)
        let hexPairs = matches.compactMap { match -> String? in
            guard let range = Range(match.range, in: convertedValue) else { return nil }
            return String(convertedValue[range])
        }
        return hexPairs.reversed().joined().uppercased()
    }

    static func getProtocolTypeFromDeviceModelType(scaleType: DeviceSourceType) -> ProtocolType {
      if scaleType == .btWifiR4 {
        return .R4
      } else if scaleType == .bluetooth {
        return .A3
      } else {
        return .A6
      }
    }

}
