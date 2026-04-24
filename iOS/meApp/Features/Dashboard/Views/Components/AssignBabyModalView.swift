//
//  AssignBabyModalView.swift
//  meApp
//

import SwiftUI

/// Modal sheet for choosing which baby a new baby-scale reading belongs to.
/// Presented via `notificationService.showModal(ModalData(...))` after the user
/// taps ASSIGN on the reading-arrival toast.
struct AssignBabyModalView: View {
    @Environment(\.appTheme) private var theme

    struct BabyItem {
        let id: String
        let name: String
        let birthday: Date?
    }

    let babies: [BabyItem]
    let weightMessage: String
    let onAssign: (String) -> Void
    let onDontAssign: () -> Void
    let onClose: () -> Void

    @State private var selectedBabyId: String

    private let lang = DashboardStrings.self

    private var weightMessageView: some View {
        let parts = weightMessage.components(separatedBy: " · ")
        let weight = parts.first ?? weightMessage
        let timestamp = parts.count > 1 ? " - \(parts[1])" : ""
        return (styledBabyWeightText(weight, theme: theme)
            + Text(timestamp)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody))
            .multilineTextAlignment(.center)
    }

    init(
        babies: [BabyItem],
        weightMessage: String,
        onAssign: @escaping (String) -> Void,
        onDontAssign: @escaping () -> Void,
        onClose: @escaping () -> Void
    ) {
        self.babies = babies
        self.weightMessage = weightMessage
        self.onAssign = onAssign
        self.onDontAssign = onDontAssign
        self.onClose = onClose
        _selectedBabyId = State(initialValue: babies.first?.id ?? "")
    }

    var body: some View {
        VStack(spacing: 0) {
            // Close button
            HStack {
                Spacer()
                Button(action: onClose) {
                    AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 20, height: 20))
                        .foregroundColor(theme.statusIconPrimary)
                }
            }
            .padding(.bottom, .spacingXS)

            // Header icon + title
            VStack(spacing: .spacingSM) {
                Circle()
                    .strokeBorder(theme.textBody.opacity(0.4), lineWidth: 1.5)
                    .frame(width: 56, height: 56)
                    .overlay(
                        Image(systemName: "person.fill")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 24, height: 24)
                            .foregroundColor(theme.textBody)
                    )

                Text(lang.assignMeasurementTitle)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)

                Text(lang.assignMeasurementSubtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                    .multilineTextAlignment(.center)

                weightMessageView
            }
            .padding(.bottom, .spacingLG)

            // Baby list
            VStack(spacing: .spacingSM) {
                ForEach(babies, id: \.id) { baby in
                    BabySelectionRow(
                        baby: baby,
                        isSelected: selectedBabyId == baby.id
                    ) {
                        selectedBabyId = baby.id
                    }
                }
            }
            .padding(.bottom, .spacingLG)

            // Action buttons
            VStack(spacing: .spacingXS) {
                ButtonView(
                    text: lang.assignMeasurementAssign,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: selectedBabyId.isEmpty,
                    action: { onAssign(selectedBabyId) }
                )
                ButtonView(
                    text: lang.assignMeasurementDontAssign,
                    type: .textPrimary,
                    size: .large,
                    isDisabled: false,
                    action: onDontAssign
                )
            }
        }
        .padding(.spacingMD)
        .background(theme.backgroundSecondary)
        .cornerRadius(.radiusXL)
    }
}

// MARK: - Baby selection row

private struct BabySelectionRow: View {
    @Environment(\.appTheme) private var theme

    let baby: AssignBabyModalView.BabyItem
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: .spacingSM) {
                // Avatar circle with initial
                ZStack {
                    Circle()
                        .fill(theme.babyPrimary.opacity(0.2))
                        .frame(width: 40, height: 40)
                    Text(String(baby.name.prefix(1)).uppercased())
                        .fontOpenSans(.body1)
                        .bold()
                        .foregroundColor(theme.babyPrimary)
                }

                // Name + age
                VStack(alignment: .leading, spacing: 2) {
                    Text(baby.name)
                        .fontOpenSans(.body1)
                        .bold()
                        .foregroundColor(theme.textHeading)
                    let age = ageLabel(for: baby.birthday)
                    if !age.isEmpty {
                        Text(age)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                }

                Spacer()

                // Radio button
                ZStack {
                    Circle()
                        .strokeBorder(isSelected ? theme.babyPrimary : theme.textBody.opacity(0.4), lineWidth: 2)
                        .frame(width: 22, height: 22)
                    if isSelected {
                        Circle()
                            .fill(theme.babyPrimary)
                            .frame(width: 12, height: 12)
                    }
                }
            }
            .padding(.spacingSM)
            .background(
                RoundedRectangle(cornerRadius: .radiusMD)
                    .fill(isSelected ? theme.babyPrimary.opacity(0.1) : theme.backgroundPrimary)
            )
        }
        .buttonStyle(.plain)
    }

    private func ageLabel(for birthday: Date?) -> String {
        guard let birthday else { return "" }
        let months = Calendar.current.dateComponents([.month], from: birthday, to: Date()).month ?? 0
        if months < 1 {
            let days = Calendar.current.dateComponents([.day], from: birthday, to: Date()).day ?? 0
            return "\(days) days old"
        }
        return "\(months) months old"
    }
}
