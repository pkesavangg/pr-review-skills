//
//  ScaleStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import Foundation
import SwiftUI
import Combine

// MARK: - Scales Store
/// A store to manage scale settings and  actions.
@MainActor
class ScaleStore: ObservableObject {
    @Published var scales: [Device] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var addScaleForm = AddScaleForm()
    
    @Injector var scaleService: ScaleService
    private var cancellables = Set<AnyCancellable>()
    
    init() {
        wireForm()
        Task { await fetchScales() }
    }
    
    private func wireForm() {
        addScaleForm.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }
    
    func resetForm() {
        addScaleForm = AddScaleForm()
        wireForm()
    }
    
    func getError() -> String? {
        addScaleForm.getError(for: .modelNumber)
    }
    
    func fetchScales() async -> [Device] {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        
        do {
            let devices = try await scaleService.getDevices()
            self.scales = devices
            return devices
        } catch {
            self.errorMessage = error.localizedDescription
            self.scales = []
            return []
        }
    }
    
    func refreshScales() async {
        _ = await fetchScales()
    }
    
    func removeScale(_ scale: Device) async throws {
        try await scaleService.deleteDevice(scale.id, showToast: true)
        _ = await fetchScales()
    }
}
