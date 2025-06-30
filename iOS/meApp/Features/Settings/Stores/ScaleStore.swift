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
    @Published var bluetoothValue: String = "Connected" // TODO: Replace with actual BT status
    @Published var wifiValue: String = "greatergoods1" // TODO: Replace with actual Wi-Fi SSID
    @Published var wifiMacAddressValue: String = "" // TODO: Replace with actual Wi-Fi MAC address
    @Published var scaleTypeValue: String = "Bluetooth/Wi-Fi" // TODO: Replace with actual scale type
    @Published var skuValue: String = "0412" // TODO: Replace with actual SKU
    @Published var datePairedValue: String = "June 2, 2025" // TODO: Replace with actual date paired
    
    // Display Metrics State
    @Published var progressMetrics: [ProgressMetricItem] = [
        ProgressMetricItem(id: "goalProgress", label: ScaleModesStrings.goalProgress, isOn: true),
        ProgressMetricItem(id: "dailyAverage", label: ScaleModesStrings.dailyAverage, isOn: true),
        ProgressMetricItem(id: "weeklyAverage", label: ScaleModesStrings.weeklyAverage, isOn: true),
        ProgressMetricItem(id: "monthlyAverage", label: ScaleModesStrings.monthlyAverage, isOn: true),
    ]
    
    // Banner States
    @Published var showWeightOnlyBanner: Bool = false
    @Published var showWeightOnlyInfo: Bool = false
    @Published var showHeartRateBanner: Bool = false
    
    // Metrics State
    @Published var metrics: [BodyMetricItem] = BodyMetrics.config.keys
        .filter { $0 != .weight }
        .map { BodyMetricItem(id: $0, isOn: true) }
    
    // User Management State
    @Published var currentUser: String = "Kristin" // TODO: Replace with actual user
    @Published var otherUsers: [String] = Array(repeating: "User Name", count: 8) // TODO: Replace with actual user
    @Published var isWifiLoading = false
    @Published var showPassword: Bool = false
    @Published var wifiPasswordValidationForm = WifiPasswordValidationForm()
    @Published var wifiConnectionState: ConnectionState = .loading
    @Published var connectedWifiNetwork: String? = nil
    @Published var wifiNetworks: [String] = ["greatergoods1", "great2542", "ggtesting"] // TODO: eplace with actual wifi Networks
    var isFormValid: Bool { wifiPasswordValidationForm.isValid }
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
    
    var passwordError: String? { wifiPasswordValidationForm.getError(for: wifiPasswordValidationForm.password) }
    
    func setPasswordTouched() {
        wifiPasswordValidationForm.password.markAsDirty()
        objectWillChange.send()
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
    
    func handleWifiCredentialsExit(onExit: @escaping () -> Void) {
        let alert = AlertModel(
            title: alertLang.ConnectWifiNetwork.title,
            message: alertLang.ConnectWifiNetwork.message,
            buttons: [
                AlertButtonModel(title: alertLang.ConnectWifiNetwork.goBackButton, type: .secondary) { _ in
                    // Do nothing, just dismiss alert
                },
                AlertButtonModel(title: alertLang.ConnectWifiNetwork.exitButton, type: .primary) { _ in
                    onExit()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func connectToWifiNetwork(wifiName: String) {
           wifiConnectionState = .loading
           // Simulate async connection (replace with your real logic)
           DispatchQueue.global().asyncAfter(deadline: .now() + 2.0) {
               // Simulate success or failure randomly
               let didSucceed = Bool.random()
               DispatchQueue.main.async {
                   // Add a slight delay for loader polish
                   DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                       self.wifiConnectionState = didSucceed ? .success : .failure
                   }
               }
           }
       }
    
    func bluetoothTapped() {
        // TODO: Implement bluetoothTapped action
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
    func saveUsers() {
        // TODO: Implement save users logic
    }
    func deleteCurrentUser() {
        // TODO: Implement delete current user logic
    }
    func deleteOtherUser(at index: Int) {
        // TODO: Implement delete other user logic
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
    
    // MARK: - Display Metrics Functions
    
    /// Saves the display metrics configuration
    func saveDisplayMetrics() {
        // TODO: Implement saveDisplayMetrics functionality
        // - Save the current state of metrics and extraToggles
    }
    
    /// Updates the weight-only mode setting
    func updateWeightOnlyMode() {
        // TODO: Implement updateWeightOnlyMode functionality
        // - Toggle weight-only mode on/off
        // - Update scale configuration
    }
    
    /// Updates the heart rate monitoring setting
    func updateHeartRate() {
        // TODO: Implement updateHeartRate functionality
        // - Toggle heart rate monitoring on/off
        // - Update scale configuration
    }
    
    /// Updates the heart rate monitoring setting
    func refreshWifiNetworks() {
        // TODO: Implement refreshWifiNetworks functionality
    }
    
}
