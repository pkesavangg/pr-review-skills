//
//  DeviceStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import Combine
import Foundation
import SwiftUI

// MARK: - Devices Store
/// A store to manage scale settings and actions, including details for a selected scale.
@MainActor
class DeviceStore: ObservableObject {
    
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var deviceService: PairedDeviceServiceProtocol
    @Injector private var bluetoothService: BluetoothServiceProtocol
    @Injector private var logger: LoggerServiceProtocol
    @Injector private var permissionsService: PermissionsServiceProtocol
    
    @Published var scales: [DeviceSnapshot] = []
    @Published var addDeviceForm = AddDeviceForm()    
    
    private let tag = "DeviceStore"
    
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
            presentedView: AnyView(ModelNumberHelpModalView {
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
    
    // MARK: - Helper Methods
    func resetForm() {
        self.addDeviceForm.reset()
        self.addDeviceForm = AddDeviceForm()
    }
    
    func updateSetupInProgressStatus(_ isInProgress: Bool) {
        logger.log(level: .info, tag: tag, message: "Scale setup in-progress status updated to \(isInProgress)")
        self.bluetoothService.isSetupInProgress = isInProgress
        if !isInProgress {
            clearScaleDiscoveredInfo()
        }
    }
    
    func clearScaleDiscoveredInfo() {
        bluetoothService.resumeSmartScan(clearOnlyPairing: false)
        Task {
            bluetoothService.syncDevices([])
        }
    }
    
    func determineConnectionStatus(for scale: DeviceSnapshot) -> DeviceConnectionStatus {
        let st = DeviceTypeHelper.determineDeviceModelType(sku: scale.sku, scaleType: scale.bathScale?.scaleType, deviceType: scale.deviceType)
        if st == .appsync { return .noStatus }

        let isBluetoothOn = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        if !isBluetoothOn { return .notConnected }

        guard scale.isConnected else { return .notConnected }

        if st == .bluetoothR4 {
            let wifiOk = scale.isWifiConfigured
            let weightOnly = !(scale.r4ScalePreference?.shouldMeasureImpedance ?? true)
            if !wifiOk && !weightOnly { return .setupIncomplete }
        }

        return .connected
    }
    
    private func setupSubscriptions() {
        // Subscribe to scale service updates
        deviceService.scalesPublisher
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
