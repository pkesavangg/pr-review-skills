import SwiftUI
#if canImport(ggInAppMessagingPackage)
import ggInAppMessagingPackage
#endif

// MARK: - IAMFeedModalView
/// Displays an in-app messaging (IAM) feed item as a modal.
/// Mirrors the design of the Ionic/Angular `feed-modal` while adhering
/// to the meApp design system (theme, spacing, typography, tokens).
struct IAMFeedModalView: View {
    // MARK: - Environment
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    
    // MARK: - Props
    let feedItem: FeedItem
    let onClose: () -> Void
    
    
    // MARK: - Body
    var body: some View {
        VStack {
            AsyncImage(url: URL(string: feedItem.titleImage)) { phase in
                switch phase {
                case .empty: Color.gray.opacity(0.1)
                case .success(let img): img.resizable().scaledToFill()
                case .failure: Color.gray.opacity(0.2)
                @unknown default: Color.gray
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 230)
            .clipped()
            
            VStack(spacing: .spacingLG) {
                VStack(spacing: .spacingSM) {
                    // Message type subtitle (e.g. "TYPE OF MESSAGE")
                    Text(feedItem.messageTypeText)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.center)
                        .textCase(.uppercase)
                    
                    // Title (headline)
                    Text(FeedTextFormatter.preventWidow(feedItem.titleText))
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                    
                    // Supporting subtitle with rich-text formatting
                    if let subtitle = feedItem.subtitleModalText {
                        let nsAttr = FeedTextFormatter.formatFeedTemplate(subtitle, feedItem: feedItem)
                        Text(AttributedString(nsAttr))
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.center)
                    }
                }
                
                VStack(spacing: .spacingSM) {
                    // Primary CTA button ("SHOP NOW", etc.)
                    if !feedItem.linkText.isEmpty {
                        ButtonView(
                            text: feedItem.linkText,
                            type: .filledPrimary,
                            size: .large,
                            isDisabled: false,
                            action: {}
                        )
                    }
                    
                    ButtonView(text: "message settings".uppercased(), type: .inlineTextPrimary, size: .large, isDisabled: false) {
                        
                    }
                }
            }
            .padding(.spacingMD)
        }
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
        .overlay(alignment: .topTrailing) {
            Button(action: onClose) {
                ZStack {
                    Circle()
                        .fill(theme.backgroundPrimary)
                        .frame(width: 20, height: 20)
                        
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 12, height: 12))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.spacingMD)

        }
    }
}
