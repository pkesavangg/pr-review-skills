//import Foundation
//import SwiftUI
//import Combine
//
///// Manages all scale data operations and API synchronization
//@MainActor
//class ScaleDataManager: ObservableObject {
//
//    // MARK: - Dependencies
//    @Injector private var scaleService: ScaleService
//    @Injector private var logger: LoggerService
//    
//
//    // MARK: - Published Properties
//    @Published var state: ScaleDataState
//
//    // MARK: - Private Properties
//    private var cancellables = Set<AnyCancellable>()
//
//    // MARK: - Initialization
//    init(initialState: ScaleDataState = ScaleDataState()) {
//        self.state = initialState
//        setupFormBindings()
//    }
//
//    // MARK: - Form Management
//    private func setupFormBindings() {
//        state.addScaleForm.objectWillChange
//            .sink { [weak self] _ in self?.objectWillChange.send() }
//            .store(in: &cancellables)
//    }
//
//    func resetForm() {
//        state.addScaleForm = AddScaleForm()
//        setupFormBindings()
//    }
//
//    func getError() -> String? {
//        state.addScaleForm.getError(for: .modelNumber)
//    }
//
//    // MARK: - Scale CRUD Operations
//    func fetchScales() async {
//        do {
//            let devices = try await scaleService.getDevices()
//            state.scales = devices
//        } catch {
//            logger.log(level: .error, tag: "ScaleDataManager", message: "Failed to fetch scales: \(error)")
//            state.scales = []
//        }
//    }
//
//    func deleteScale(scaleId: String) async throws {
//        try await scaleService.deleteDevice(scaleId, showToast: true)
//        await scaleService.pushLocalChangesToServer()
//        await fetchScales()
//    }
//
//    func saveScaleName(_ newName: String, for scale: Device) async throws {
//        let properties: [String: Any] = ["nickname": newName]
//        _ = try await scaleService.editDevice(scale.id, properties: properties)
//        await scaleService.pushLocalChangesToServer()
//        await fetchScales()
//    }
//
//    func saveNickname(_ nickname: String, for scale: Device) async throws {
//        let properties: [String: Any] = ["nickname": nickname]
//        _ = try await scaleService.editDevice(scale.id, properties: properties)
//        await scaleService.pushLocalChangesToServer()
//        await fetchScales()
//    }
//
//    // MARK: - WiFi Operations
//    func setPasswordTouched() {
//        state.wifiPasswordValidationForm.password.markAsDirty()
//        objectWillChange.send()
//    }
//
//    var passwordError: String? {
//        state.wifiPasswordValidationForm.getError(for: state.wifiPasswordValidationForm.password)
//    }
//
//    var isFormValid: Bool {
//        state.wifiPasswordValidationForm.isValid
//    }
//
//    // MARK: - Browser Operations
//    func productGuideURL(for sku: String) -> URL {
//        guard !sku.isEmpty else {
//            return AppConstants.LegalURLs.notFound
//        }
//        
//        let url = AppConstants.LegalURLs.serviceBase.appendingPathComponent(sku)
//        return url
//    }
//
//    func openProductGuide(for sku: String) {
//        let url = productGuideURL(for: sku)
//        state.browserURL = url
//    }
//} 
