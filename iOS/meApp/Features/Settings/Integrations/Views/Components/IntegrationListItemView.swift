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
    var body: some View {
        VStack {
            Spacer()
            HStack(spacing: .spacingSM) {
                
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
                            .frame(width: 42, height: 44)
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
                .frame(width: 42, height: 44)
                .padding(.top, .spacingXS)

                VStack(alignment: .leading, spacing: 2) {
                    Text(item.type.displayName)
                        .fontOpenSans(.itemTitle)
                        .foregroundColor(theme.textBody)

                    // Optional, non-interactive provider notice (e.g. Fitbit
                    // deprecation). Display-only — does not affect row tap
                    // or connect/sync behaviour (MOB-1608).
                    if let notice = item.type.deprecationNotice {
                        deprecationNotice(notice)
                    }
                }

                Spacer()

                AppIconView(
                    icon: item.isSelected ? AppAssets.circleCheckFilled : AppAssets.circleOutline,
                    size: IconSize(width: 24, height: 24)
                )
                .foregroundColor(theme.statusIconPrimary)
            }
            Spacer()
        }
        // minHeight (not a fixed height) so the row can grow to fit the
        // deprecation notice and larger Dynamic Type sizes without clipping.
        .frame(minHeight: rowHeight)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
    }

    // MARK: - Deprecation notice
    /// Subtle inline info pill shown beneath a provider title. Non-interactive.
    private func deprecationNotice(_ text: String) -> some View {
        // Render the info icon INLINE with the text (a single Text) so it sits on the same
        // line as "Moving …", baseline-aligned, and stays with the first word when the
        // notice wraps. Design spec: 12px / regular with an 18px line-height — body4 is
        // 12px OpenSans-Regular (~16.3px natural line height), so ~2pt of extra line
        // spacing lands the line box at the 18px design value.
        (Text(Image(systemName: "info.circle")) + Text(" ") + Text(text))
            .font(.custom("OpenSans-Regular", size: CustomTextStyle.body4.size))
            .lineSpacing(2)
            .foregroundColor(theme.textError)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(theme.backgroundSecondary)
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
