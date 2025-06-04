import Foundation
import HealthKit
import ggHealthKitPackage
import SwiftData

@MainActor
public final class HealthKitService: HealthKitServiceProtocol {
    
    private let healthKitService = ggHealthKitPackage.HealthKitService.shared
    private let integrationService: IntegrationServiceProtocol
    private let context: ModelContext
    
    // MARK: - Initialization
    
    init(integrationService: IntegrationServiceProtocol) {
        self.integrationService = integrationService
        self.context = PersistenceController.shared.context
    }
    
    // MARK: - HealthKitServiceProtocol
    
    public func integrate(turnOn: Bool) async throws -> Bool {
        if turnOn {
            do {
                _ = try await integrationService.checkIfIntegrationIsAlreadyUsed(type: .healthKit)
            } catch {
                throw HealthKitError.authorizationDenied
            }
        }
        
        if turnOn {
            do {
                try await healthKitService.requestAuthorization()
                try await syncAllData()
                try await integrationService.setStoredIntegrationData(IntegrationInfo(
                    type: .healthKit,
                    isIntegrated: true,
                    assignedTo: nil,
                    deIntegrated: nil
                ))
                return true
            } catch {
                return false
            }
        } else {
            try await clearHealthKit()
            try await integrationService.setStoredIntegrationData(IntegrationInfo(
                type: .healthKit,
                isIntegrated: false,
                assignedTo: nil,
                deIntegrated: nil
            ))
            return false
        }
    }
    
    public func syncAllData() async throws {
        try await healthKitService.checkAuthorizationStatus()
        let entries = try await fetchAllEntries()
        let healthKitData = buildHealthKitData(from: entries)
        try await healthKitService.saveData(healthKitData)
    }
    
    func syncNewData(entry: Entry) async throws {
        guard try await integrationService.getStoredIntegrationData()?.isIntegrated == true else {
            return
        }
        try await healthKitService.checkAuthorizationStatus()
        let healthKitData = buildHealthKitData(from: [entry])
        try await healthKitService.saveData(healthKitData)
    }
    
    public func checkAuthStatus() async throws {
        try await healthKitService.checkAuthorizationStatus()
    }
    
    func deleteEntry(entry: Entry) async throws -> Bool {
        let healthKitData = buildHealthKitData(from: [entry])
        try await healthKitService.deleteData(healthKitData)
        return true
    }
    
    public func clearHealthKit() async throws {
        if try await integrationService.getStoredIntegrationData()?.isIntegrated == true {
            let entries = try await fetchAllEntries()
            let healthKitData = buildHealthKitData(from: entries)
            try await healthKitService.deleteData(healthKitData)
        }
    }
    
    // MARK: - Private Methods
    
    private func fetchAllEntries() async throws -> [Entry] {
        let descriptor = FetchDescriptor<Entry>(
            sortBy: [SortDescriptor(\.entryTimestamp, order: .forward)]
        )
        return try context.fetch(descriptor)
    }
    
    private func buildHealthKitData(from entries: [Entry]) -> [HealthKitData] {
        var healthKitData: [HealthKitData] = []
        
        for entry in entries {
            guard let scaleEntry = entry.scaleEntry,
                  let timestamp = ISO8601DateFormatter().date(from: entry.entryTimestamp) else {
                continue
            }
            
            if let weight = scaleEntry.weight {
                healthKitData.append(HealthKitData(
                    type: .weight,
                    value: Double(weight),
                    timestamp: timestamp
                ))
            }
            
            if let bodyFat = scaleEntry.bodyFat {
                healthKitData.append(HealthKitData(
                    type: .bodyFat,
                    value: Double(bodyFat),
                    timestamp: timestamp
                ))
            }
            
            if let muscleMass = scaleEntry.muscleMass {
                healthKitData.append(HealthKitData(
                    type: .leanBodyMass,
                    value: Double(muscleMass),
                    timestamp: timestamp
                ))
            }
            
            if let bmi = scaleEntry.bmi {
                healthKitData.append(HealthKitData(
                    type: .bmi,
                    value: Double(bmi),
                    timestamp: timestamp
                ))
            }
        }
        
        return healthKitData
    }
}

