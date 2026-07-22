//  IntegrationListItemView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//
import SwiftUI

// MARK: - IntegrationListItemView
/// A single row inside the integrations list showing provider logo, title and selection indicator.
struct IntegrationListItemView: View {
    @Environment(\.appTheme) private var theme

    let item: IntegrationItem
    /// Closure triggered when the *row* (excluding the out-of-sync badge) is tapped.
    var onTap: () -> Void
    /// Optional closure triggered **only** when the out-of-sync exclamation badge is tapped.
    /// If `nil`, the tap is ignored and row tap will be triggered instead.
    var onBadgeTap: (() -> Void)?
    let rowHeight: CGFloat = 80
    /// Fixed leading logo column. Kept in one place so the title row and the
    /// deprecation-notice indent stay in lockstep across every provider.
    private let iconSize: CGFloat = 32
    var body: some View {
        // Logo is centred against the full text block (title + optional notice),
        // while the provider name and the selection indicator share the first
        // line and the notice hangs beneath the title — matching the Figma mock.
        HStack(alignment: .center, spacing: .spacingSM) {
            logo

            VStack(alignment: .leading, spacing: 2) {
                // Title + selection indicator on the same line.
                HStack(alignment: .center, spacing: .spacingSM) {
                    Text(item.type.displayName)
                        .fontOpenSans(.itemTitle)
                        .foregroundColor(theme.textBody)

                    Spacer()

                    AppIconView(
                        icon: item.isSelected ? AppAssets.circleCheckFilled : AppAssets.circleOutline,
                        size: IconSize(width: 24, height: 24)
                    )
                    .foregroundColor(theme.statusIconPrimary)
                }

                // Optional, non-interactive provider notice (e.g. Fitbit
                // deprecation). Display-only — does not affect row tap or
                // connect/sync behaviour (MOB-1608). Sits directly beneath
                // the title text.
                if let notice = item.type.deprecationNotice {
                    deprecationNotice(notice)
                }
            }
        }
        // minHeight (not a fixed height) so the row can grow to fit the
        // deprecation notice and larger Dynamic Type sizes without clipping.
        .frame(minHeight: rowHeight)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }

    // MARK: - Logo
    /// Provider logo with an optional out-of-sync warning badge overlaid at the
    /// top-trailing corner. Fixed to `iconSize` so every row's logo lines up.
    private var logo: some View {
        ZStack(alignment: .topTrailing) {
            // Icon tap: triggers onTap()
            Button(action: {
                if item.isOutOfSync {
                    onBadgeTap?()
                } else {
                    onTap()
                }
            }, label: {
                Image(item.type.iconAsset)
                    .resizable()
                    .frame(width: iconSize, height: iconSize)
            })
            .buttonStyle(.plain)

            // Badge tap: triggers onBadgeTap()
            if item.isOutOfSync {
                Button(action: {
                    onBadgeTap?()
                }, label: {
                    AppIconView(
                        icon: AppAssets.exclamationMark,
                        size: IconSize(width: 20, height: 20)
                    )
                    .foregroundColor(theme.statusError)
                })
                .buttonStyle(.plain)
                .offset(x: 12, y: -10)
            }
        }
        .frame(width: iconSize, height: iconSize)
    }

    // MARK: - Deprecation notice
    /// Subtle inline info pill shown beneath a provider title. Non-interactive.
    private func deprecationNotice(_ text: String) -> some View {
        // Render the info icon INLINE with the text (a single Text) so it sits on the same
        // line as "Moving …", baseline-aligned. The single space between icon and text
        // mirrors the mock's 3px gap. The notice is a single, non-wrapping line per the
        // Figma "Fitbit alert" mock — lineLimit(1) + fixedSize lets the pill hug its
        // content width. Typography is Body 4 (12px OpenSans-Regular).
        // Colours bind to the exact semantic tokens shown in the Figma inspector:
        //   text + icon → meApp/Action/error-default (red-800)
        //   pill fill   → meApp/Action/inverse-pressed (neutral-200):
        //                 #F6F4F1 light · #12161B dark
        (Text(Image(systemName: "info.circle")) + Text(" ") + Text(text))
            .font(.custom("OpenSans-Regular", size: CustomTextStyle.body4.size))
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .foregroundColor(theme.actionError)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(theme.actionInversePressed)
            .clipShape(Capsule())
            .accessibilityLabel(text)
    }
}

// MARK: - Preview
#Preview {
    List {
        Section {
            IntegrationListItemView(
                item: IntegrationItem(
                    type: .appleHealth,
                    isSelected: true,
                    isOutOfSync: true
                ),
                onTap: { },
                onBadgeTap: { }
            )
            .listRowInsets()
            IntegrationListItemView(
                item: IntegrationItem(
                    type: .appleHealth,
                    isSelected: true
                ),
                onTap: { },
                onBadgeTap: nil
            )
            .listRowInsets()
            IntegrationListItemView(
                item: IntegrationItem(
                    type: .myFitnessPal,
                    isSelected: false
                ),
                onTap: {},
                onBadgeTap: nil
            )
            .listRowInsets()
            IntegrationListItemView(
                item: IntegrationItem(
                    type: .fitbit,
                    isSelected: false
                ),
                onTap: {},
                onBadgeTap: nil
            )
            .listRowInsets()
        }
    }
    .listStyle(.insetGrouped)
    .scrollContentBackground(.hidden)
    .environmentObject(Theme.shared)
    .background(.gray.opacity(0.51))
}
