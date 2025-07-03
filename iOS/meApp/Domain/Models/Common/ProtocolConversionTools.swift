// ProtocolConversionTools.swift
// Utility class for protocol-specific conversion (e.g., int <-> hex using protocol types)

final class ProtocolConversionTools {
    // MARK: - Constants
    private static let r4HexPadding = "000000000000"
    private static let standardHexPadding = "0000000"

    // MARK: - Protocol Conversion
    /// Converts integer to hex string for protocol (R4 or other)
    static func convertIntToHex(_ value: Int, protocolType: ProtocolType) -> String {
        var convertedValue = String(format: "%x", value)
        if protocolType == .R4 {
            convertedValue = String(r4HexPadding + convertedValue).suffix(12).description
        } else {
            if convertedValue.count < 8 {
                convertedValue = String(standardHexPadding + convertedValue).suffix(8).description
            } else if convertedValue.count > 8 && convertedValue.count < 12 {
                convertedValue = String(standardHexPadding + convertedValue).suffix(12).description
            }
        }
        // Reverse every 2 chars and uppercase
        let hexPairs = stride(from: 0, to: convertedValue.count, by: 2).map {
            (i) -> String in
            let start = convertedValue.index(convertedValue.startIndex, offsetBy: i)
            let end = convertedValue.index(start, offsetBy: 2, limitedBy: convertedValue.endIndex) ?? convertedValue.endIndex
            return String(convertedValue[start..<end])
        }
        return hexPairs.reversed().joined().uppercased()
    }

    static func getProtocolTypeFromScaleType(scaleType: ScaleSourceType, sku: String) -> ProtocolType {
      if scaleType == .btWifiR4 || sku == "0412" {
        return .R4
      } else if scaleType == .bluetooth {
        return .A3
      } else {
        return .A6
      }
    }

}
