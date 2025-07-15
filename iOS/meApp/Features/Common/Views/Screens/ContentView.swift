//
//  ContentView.swift
//  meApp
//
//  Created by Barath Chittibabu on 27/05/25.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @StateObject private var viewModel = ContentViewModel()
    let wifiNetworks: [WifiDetails] = [
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:ff", ssid: "Home WiFi"),
        WifiDetails(macAddress: "11:22:33:44:55:66", ssid: "Office WiFi"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:00", ssid: "Guest Network"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:01", ssid: "Cafe Lounge"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:02", ssid: "Library Public"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:03", ssid: "John's iPhone"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:04", ssid: "Smart TV"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:05", ssid: "Printer LAN"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:06", ssid: "Warehouse Net"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:07", ssid: "KitchenCam"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:08", ssid: "Studio Sound"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:09", ssid: "Drone Access"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:0A", ssid: "Security Router"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:0B", ssid: "LivingRoomMesh"),
        WifiDetails(macAddress: "aa:bb:cc:dd:ee:0C", ssid: "IoT Hub")
    ]
    @State private var openSheetView = false
    var body: some View {
        
//        Button {
//            openSheetView.toggle()
//        } label: {
//            Text("Open Wifi Selection")
//        }

        VStack {
            switch viewModel.contentViewState {
            case .initializing:
                LoadingScreen()
            case .dashboard:
                BottomTabBarView()
            case .landing:
                LandingScreen()
            }
        }
        .preferredColorScheme(themeManager.getPreferredAppearanceMode())
        .onChange(of: colorScheme, { oldValue, newValue in
            themeManager.syncWithSystemColorScheme(newValue)
        })
        .onAppear {
            viewModel.performAppInitialization()
            themeManager.syncWithSystemColorScheme(colorScheme)
        }
        .sheet(isPresented: $openSheetView) {
            WifiSelectionView(
                connectedWifiNetwork: WifiDetails(macAddress: "aa:bb:cc:dd:ee:ff", ssid: "Home WiFi"),
                wifiNetworks: wifiNetworks,
                onRefresh: {},
                onNetworkSelected: { _ in }
            )
            .padding(.horizontal)
            .frame(maxHeight: .infinity)
            .background(theme.backgroundSecondary)
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(Theme.shared)
}
