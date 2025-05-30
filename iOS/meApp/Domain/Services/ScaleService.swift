// ScaleService.swift
// Business logic service for scale management, sync, and device orchestration.
// Calls methods from remote (ScaleAPIRepository) and local (ScaleLocalRepository) repositories.
// Follows protocol-driven architecture.

import Foundation
import SwiftData

final class ScaleService {
    private let repository: ScaleAPIRepository
    private let modelContext: ModelContext

    init(repository: ScaleAPIRepository, modelContext: ModelContext) {
        self.repository = repository
        self.modelContext = modelContext
    }

    // MARK: - Public API

    /// List scales (fetch from API, save to local SwiftData)
    func listScales() async -> [ScaleDTO] {
        let dtos = (try? await repository.listScales()) ?? []
        saveScalesToLocal(dtos)
        return dtos
    }

    /// Create a new scale (API, then save to local)
    func createScale(_ scale: ScaleDTO) async -> ScaleDTO? {
        guard let dto = try? await repository.createScale(scale) else { return nil }
        saveScalesToLocal([dto])
        return dto
    }

    /// Edit a scale (API, then update local)
    func editScale(_ scaleId: String, properties: [String: Any]) async -> ScaleDTO? {
        guard let dto = try? await repository.editScale(scaleId, properties: properties) else { return nil }
        saveScalesToLocal([dto])
        return dto
    }

    /// Delete a scale (API, then delete local)
    func deleteScale(_ scaleId: String) async {
        try? await repository.deleteScale(scaleId)
        deleteScaleFromLocal(scaleId)
    }

    /// Patch scale meta (API, then update local if needed)
    func patchScaleMeta(_ scaleId: String, metaData: ScaleMetaDataDTO) async {
        try? await repository.patchScaleMeta(scaleId, metaData: metaData)
        // Optionally update local BathScale with new metaData
    }

    /// Patch scale preference (API, then update local if needed)
    func patchScalePreference(_ preference: R4ScalePreferenceDTO) async {
        try? await repository.patchScalePreference(preference)
        // Optionally update local BathScale with new preference
    }

    // MARK: - Local Storage Helpers

    /// Save or update BathScale objects in SwiftData
    private func saveScalesToLocal(_ dtos: [ScaleDTO]) {
        for dto in dtos {
            // Use the convenience initializer for mapping
            let device = Device(from: dto)
            modelContext.insert(device)
        }
        try? modelContext.save()
    }

    /// Delete BathScale from SwiftData by id
    private func deleteScaleFromLocal(_ scaleId: String) {
        let fetchRequest = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let scale = try? modelContext.fetch(fetchRequest).first {
            modelContext.delete(scale)
            try? modelContext.save()
        }
    }
}
