//
//  ConnectionIndicatorView.swift
//  meApp
//
//  Created by Lakshmi Priya on 20/06/25.
//

import SwiftUI

struct ConnectionIndicatorView: View {
    let image: String
    let isFailure: Bool
    @State private var pulse = false
    @Environment(\.appTheme) var theme
    
    var body: some View {
        ZStack(alignment: .center) {
            Circle()
                .fill(isFailure ? theme.statusIconLoadingError : theme.statusIconLoading)
                .frame(width: pulse ? 172 : 100, height: pulse ? 172 : 100)
                .scaleEffect(pulse ? 1.15 : 1.0)
                .opacity(pulse ? 0.7 : 1.0)
                .animation(
                    .easeInOut(duration: 1.2).repeatForever(autoreverses: true),
                    value: pulse
                )
            
            Circle()
                .fill(isFailure ? theme.statusError : theme.brandWgPrimary)
                .frame(width: 89, height: 89)
            
            Image(image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 59, height: 59)
                .foregroundColor(theme.backgroundPrimary)
        }
        .frame(width: 172, height: 172)
        .onAppear {
            pulse = true
        }
    }
}

#Preview {
    ConnectionIndicatorView(image: AppAssets.wifi, isFailure: true)
}

//Testing View
struct TestConnectedIndicatorView: View {
    var body: some View {
        VStack(spacing: 35){
            ConnectionIndicatorView(image: AppAssets.wifi, isFailure: false)
            ConnectionIndicatorView(image: AppAssets.wifi, isFailure: true)
            ConnectionIndicatorView(image: AppAssets.meLogoLight, isFailure: true)
        }
    }
}

#Preview {
    TestConnectedIndicatorView()
}
