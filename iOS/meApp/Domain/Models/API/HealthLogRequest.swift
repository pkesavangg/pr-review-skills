//
//  HealthLogRequest.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//

import Foundation

/// Encodable payload for the `/integrations/health/log` endpoint.
///
/// Includes all measurement fields (even when `nil`) so that the backend receives
/// an explicit `null` value, and preventing 400 errors.
struct HealthLogRequest: Encodable {
    let type: String
    let sentAt: String
    let timestamp: String
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let data: [String: AnyCodable]

    private enum CodingKeys: String, CodingKey {
        case type, sentAt, timestamp, weight, bodyFat, muscleMass, water, bmi, data
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(type, forKey: .type)
        try container.encode(sentAt, forKey: .sentAt)
        try container.encode(timestamp, forKey: .timestamp)

        // Encode optionals explicitly – encode `null` when value is nil
        if let weight = weight {
            try container.encode(weight, forKey: .weight)
        } else {
            try container.encodeNil(forKey: .weight)
        }
        if let bodyFat = bodyFat {
            try container.encode(bodyFat, forKey: .bodyFat)
        } else {
            try container.encodeNil(forKey: .bodyFat)
        }
        if let muscleMass = muscleMass {
            try container.encode(muscleMass, forKey: .muscleMass)
        } else {
            try container.encodeNil(forKey: .muscleMass)
        }
        if let water = water {
            try container.encode(water, forKey: .water)
        } else {
            try container.encodeNil(forKey: .water)
        }
        if let bmi = bmi {
            try container.encode(bmi, forKey: .bmi)
        } else {
            try container.encodeNil(forKey: .bmi)
        }

        try container.encode(data, forKey: .data)
    }
} 
