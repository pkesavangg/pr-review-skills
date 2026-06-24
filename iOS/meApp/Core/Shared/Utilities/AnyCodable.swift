//
//  AnyCodable.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 29/05/25.
//

import Foundation

// MARK: - AnyCodable
/// AnyCodable is a type-erased wrapper that allows any value to be encoded and decoded as a single value.
public struct AnyCodable: Codable {
    public let value: Any

    public init(_ value: Any) {
        self.value = value
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let intVal = try? container.decode(Int.self) {
            value = intVal
        } else if let doubleVal = try? container.decode(Double.self) {
            value = doubleVal
        } else if let boolVal = try? container.decode(Bool.self) {
            value = boolVal
        } else if let stringVal = try? container.decode(String.self) {
            value = stringVal
        } else if let arrayVal = try? container.decode([AnyCodable].self) {
            value = arrayVal.map { $0.value }
        } else if let dictVal = try? container.decode([String: AnyCodable].self) {
            value = dictVal.mapValues { $0.value }
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported value")
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let intVal as Int:
            try container.encode(intVal)
        case let doubleVal as Double:
            try container.encode(doubleVal)
        case let boolVal as Bool:
            try container.encode(boolVal)
        case let stringVal as String:
            try container.encode(stringVal)
        case let arrayVal as [Any]:
            let encodableArray = arrayVal.map { AnyCodable($0) }
            try container.encode(encodableArray)
        case let dictVal as [String: Any]:
            let encodableDict = dictVal.mapValues { AnyCodable($0) }
            try container.encode(encodableDict)
        default:
            throw EncodingError.invalidValue(value, EncodingError.Context(codingPath: container.codingPath, debugDescription: "Unsupported value"))
        }
    }
}

// MARK: - USAGE GUIDE
///
/// Wrap any value for encoding:
/// ```swift
/// let wrapped = AnyCodable(["foo": 123, "bar": true])
/// let data = try JSONEncoder().encode(wrapped)
/// ```
///
/// Decode back:
/// ```swift
/// let decoded = try JSONDecoder().decode(AnyCodable.self, from: data)
/// print(decoded.value)
/// ```
///
/// Use with AppLogger:
/// ```swift
/// AppLogger.shared.log(level: .info, tag: "Test", message: "msg", data: AnyCodable(["foo": 1]))
/// ```
