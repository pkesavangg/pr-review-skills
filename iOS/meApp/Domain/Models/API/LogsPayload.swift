//
//  LogsPayload.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 21/07/25.
//
import Foundation

/// Request structure for sending logs to the server
struct LogsPayload: Codable {
    let version: String
    let logs: [LogEntryPayload]
}

/// Individual log entry for API submission
struct LogEntryPayload: Codable {
    let time: String
    let data: LogEntryData
}

/// Log entry data can be either a string or an array with mixed types
enum LogEntryData: Codable {
    case string(String)
    case array(String, [Any]?)
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let str):
            try container.encode(str)
        case .array(let message, let additionalData):
            var arrayContainer = encoder.unkeyedContainer()
            try arrayContainer.encode(message)
            if let data = additionalData, !data.isEmpty {
                try arrayContainer.encode(AnyCodable(data))
            }
        }
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let str = try? container.decode(String.self) {
            self = .string(str)
        } else {
            // For decoding, we'll just try to decode as string since we mainly encode
            throw DecodingError.typeMismatch(LogEntryData.self, DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "Expected string or array"))
        }
    }
} 
