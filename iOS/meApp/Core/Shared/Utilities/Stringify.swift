import Foundation

///// Converts any value to a pretty-printed JSON string if possible, otherwise falls back to String(describing:).
///// Uses AnyCodable for best-effort serialization.
public func stringify(_ value: Any) -> String {
    let encoder = JSONEncoder()
    encoder.outputFormatting = .prettyPrinted
    if let encodableValue = wrapInAnyCodable(value),
       let data = try? encoder.encode(encodableValue),
       let jsonString = String(data: data, encoding: .utf8) {
        return jsonString
    }
    return String(describing: value)
} 

func wrapInAnyCodable(_ value: Any) -> AnyCodable? {
    return AnyCodable(value)
}
