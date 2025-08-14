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
/// A store to manage scale settings and actions, including details for a selected scale.
@MainActor
class ScaleStore: ObservableObject {
    
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var scaleService: ScaleService
    @Injector private var bluetoothService: BluetoothService
    @Injector private var logger: LoggerService
    
    @Published var scales: [Device] = []
    @Published var addScaleForm = AddScaleForm()    
    
    private let tag = "ScaleStore"
    
    // MARK: - Initialization
    init() {
        setupSubscriptions()
    }
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    
    // MARK: - Constants
    private let legalURLs = AppConstants.LegalURLs.self
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self

    
    func handleDuplicateScale(sku: String, onPair: @escaping () -> Void) {
        let lang = alertLang.DeviceAlreadyPairedAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message(sku),
            buttons: [
                AlertButtonModel(title: lang.returnButton, type: .secondary) { _ in },
                AlertButtonModel(title: lang.pairButton, type: .primary) { _ in
                    onPair()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func openHelp() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(ModelNumberHelpModalView(){
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
    
    // MARK: - Helper Methods
    func resetForm() {
        self.addScaleForm.reset()
        self.addScaleForm = AddScaleForm()
    }
    
    func updateSetupInProgressStatus(_ isInProgress: Bool) {
        self.bluetoothService.isSetupInProgress = isInProgress
    }
    
    func clearScaleDiscoveredInfo() {
        updateSetupInProgressStatus(false)
        bluetoothService.resumeSmartScan(clearOnlyPairing: false)
        bluetoothService.clearScaleDiscoveredInfo()
        Task {
            bluetoothService.syncDevices([])
        }
    }
    
    func determineConnectionStatus(for scale: Device) -> ScaleConnectionStatus {
        let st = ScaleTypeHelper.determineScaleType(for: scale)
        if st == .appsync { return .noStatus }
        if st == .bluetoothR4 && scale.isConnected == true {
            let wifiOk = scale.isWifiConfigured == true
            let weightOnly = !(scale.r4ScalePreference?.shouldMeasureImpedance ?? true)
            if !wifiOk && !weightOnly { return .setupIncomplete }
        }
        return scale.isConnected == true ? .connected : .notConnected
    }
    
    private func setupSubscriptions() {
        // Subscribe to scale service updates
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                // Sort scales by createdAt timestamp in descending order (latest first)
                let sortedDevices = devices.sorted { device1, device2 in
                    guard let createdAt1 = device1.createdAt,
                          let createdAt2 = device2.createdAt else {
                        // If one or both createdAt values are nil, put nil values at the end
                        return device1.createdAt != nil && device2.createdAt == nil
                    }
                    
                    // Parse the date strings and compare
                    let date1 = DateTimeTools.parse(createdAt1) ?? Date.distantPast
                    let date2 = DateTimeTools.parse(createdAt2) ?? Date.distantPast
                    
                    return date1 > date2 // Descending order (latest first)
                }
                self?.scales = sortedDevices
            }
            .store(in: &cancellables)
    }
    
    deinit {
        cancellables.forEach { $0.cancel() }
    }
}
