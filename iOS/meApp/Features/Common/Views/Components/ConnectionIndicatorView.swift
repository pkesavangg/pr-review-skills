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
    let customImageSize: CGSize?
    let showSmallCircleOverride: Bool?
    
    @State private var pulse = false
    @Environment(\.appTheme) var theme
    
    // MARK: - Animation Constants
    private let pulsingCircleSize: CGFloat = 172
    private let minScale: CGFloat = 100 / 172
    
    init(image: String, isFailure: Bool = false, showPulsingCircle: Bool = true, customImageSize: CGSize? = nil, showSmallCircleOverride: Bool? = nil) {
        self.image = image
        self.isFailure = isFailure
        self.showPulsingCircle = showPulsingCircle
        self.customImageSize = customImageSize
        self.showSmallCircleOverride = showSmallCircleOverride
    }
    
    // MARK: - Computed Properties for State Management
    var shouldPulse: Bool {
        showPulsingCircle
    }
    
    var showSmallCircle: Bool {
        if let override = showSmallCircleOverride {
            return override
        }
        return !(shouldPulse && isFailure)
    }
    
    var smallCircleColor: Color {
        isFailure ? theme.textError : theme.brandWgPrimary
    }
    
    var pulsingCircleColor: Color {
        isFailure ? theme.textErrorDisabled : theme.statusIconLoading
    }
    
    var showImage: Bool {
        shouldPulse && isFailure
    }
    
    var containerSize: CGFloat {
        shouldPulse ? pulsingCircleSize : 89
    }
    
    var body: some View {
        ZStack(alignment: .center) {
            if shouldPulse {
                Circle()
                    .fill(pulsingCircleColor)
                    .frame(width: pulsingCircleSize, height: pulsingCircleSize)
                    .scaleEffect(pulse ? 1.0 : minScale)
                    .opacity(pulse ? 0.7 : 1.0)
                    .animation(
                        .easeInOut(duration: 1.2).repeatForever(autoreverses: true),
                        value: pulse
                    )
            }
            
            if showSmallCircle {
                Circle()
                    .fill(smallCircleColor)
                    .frame(width: 89, height: 89)
            }
            
            if showImage {
                Image(image)
                    .resizable()
                    .scaledToFit()
                    .frame(
                        width: customImageSize?.width ?? 89,
                        height: customImageSize?.height ?? 89
                    )
                    .themeDropShadow()
            } else {
                AppIconView(icon: image, size: IconSize(width: 59, height: 59))
                    .foregroundColor(theme.backgroundPrimary)
            }
        }
        .frame(width: containerSize, height: containerSize)
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

// Testing View
struct TestConnectedIndicatorView: View {
    var body: some View {
        VStack(spacing: 35) {
            ConnectionIndicatorView(image: AppAssets.wifi, isFailure: false)
            ConnectionIndicatorView(image: AppAssets.wifi, isFailure: true)
            ConnectionIndicatorView(image: AppAssets.meLogoLight, isFailure: true)
        }
    }
}

#Preview {
    TestConnectedIndicatorView()
}
