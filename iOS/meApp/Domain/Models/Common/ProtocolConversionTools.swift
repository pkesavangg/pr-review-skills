// ProtocolConversionTools.swift
// Utility class for protocol-specific conversion (e.g., int <-> hex using protocol types)

import Foundation

final class ProtocolConversionTools {
    // MARK: - Protocol Conversion
    /// Converts integer to hex string for protocol (R4 or other)
    static func convertIntToHex(_ value: Int, protocolType: ProtocolType) -> String {
        // Scales' broadcastIds and passwords are stored as integers and need to be
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
        let regex = try! NSRegularExpression(pattern: ".{2}")
        let nsrange = NSRange(convertedValue.startIndex..<convertedValue.endIndex, in: convertedValue)
        let matches = regex.matches(in: convertedValue, options: [], range: nsrange)
        let hexPairs = matches.map {
            String(convertedValue[Range($0.range, in: convertedValue)!])
        }
        return hexPairs.reversed().joined().uppercased()
    }

    static func getProtocolTypeFromScaleType(scaleType: ScaleSourceType) -> ProtocolType {
      if scaleType == .btWifiR4 {
        return .R4
      } else if scaleType == .bluetooth {
        return .A3
      } else {
        return .A6
      }
    }

}
