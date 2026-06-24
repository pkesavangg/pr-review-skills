//
//  BpmReadingDisplayData.swift
//  meApp
//
//  Display-ready blood pressure reading data used by BPM cards and sheets.
//

import Foundation

struct BpmReadingDisplayData: Identifiable {
    let id: UUID
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let timestamp: String
    let classification: AhaPressureClass

    init?(entry: Entry) {
        guard let systolic = entry.scaleEntry?.systolic,
              let diastolic = entry.scaleEntry?.diastolic else {
            return nil
        }

        self.id = entry.id
        self.systolic = systolic
        self.diastolic = diastolic
        self.pulse = entry.scaleEntryMetric?.pulse ?? 0
        self.timestamp = entry.entryTimestamp
        self.classification = AhaPressureClass.classify(systolic: systolic, diastolic: diastolic)
    }

    init?(summary: BathScaleWeightSummary) {
        guard let systolic = summary.systolic,
              let diastolic = summary.diastolic else {
            return nil
        }

        self.id = summary.id
        self.systolic = Int(round(systolic))
        self.diastolic = Int(round(diastolic))
        self.pulse = Int(round(summary.pulse ?? 0))
        self.timestamp = summary.entryTimestamp
        self.classification = AhaPressureClass.classify(
            systolic: Int(round(systolic)),
            diastolic: Int(round(diastolic))
        )
    }

    var formattedDate: String {
        guard let date = DateTimeTools.parse(timestamp) else { return timestamp }
        return DateTimeTools.formatter("MMM d, yyyy").string(from: date)
    }
}
