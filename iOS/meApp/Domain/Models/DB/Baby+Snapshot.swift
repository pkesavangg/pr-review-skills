//
//  Baby+Snapshot.swift
//  meApp
//
//  Snapshot + request projections for the SwiftData `Baby` model (MOB-386).
//

import Foundation

extension Baby {
    /// Produces a flat, `Sendable` `BabySnapshot`. Call on the main actor before any `await`.
    func toSnapshot() -> BabySnapshot {
        BabySnapshot(
            id: id,
            accountId: accountId,
            name: name,
            deviceId: deviceId,
            isSynced: isSynced,
            birthday: birthday,
            biologicalSex: biologicalSex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
    }

    /// Builds the wire `BabyRequest` for create/update, converting local units to the API's
    /// decigrams / millimeters / `YYYY-MM-DD`.
    func toRequest() -> BabyRequest {
        BabyRequest(
            name: name,
            birthday: birthday,
            biologicalSex: biologicalSex,
            birthLengthInches: birthLengthInches,
            birthWeightLbs: birthWeightLbs,
            birthWeightOz: birthWeightOz
        )
    }
}
