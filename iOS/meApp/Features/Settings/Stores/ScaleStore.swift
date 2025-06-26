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
    // List State
    @Published var scales: [Device] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var addScaleForm = AddScaleForm()
    
    // Selected Scale State
    @Published var scale: Device? = nil
    @Published var firmwareVersion: String? = nil
    @Published var macAddress: String? = nil
    @Published var connectedWifiSSID: String? = nil
    @Published var deviceInfo: DeviceMetaData? = nil
    @Published var isBluetoothScale: Bool = false
    @Published var isDeviceConnected: Bool = false
    @Published var showErrorToast: ToastModel? = nil
    @Published var showNicknameAlert: Bool = false
    @Published var nicknameInput: String = ""
    
    // In-App Browser State
    @Published var showTermsBrowser: Bool = false
    @Published var browserURL: URL? = nil
    
    // Settings/detail values for UI (replace with computed or fetched values later)
    @Published var modeValue: ScaleModes = .weightOnly 
    @Published var displayMetricsValue: String = "" // TODO: Replace with actual display metrics
    @Published var usersValue: String = "Kristin" // TODO: Replace with actual users
    @Published var scaleNameValue: String = "AccuCheck Verve..." // TODO: Replace with actual scale name
    @Published var bluetoothValue: String = "Connected" // TODO: Replace with actual BT status
    @Published var wifiValue: String = "greatergoods1" // TODO: Replace with actual Wi-Fi SSID
    @Published var wifiMacAddressValue: String = "" // TODO: Replace with actual Wi-Fi MAC address
    @Published var scaleTypeValue: String = "Bluetooth/Wi-Fi" // TODO: Replace with actual scale type
    @Published var skuValue: String = "0412" // TODO: Replace with actual SKU
    @Published var datePairedValue: String = "June 2, 2025" // TODO: Replace with actual date paired
    
    private var cancellables = Set<AnyCancellable>()
    private let legalURLs = AppConstants.LegalURLs.self
    
    // MARK: - In-App Browser Presentation Binding
    var isBrowserPresented: Binding<Bool> {
        Binding(
            get: { self.showTermsBrowser },
            set: { newValue in
                if !newValue {
                    self.showTermsBrowser = false
                    self.browserURL = nil
                }
            }
        )
    }
    var presentingBrowserURL: URL {
        browserURL ?? legalURLs.greaterGoodsWebsite
    }
    
    // MARK: - Initialization
    @Injector var scaleService: ScaleService
    @Injector var notificationService: NotificationHelperService
    let alertLang = AlertStrings.self
    
    init() {
        wireForm()
        fetchScales()
    }
    
    private func wireForm() {
        addScaleForm.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }
    
    func resetForm() {
        addScaleForm = AddScaleForm()
        cancellables.removeAll()
        wireForm()
    }
    
    func getError() -> String? {
        addScaleForm.getError(for: .modelNumber)
    }
    
    // MARK: - List & CRUD
    func fetchScales() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                let devices = try await scaleService.getDevices()
                await MainActor.run {
                    self.scales = devices
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.scales = []
                    self.isLoading = false
                }
            }
        }
    }
    
    // MARK: - Scale Detail Actions
    func loadScale(_ scale: Device) async {
        self.scale = scale
        self.isBluetoothScale = scale.deviceType == "bluetooth"
        self.isDeviceConnected = scale.isConnected ?? false
        await getDeviceInfo()
        await getConnectedWifiSSID()
    }
    
    func getDeviceInfo() async {
        guard let scale = scale else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            if let device = try await scaleService.getDevices().first(where: { $0.id == scale.id }) {
                await MainActor.run {
                    self.deviceInfo = device.metaData
                    self.macAddress = device.mac
                    self.firmwareVersion = device.metaData?.firmwareRevision
                }
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    func getConnectedWifiSSID() async {
        connectedWifiSSID = scale?.wifiMac
    }
    
    func changeNickname() {
        showNicknameAlert = true
        nicknameInput = scale?.nickname ?? ""
    }
    
    func saveNickname() async {
        guard let scale = scale else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let properties: [String: Any] = ["nickname": nicknameInput]
            _ = try await scaleService.editDevice(scale.id, properties: properties)
            notificationService.showToast(ToastModel(title: "Saved", message: "Nickname updated"))
            self.scale?.nickname = nicknameInput
        } catch {
            errorMessage = error.localizedDescription
        }
        showNicknameAlert = false
    }
    
    func deleteScale(scaleId: String, onSuccess: @escaping () -> Void) async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await scaleService.deleteDevice(scaleId, showToast: true)
            await MainActor.run {
                notificationService.showToast(ToastModel(title: "Deleted", message: "Scale deleted"))
                if self.scale?.id == scaleId {
                    self.scale = nil
                }
                fetchScales()
                onSuccess()
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    func handleScaleDelete(scaleId: String, onSuccess: @escaping () -> Void) {
        let alert = AlertModel(
            title: alertLang.DeleteScaleAlert.title,
            message: alertLang.DeleteScaleAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.DeleteScaleAlert.deleteButton, type: .primary) { _ in
                    Task {
                        await self.deleteScale(scaleId: scaleId, onSuccess: onSuccess)
                    }
                },
                AlertButtonModel(title: alertLang.DeleteScaleAlert.cancelButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func displayMetricsTapped() {
        // TODO: Implement displayMetricsTapped action
    }
    func usersTapped() {
        // TODO: Implement usersTapped action
    }
    func scaleNameTapped() {
        // TODO: Implement scaleNameTapped action
    }
    func bluetoothTapped() {
        // TODO: Implement bluetoothTapped action
    }
    func wifiTapped() {
        // TODO: Implement wifiTapped action
    }
    func wifiMacAddressTapped() {
        // TODO: Implement wifiMacAddressTapped action
    }
    func scaleTypeTapped() {
        // TODO: Implement scaleTypeTapped action
    }
    func handleSave() {
        // TODO: Implement save button action
    }
    
    func handleHelp() {
        // TODO: Implement help button action
    }
    
    // MARK: - Product Guide URL helper & Browser Presentation
    func productGuideURL(for sku: String) -> URL {
        guard !sku.isEmpty else { return legalURLs.notFound }
        return (AppConstants.LegalURLs.serviceBase.appendingPathComponent(sku)) // Use type-safe base
    }
    func openProductGuide(for sku: String) {
        browserURL = productGuideURL(for: sku)
        showTermsBrowser = true
    }
    
    func openBIAModel(){
        notificationService.showModal(ModalData(
            presentedView: AnyView(BIAInfoModalView(){
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
}
