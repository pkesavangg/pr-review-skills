//
//  BabyResponse.swift
//  meApp
//
//  Response shape for the Baby Profile endpoints (MOB-386):
//  `POST /v3/baby/`, `GET /v3/baby/`, `PUT /v3/baby/:babyId`.
//

import Foundation

/// Wire shape returned by the baby profile endpoints. Scoped to the fields the existing
/// Baby app consumes (`id`, `name`, `birthdate`, `sex`, `birthWeightDecigrams`,
/// `birthLengthMillimeters`); unused spec fields are ignored even if present.
struct BabyResponse: Codable, Equatable {
    let id: String
    let name: String
    let birthdate: String?
    let sex: String?
    let birthWeightDecigrams: Int?
    let birthLengthMillimeters: Int?
}

extension BabyResponse {
    private static let birthdateFormat = "yyyy-MM-dd"

    /// Parses `birthdate` (`YYYY-MM-DD`) into a `Date`.
    var birthdayDate: Date? {
        birthdate.flatMap { DateTimeTools.formatter(Self.birthdateFormat).date(from: $0) }
    }

    /// Birth length in inches, converted from the wire's millimeters.
    var birthLengthInchesValue: Double? {
        birthLengthMillimeters.map { ConversionTools.convertBabyMmToInches($0) }
    }

    /// Birth weight split into (pounds, ounces), converted from the wire's decigrams.
    var birthWeightLbsOz: (lbs: Double, oz: Double)? {
        guard let decigrams = birthWeightDecigrams else { return nil }
        let result = ConversionTools.convertBabyDecigramsToLbsOz(decigrams)
        return (Double(result.lbs), result.oz)
    }

    /// Builds a local SwiftData `Baby` from this response, marked synced.
    /// - Parameters:
    ///   - accountId: The owning account.
    ///   - deviceId: Optional linked baby-scale device id (preserved across merges by the caller).
    func toBaby(accountId: String, deviceId: String? = nil) -> Baby {
        let weight = birthWeightLbsOz
        return Baby(
            id: id,
            accountId: accountId,
            name: name,
            deviceId: deviceId,
            isSynced: true,
            isServerCreated: true,
            birthday: birthdayDate,
            biologicalSex: sex,
            birthLengthInches: birthLengthInchesValue,
            birthWeightLbs: weight?.lbs,
            birthWeightOz: weight?.oz
        )
    }
}
