//
//  RefetchedEntryWrapper.swift
//  meApp
//
//  Created to safely refetch Entry objects on main actor before displaying
//

import SwiftUI
import SwiftData

/// Wrapper view that refetches an Entry by ID on the main actor before displaying ScaleMetricsView.
/// This ensures SwiftData properties can be safely accessed in MetricDetailView.
struct RefetchedEntryWrapper: View {
    let entryId: UUID
    let selectedMetric: BodyMetric
    @State private var entryDTO: BathScaleOperationDTO?
    @State private var isLoading = true
    
    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let dto = entryDTO {
                ScaleMetricsView(entryDTO: dto, selectedMetric: selectedMetric)
            } else {
                EmptyView()
            }
        }
        .task {
            await refetchEntry()
        }
    }
    
    private func refetchEntry() async {
        let repository = EntryRepository()
        do {
            let refetched = try await repository.refetchEntriesOnMainActor(entryIds: [entryId])
            // Extract DTO on main actor to avoid SwiftData access issues
            await MainActor.run {
                if let entry = refetched[entryId] {
                    self.entryDTO = entry.toOperationDTO()
                }
                self.isLoading = false
            }
        } catch {
            // Log the error for debugging and user awareness
            LoggerService.shared.log(
                level: .error,
                tag: "RefetchedEntryWrapper",
                message: "Failed to refetch entry with ID \(entryId): \(error.localizedDescription)"
            )
            await MainActor.run {
                self.isLoading = false
            }
        }
    }
}

