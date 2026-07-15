///
///  A3BpmInstructionView.swift
///  meApp
///

import SwiftUI
import UIKit

/// Reusable instruction view used for multiple BPM setup steps that show
/// a heading, body text, and an optional device image in a rounded card.
///
/// Used by: ``BpmSetupStep/setUser``, ``BpmSetupStep/confirmUser``,
/// ``BpmSetupStep/prePairing``, ``BpmSetupStep/measureSetup``, ``BpmSetupStep/measureStart``.
struct A3BpmInstructionView: View {
    @Environment(\.appTheme) private var theme

    let title: String
    let description: String
    let imagePath: String?
    var gifName: String?
    var gifSubdirectory: String?
    var resourceImageName: String?
    var resourceImageSubdirectory: String?
    var mediaLayout: BpmSetupMediaLayout = .center
    var contentHorizontalPadding: CGFloat = .spacingXSM
    var mediaHorizontalPadding: CGFloat = .spacingXSM
    var wrapsMediaInCard: Bool = true

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)
                .frame(maxWidth: .infinity, alignment: .leading)

                mediaView
                    .padding(.horizontal, mediaHorizontalPadding)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            .padding(.top, .spacingLG)
        }
    }

    @ViewBuilder
    private var mediaView: some View {
        if let gifName {
            if wrapsMediaInCard {
                wrappedMediaViewIfNeeded {
                    GifView(
                        gifName: gifName,
                        subdirectory: gifSubdirectory,
                        verticalAlignment: gifVerticalAlignment
                    )
                    .accessibilityLabel(BpmSetupStrings.A11y.gifLabel)
                }
            } else {
                measurementGifView(gifName: gifName)
            }
        } else if let resourceImage {
            wrappedMediaViewIfNeeded {
                Image(uiImage: resourceImage)
                    .resizable()
                    .scaledToFit()
                    // Cap the size but allow the image to shrink on narrower pages.
                    // A fixed width here would force the whole content column wider
                    // than the page, clipping the heading and bleeding to the next slide.
                    .frame(
                        maxWidth: imageMediaSize.width,
                        maxHeight: imageMediaSize.height
                    )
                    .accessibilityLabel(BpmSetupStrings.A11y.deviceImageLabel)
            }
        } else if let imagePath {
            wrappedMediaViewIfNeeded {
                Image(imagePath)
                    .resizable()
                    .scaledToFit()
                    .frame(
                        maxWidth: imageMediaSize.width,
                        maxHeight: imageMediaSize.height
                    )
                    .accessibilityLabel(BpmSetupStrings.A11y.deviceImageLabel)
            }
        }
    }

    @ViewBuilder
    private func wrappedMediaViewIfNeeded<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        let builtContent = content()

        if wrapsMediaInCard {
            BpmSetupMediaCard(layout: mediaLayout) {
                builtContent
            }
        } else {
            builtContent
        }
    }

    private func measurementGifView(gifName: String) -> some View {
        // Fill the available page width rather than a fixed 370pt. A hard width
        // wider than the page's inner width forced the whole content block off-centre
        // (left/right clipped) on larger devices like iPhone 17 Pro. The GIF uses
        // object-fit: contain, so it keeps its aspect ratio within this frame.
        GifView(
            gifName: gifName,
            subdirectory: gifSubdirectory,
            verticalAlignment: gifVerticalAlignment
        )
        .accessibilityLabel(BpmSetupStrings.A11y.gifLabel)
        .frame(maxWidth: .infinity)
        .frame(height: 250)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: .radiusLG))
    }

    private var resourceImage: UIImage? {
        guard let resourceImageName else { return nil }
        let path =
            Bundle.main.path(
                forResource: resourceImageName,
                ofType: "png",
                inDirectory: resourceImageSubdirectory
            ) ??
            Bundle.main.path(forResource: resourceImageName, ofType: "png")

        guard let path else {
            return nil
        }
        return UIImage(contentsOfFile: path)
    }

    private var gifVerticalAlignment: GifVerticalAlignment {
        switch mediaLayout {
        case .top: return .top
        case .center: return .center
        case .bottom: return .bottom
        }
    }

    private var imageMediaSize: CGSize {
        if wrapsMediaInCard {
            return BpmSetupMediaMetrics.cardContentSize
        }

        return CGSize(
            width: DevicePlatform.isMiniPhone ? 260 : 300,
            height: DevicePlatform.isMiniPhone ? 220 : 250
        )
    }
}

#Preview {
    A3BpmInstructionView(
        title: "Set the monitor to User 1.",
        description: "Change the user by tapping the USER button.",
        imagePath: AppAssets.bpm0603
    )
    .padding(.horizontal)
    .environmentObject(Theme.shared)
}
