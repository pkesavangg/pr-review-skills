//
//  BpmSessionOrderingTests.swift
//  meAppTests
//
//  MOB-1848: a multi-reading blood-pressure session showed the FIRST reading on the
//  new-reading card instead of the latest. The monitor delivers a session oldest-first,
//  so the batch path's `.first` was the oldest reading.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BpmSessionOrderingTests {

    private func makeMeasurement(
        systolic: Int,
        diastolic: Int,
        pulse: Int,
        secondsFromBase: TimeInterval
    ) -> BpmMeasurement {
        BpmMeasurement(
            systolic: systolic,
            diastolic: diastolic,
            pulse: pulse,
            timestamp: Date(timeIntervalSince1970: 1_800_000_000 + secondsFromBase)
        )
    }

    /// The exact session from the MA-T4094 repro: 111/77-65, then 118/80-70, then 109/72-63.
    /// The card must show 109/72-63; the other two are the historical readings.
    @Test("session delivered oldest-first is ordered latest-first")
    func oldestFirstBatchIsReordered() {
        let session = [
            makeMeasurement(systolic: 111, diastolic: 77, pulse: 65, secondsFromBase: 0),
            makeMeasurement(systolic: 118, diastolic: 80, pulse: 70, secondsFromBase: 60),
            makeMeasurement(systolic: 109, diastolic: 72, pulse: 63, secondsFromBase: 120)
        ]

        let ordered = BluetoothService.sessionOrderedLatestFirst(session)

        #expect(ordered.map(\.systolic) == [109, 118, 111])
        #expect(ordered.first?.diastolic == 72)
        #expect(ordered.first?.pulse == 63)
    }

    @Test("a batch already newest-first keeps the newest reading staged")
    func newestFirstBatchIsPreserved() {
        let session = [
            makeMeasurement(systolic: 109, diastolic: 72, pulse: 63, secondsFromBase: 120),
            makeMeasurement(systolic: 118, diastolic: 80, pulse: 70, secondsFromBase: 60),
            makeMeasurement(systolic: 111, diastolic: 77, pulse: 65, secondsFromBase: 0)
        ]

        let ordered = BluetoothService.sessionOrderedLatestFirst(session)

        #expect(ordered.map(\.systolic) == [109, 118, 111])
    }

    @Test("readings sharing a timestamp fall back to the later batch position")
    func identicalTimestampsUseBatchPosition() {
        let session = [
            makeMeasurement(systolic: 111, diastolic: 77, pulse: 65, secondsFromBase: 0),
            makeMeasurement(systolic: 118, diastolic: 80, pulse: 70, secondsFromBase: 0),
            makeMeasurement(systolic: 109, diastolic: 72, pulse: 63, secondsFromBase: 0)
        ]

        let ordered = BluetoothService.sessionOrderedLatestFirst(session)

        #expect(ordered.map(\.systolic) == [109, 118, 111])
    }

    @Test("single-reading and empty sessions are unchanged")
    func degenerateSessions() {
        let single = [makeMeasurement(systolic: 120, diastolic: 80, pulse: 70, secondsFromBase: 0)]

        #expect(BluetoothService.sessionOrderedLatestFirst(single) == single)
        #expect(BluetoothService.sessionOrderedLatestFirst([]).isEmpty)
    }

    /// The banner count is `batchCount - 1`: 3 session readings ⇒ "2 more readings received
    /// for this session" (MA-T4094 step 3).
    @Test("session banner copy reports total readings minus the one displayed")
    func bannerCountExcludesDisplayedReading() {
        #expect(DashboardStrings.moreReadingsReceived(2) == "2 more readings received for this session")
        #expect(DashboardStrings.moreReadingsReceived(1) == "1 more reading received for this session")
    }
}
