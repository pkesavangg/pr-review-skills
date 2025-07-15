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
    let showPulsingCircle: Bool
    
    @State private var pulse = false
    @Environment(\.appTheme) var theme
    
    // MARK: - Animation Constants
    private let pulsingCircleSize: CGFloat = 172
    private let minScale: CGFloat = 100 / 172
    
    init(image: String, isFailure: Bool = false, showPulsingCircle: Bool = true) {
        self.image = image
        self.isFailure = isFailure
        self.showPulsingCircle = showPulsingCircle
    }
    
    var shouldPulse: Bool {
        showPulsingCircle
    }
    
    var body: some View {
        ZStack(alignment: .center) {
            if shouldPulse {
                Circle()
                    .fill(isFailure ? theme.statusError : theme.statusIconLoading)
                    .frame(width: pulsingCircleSize, height: pulsingCircleSize)
                    .scaleEffect(pulse ? 1.0 : minScale)
                    .opacity(pulse ? 0.7 : 1.0)
                    .animation(
                        .easeInOut(duration: 1.2).repeatForever(autoreverses: true),
                        value: pulse
                    )
            }
            if !(shouldPulse && isFailure) {
                 Circle()
                    .fill(isFailure ? theme.statusError : theme.brandWgPrimary)
                    .frame(width: 89, height: 89)
            }
            
            if shouldPulse && isFailure {
                Image(image)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 89, height: 89)
            } else {
                AppIconView(icon: image, size: IconSize(width: 59, height: 59))
                    .foregroundColor(theme.backgroundPrimary)
            }
        }
        .frame(width: shouldPulse ? pulsingCircleSize : 89,
               height: shouldPulse ? pulsingCircleSize : 89)
        .onAppear {
            if shouldPulse {
                pulse = true
            }
        }
        .onChange(of: shouldPulse) { _, newValue in
            pulse = newValue
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
