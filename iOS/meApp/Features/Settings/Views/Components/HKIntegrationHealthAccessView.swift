//
//  HKIntegrationHealthAccessView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//


import SwiftUI

struct HKIntegrationHealthAccessView: View {
    let state: AppleHealthIntegrationState
    let commonLang = CommonStrings.self
    let lang = HKIntegrationStrings.self
    let onDismiss: (() -> Void)?

    private var content: HKIntegrationHealthAccessContent {
        switch state {
        case .notConnected:
            return HKIntegrationHealthAccessStrings.notConnected
        case .permissionsAllowed:
            return HKIntegrationHealthAccessStrings.permissionsAllowed
        case .permissionsNotAllowed:
            return HKIntegrationHealthAccessStrings.notConnected // Reuse or create new if needed
        case .integrationComplete:
            return HKIntegrationHealthAccessStrings.integrationComplete
        case .integrationFailed:
            return HKIntegrationHealthAccessStrings.integrationFailed
        case .userConflict:
            return HKIntegrationHealthAccessStrings.userConflict
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            
            NavbarHeaderView(
                title: lang.healthAccess,
                leadingContent: { Image(AppAssets.xmark) },
                trailingContent: {
                    Button {
                        
                    } label: {
                        Image(AppAssets.helpCircle)
                    }
                },
                onLeadingTap: {
                    onDismiss?()
                },
                onTrailingTap: {},
                canShowBorder: true
            )
            Spacer()
            
            // Header
            Text("Health Access")
                .font(.title2)
                .fontWeight(.semibold)
                .padding(.top, 20)

            // Image
            Image(content.imageName)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: 300, maxHeight: 300)
                .padding()

            // Title
            Text(content.title)
                .font(.title3)
                .fontWeight(.semibold)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            // Description
            Text(content.description)
                .font(.body)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            // Button
            Button(action: {
                // Handle action depending on state if needed
            }) {
                Text(content.buttonTitle)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(12)
            }
            .padding(.horizontal)
            .padding(.bottom, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}

struct AppleHealthIntegrationScreen_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            HKIntegrationHealthAccessView(state: .integrationComplete) {
                
            }
            HKIntegrationHealthAccessView(state: .integrationFailed) {
                
            }
        }
    }
}
