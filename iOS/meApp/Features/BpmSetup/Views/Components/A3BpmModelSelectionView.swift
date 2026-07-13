///
///  A3BpmModelSelectionView.swift
///  meApp
///

import SwiftUI

/// Grid of BPM model cards for the ``BpmSetupStep/selectModel`` step.
struct A3BpmModelSelectionView: View {
    @Environment(\.appTheme) private var theme

    let models: [DeviceItemInfo]
    let selectedSku: String?
    let onSelect: (String) -> Void

    private let lang = BpmSetupStrings.ModelSelection.self
    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    Text(lang.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                .accessibilityElement(children: .combine)

                LazyVGrid(columns: columns, spacing: .spacingMD) {
                    ForEach(models) { model in
                        BpmModelCard(
                            model: model,
                            isSelected: model.sku == selectedSku
                        ) {
                            onSelect(model.sku)
                        }
                        .appAccessibility(id: AccessibilityID.bpmModelCard(model.sku))
                    }
                }
            }
            .padding(.top, .spacingLG)
        }
    }
}

// MARK: - Model Card

private struct BpmModelCard: View {
    @Environment(\.appTheme) private var theme

    let model: DeviceItemInfo
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: .spacingXS) {
                Image(model.imgPath)
                    .resizable()
                    .scaledToFit()
                    .frame(height: 100)
                    .cornerRadius(.radiusMD)
                    .accessibilityHidden(true)

                Text(bpmListModelLabel(primarySku: model.sku))
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textHeading)
            }
            .frame(maxWidth: .infinity)
            .padding(.spacingMD)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusLG)
            .overlay(
                RoundedRectangle(cornerRadius: .radiusLG)
                    .stroke(
                        isSelected ? theme.brandWgPrimary : theme.statusUtilitySecondary,
                        lineWidth: isSelected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityHint(BpmSetupStrings.A11y.modelCardHint)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

#Preview {
    A3BpmModelSelectionView(
        models: BPMS,
        selectedSku: "0603"
    ) { _ in }
    .padding(.horizontal)
    .environmentObject(Theme.shared)
}
