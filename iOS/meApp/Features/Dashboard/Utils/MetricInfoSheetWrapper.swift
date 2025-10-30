//
//  MetricInfoSheetWrapper.swift
//  meApp
//
//  Wrapper to safely extract DTO from Entry before displaying ScaleMetricsView
//

import SwiftUI
import SwiftData

/// Wrapper that safely extracts DTO from Entry before displaying ScaleMetricsView.
/// This ensures SwiftData properties are accessed on main actor within a ModelContext.
struct MetricInfoSheetWrapper: View {
    let entry: Entry
    let selectedMetric: BodyMetric
    @State private var entryDTO: BathScaleOperationDTO?
    
    var body: some View {
        Group {
            if let dto = entryDTO {
                ScaleMetricsView(entryDTO: dto, selectedMetric: selectedMetric)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            await extractDTO()
        }
    }
    
    private func extractDTO() async {
        // Extract DTO on main actor without modifying the Entry's context
        let dto = await MainActor.run {
            let mainContext = PersistenceController.shared.context
            
            // Check if entry is already in a context
            if entry.modelContext == nil {
                // Insert temporarily to allow property access
                // Note: We won't save the context, so this won't persist
                // The Entry will be cleaned up when the view is dismissed
                mainContext.insert(entry)
            }
            
            // Access properties and build DTO directly (not using toOperationDTO to avoid double access)
            let scaleEntry = entry.scaleEntry
            let scaleEntryMetric = entry.scaleEntryMetric
            
            return BathScaleOperationDTO(
                accountId: entry.accountId,
                bmr: scaleEntryMetric?.bmr.map { Double($0) },
                bmi: scaleEntry?.bmi.map { Double($0) },
                bodyFat: scaleEntry?.bodyFat.map { Double($0) },
                boneMass: scaleEntryMetric?.boneMass.map { Double($0) },
                entryTimestamp: entry.entryTimestamp,
                impedance: scaleEntryMetric?.impedance.map { Double($0) },
                metabolicAge: scaleEntryMetric?.metabolicAge.map { Double($0) },
                muscleMass: scaleEntry?.muscleMass.map { Double($0) },
                operationType: entry.operationType,
                proteinPercent: scaleEntryMetric?.proteinPercent.map { Double($0) },
                pulse: scaleEntryMetric?.pulse.map { Double($0) },
                serverTimestamp: entry.serverTimestamp,
                skeletalMusclePercent: scaleEntryMetric?.skeletalMusclePercent.map { Double($0) },
                source: scaleEntry?.source,
                subcutaneousFatPercent: scaleEntryMetric?.subcutaneousFatPercent.map { Double($0) },
                unit: scaleEntryMetric?.unit,
                visceralFatLevel: scaleEntryMetric?.visceralFatLevel.map { Double($0) },
                water: scaleEntry?.water.map { Double($0) },
                weight: scaleEntry?.weight.map { Double($0) }
            )
        }
        await MainActor.run {
            self.entryDTO = dto
        }
    }
}

