//
//  BabyRequest.swift
//  meApp
//
//  Request body for the Baby Profile CRUD endpoints (MOB-386):
//  `POST /v3/baby/` and `PUT /v3/baby/:babyId`.
//

import Foundation

/// Wire shape for creating/updating a baby profile.
///
/// Per the Baby App audit, only the fields the existing Baby app actually uses are sent:
/// `name` (required) plus optional `birthdate`, `sex`, `birthWeightDecigrams`,
/// `birthLengthMillimeters`. Excluded spec fields (`dueDate`, `isBorn`, …) are intentionally
/// omitted. Optional fields left `nil` are dropped by `JSONEncoder`.
struct BabyRequest: Codable, Equatable {
    /// Baby's display name. Required.
    let name: String
    /// Date of birth as `YYYY-MM-DD`.
    let birthdate: String?
    /// `male`, `female`, or `private`.
    let sex: String?
    /// Birth weight in decigrams (1 kg = 10000 dg).
    let birthWeightDecigrams: Int?
    /// Birth length in millimeters.
    let birthLengthMillimeters: Int?
}

extension BabyRequest {
    private static let birthdateFormat = "yyyy-MM-dd"

    /// Builds a request from the app's local-unit baby fields, converting to the wire units
    /// (decigrams / millimeters / `YYYY-MM-DD`).
    /// - Parameters:
    ///   - name: Baby's display name.
    ///   - birthday: Local `Date` of birth (formatted to `YYYY-MM-DD`).
    ///   - biologicalSex: `male`/`female`/`private`.
    ///   - birthLengthInches: Birth length in inches.
    ///   - birthWeightLbs: Birth weight pounds component.
    ///   - birthWeightOz: Birth weight ounces component.
    init(
        name: String,
        birthday: Date?,
        biologicalSex: String?,
        birthLengthInches: Double?,
        birthWeightLbs: Double?,
        birthWeightOz: Double?
    ) {
        let birthdate = birthday.map { DateTimeTools.formatter(Self.birthdateFormat).string(from: $0) }

        let decigrams: Int?
        if birthWeightLbs != nil || birthWeightOz != nil {
            decigrams = ConversionTools.convertBabyLbsOzToDecigrams(
                lbs: Int(birthWeightLbs ?? 0), oz: birthWeightOz ?? 0
            )
        } else {
            decigrams = nil
        }

        let millimeters = birthLengthInches.map { ConversionTools.convertBabyInchesToMm($0) }

        // The API expects lowercase `male`/`female`/`private`, but the picker stores a
        // capitalized display value ("Male"/"Female") — normalize so the server accepts it.
        self.init(
            name: name,
            birthdate: birthdate,
            sex: biologicalSex?.lowercased(),
            birthWeightDecigrams: decigrams,
            birthLengthMillimeters: millimeters
        )
    }
}
