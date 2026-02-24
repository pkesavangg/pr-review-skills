//
//  MetricInfoSheetWrapper.swift
//  meApp
//
//  Wrapper to safely extract DTO from Entry before displaying ScaleMetricsView
//

import SwiftData
import SwiftUI

/// Wrapper that safely extracts DTO from Entry before displaying ScaleMetricsView.
/// This ensures SwiftData properties are accessed on main actor within a ModelContext.
struct MetricInfoSheetWrapper: View {
    let entry: Entry
    let selectedMetric: BodyMetric
    @ObservedObject var dashboardStore: DashboardStore
    @State private var entryDTO: BathScaleOperationDTO?
    
    var body: some View {
        Group {
            if let dto = entryDTO {
                ScaleMetricsView(entryDTO: dto, selectedMetric: selectedMetric, dashboardStore: dashboardStore)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task(id: entry.id) {
            await extractDTO()
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            Task {
                await extractDTO()
            }
        }
        .onChange(of: dashboardStore.state.metrics.metrics) { _, _ in
            Task {
                await extractDTO()
            }
        }
    }
    
    private func extractDTO() async {
        // Try to refetch entry by ID if it exists in the database
        // This avoids inserting temporary entries without saving
        let repository = EntryRepository()
        
        do {
            // Attempt to refetch entry by ID if it exists in the database
            let refetched = try await repository.refetchEntriesOnMainActor(entryIds: [entry.id])
            
            // Extract DTO on main actor from refetched entry
            await MainActor.run {
                if let refetchedEntry = refetched[entry.id] {
                    // Entry exists in database, use refetched version
                    self.entryDTO = refetchedEntry.toOperationDTO()
                } else {
                    // Entry doesn't exist in database (temporary entry created for display)
                    // If entry has a modelContext, use it directly
                    if entry.modelContext != nil {
                        self.entryDTO = entry.toOperationDTO()
                    } else {
                        // Entry doesn't have a context - need to insert temporarily to access relationships
                        // Insert, extract DTO, then delete immediately to avoid memory leaks
                        let mainContext = PersistenceController.shared.context
                        mainContext.insert(entry)
                        
                        // Extract DTO while entry is in context
                        self.entryDTO = entry.toOperationDTO()
                        
                        // Delete immediately without saving to ensure cleanup
                        mainContext.delete(entry)
                        // Note: We don't save the context, so this deletion won't persist
                        // The entry will be properly deallocated when the view is dismissed
                    }
                }
            }
        } catch {
            // If refetch fails, check if entry has a context
            await MainActor.run {
                if entry.modelContext != nil {
                    // Entry has context, use it directly
                    self.entryDTO = entry.toOperationDTO()
                } else {
                    // Entry doesn't have context and refetch failed
                    // Insert temporarily, extract, then delete
                    let mainContext = PersistenceController.shared.context
                    mainContext.insert(entry)
                    self.entryDTO = entry.toOperationDTO()
                    mainContext.delete(entry)
                }
            }
        }
    }
}
