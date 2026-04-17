//
//  MetricDetail.swift
//  meApp
//
//  Created by Barath Chittibabu on 20/06/25.
//
import SwiftUI

struct MetricDetailView: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.weightlessSettings) private var weightlessSettings
    @Environment(\.weightUnit) private var weightUnit
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel

    @State private var presentingBrowserURL: URL?
    @State private var isBrowserPresented: Bool = false
    @State private var showScaleModesSheet: Bool = false

    let entryDTO: BathScaleOperationDTO
    let metric: BodyMetric
    let measurementLabel: String?
    
    private let placeholder = "--"
    private var config: MetricData {
        BodyMetrics.config[metric]
        ?? BodyMetrics.config[.weight]
        ?? MetricData(
            unit: "",
            label: MetricStrings.weight,
            bodyCompositionRelated: true,
            icon: AppAssets.bmiIcon
        )
    }
    private var fallbackBrowserURL: URL {
        URL(string: URLStrings.baseUrl) ?? AppConstants.LegalURLs.greaterGoodsWebsite
    }

    // Extract raw metric value from DTO (no SwiftData access needed)
    private var rawValue: Double? {
        switch metric {
        case .weight:                 return entryDTO.weight
        case .bmi:                    return entryDTO.bmi
        case .bodyFat:                return entryDTO.bodyFat
        case .muscleMass:             return entryDTO.muscleMass
        case .water:                  return entryDTO.water
        case .pulse:                  return entryDTO.pulse
        case .boneMass:               return entryDTO.boneMass
        case .visceralFatLevel:       return entryDTO.visceralFatLevel
        case .subcutaneousFatPercent: return entryDTO.subcutaneousFatPercent
        case .proteinPercent:         return entryDTO.proteinPercent
        case .skeletalMusclePercent:  return entryDTO.skeletalMusclePercent
        case .bmr:                    return entryDTO.bmr
        case .metabolicAge:           return entryDTO.metabolicAge
        }
    }

    // Helper to format metrics that are stored as scaled integers (e.g., BMR, visceral fat)
    // These metrics are stored scaled by 10 in the database and need to be divided by 10 for display
    private func formatScaledMetric(_ rawValue: Double?, scalingFactor: Double = 10.0) -> String {
        guard let raw = rawValue, abs(raw) > 0.001 else { return placeholder }
        let displayValue = raw / scalingFactor
        guard abs(displayValue) > 0.001 else { return placeholder }
        let formatted = BodyMetricsConvertor.convert(
            displayValue,
            shouldCompose: false,
            wholeNumber: true
        )
        if formatted == "0" || formatted == "0.0" {
            return placeholder
        }
        return formatted
    }

    private var formattedValue: String {
      if metric == .weight {
        return WeightValueConvertor.formatWeight(rawValue ?? 0, showSymbol: false, weightUnit: weightUnit, weightless: weightlessSettings)
        } else if metric == .bmr {
            // BMR: entryDTO.bmr is stored scaled by 10 (e.g., 14508 for 1450.8), so divide by 10 for display
            return formatScaledMetric(rawValue, scalingFactor: 10.0)
        } else if metric == .visceralFatLevel {
            // Visceral fat: entryDTO.visceralFatLevel is stored scaled by 10 (e.g., 200 for level 20), so divide by 10 for display
            return formatScaledMetric(rawValue, scalingFactor: 10.0)
        } else {
            // For other metrics, check if value is 0 or nil and return placeholder
            guard let value = rawValue, abs(value) > 0.001 else { return placeholder }
            let formatted = BodyMetricsConvertor.convert(
                value,
                shouldCompose: config.bodyCompositionRelated,
                wholeNumber: config.isWholeNumber
            )
            // Check if formatted result is "0" or "0.0" and return placeholder
            if formatted == "0" || formatted == "0.0" {
                return placeholder
            }
            return formatted
        }
    }

    // Dynamic unit for weight (lb/lbs or kg) based on the displayed magnitude
    private var weightUnitLabel: String {
        guard metric == .weight else { return "" }
        let stored = Int(rawValue ?? 0)
        let display = ConversionTools.convertStoredToDisplay(stored, isMetric: weightUnit == .kg)
        return WeightValueConvertor.unitForDisplay(value: display, unit: weightUnit)
    }

    private var measurementDescription: String {
        guard rawValue != nil else { return MetricStrings.noMeasurementAvailable }
        if let provided = measurementLabel, !provided.isEmpty { return provided }
        let date = DateTimeTools.getMonthDayYear(entryDTO.entryTimestamp ?? "")
        return MetricStrings.measurementTaken + " \(date)"
    }

    private var shouldShowHeartRateBanner: Bool {
        guard metric == .pulse else { return false }
        return !heartRateDisabledScales.isEmpty
    }

    private var content: MetricDetailContent {
        MetricContentRepository.content(for: metric)
    }

    // MARK: - Scale Preference Helpers
    private var allScales: [DeviceSnapshot] { ScaleService.shared.scales }
    private var heartRateDisabledScales: [DeviceSnapshot] {
        allScales.filter { device in
            guard let pref = device.r4ScalePreference else { return false }
            return pref.shouldMeasureImpedance && !pref.shouldMeasurePulse
        }
    }
    private var activePreference: R4ScalePreferenceSnapshot? { allScales.first?.r4ScalePreference }
    private var selectedDisabledPreference: R4ScalePreferenceSnapshot? { heartRateDisabledScales.first?.r4ScalePreference }
    private var isHeartRateOnBannerState: Bool { heartRateDisabledScales.isEmpty }
    private var selectedModeFromPreference: ScaleModes { (activePreference?.shouldMeasureImpedance ?? true) ? .allBodyMetrics : .weightOnly }
    /// Preferred scale for presenting ScaleModes when exactly one scale needs update.
    private var selectedScale: Device? {
        (heartRateDisabledScales.first ?? ScaleService.shared.scales.first)?.toDevice()
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {

                VStack(alignment: .leading, spacing: 0) {
                                  // Value & unit
                  HStack(alignment: .firstTextBaseline, spacing: .spacingXS) {
                    if let pre = config.preLabel {
                      Text(pre)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .padding(.trailing, .spacingXS / 2)
                    }
                      Text(formattedValue)
                        .fontOpenSans(.heading2)
                        .foregroundColor(theme.textHeading)
                        .offset(y: formattedValue == placeholder ? 8 : 0)
                    
                    if metric == .weight {
                      Text(weightUnitLabel)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .padding(.leading, .spacingXS / 2)
                    }
                    if !config.unit.isEmpty {
                      Text(config.unit)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .padding(.leading, .spacingXS / 2)
                    }
                  }
                    // Measurement date/placeholder
                    Text(measurementDescription)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)

                    // Heart-rate banner (only for Heart Rate metric when HR is OFF)
                    if shouldShowHeartRateBanner {
                        HeartRateBanner(
                            isHeartRateOn: isHeartRateOnBannerState,
                            onUpdate: {
                                let disabled = heartRateDisabledScales
                                if disabled.count == 1 {
                                    showScaleModesSheet = true
                                } else if disabled.count > 1 {
                                    // Dismiss this modal first, then route to My Scales via Settings tab routing
                                    dismiss()
                                    tabViewModel.navigateToSettings(route: .addEditScales)
                                }
                            },
                            showOnlyContent: true
                        )
                        .padding(.top, .spacingXL)
                        .padding(.bottom, .spacingXS)
                    }
                }
                .padding(.top, .spacingLG)

                VStack(alignment: .leading, spacing: .spacingMD) {
                                  // Educational header
                    Text(content.header)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    // Messages
                    ForEach(content.messages, id: \.self) { msg in
                        Text(msg)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                // Resources
                if !content.resources.isEmpty {
                  VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(CommonStrings.resources)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        ForEach(content.resources, id: \.link) { res in
                            ButtonView(
                                text: res.title,
                                type: .inlineTextPrimary,
                                size: .small,
                                isDisabled: false,
                                alignment: .leading
                            ) {
                                  if let url = URL(string: res.link) {
                                    presentingBrowserURL = url
                                    isBrowserPresented = true
                                  }
                                }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .multilineTextAlignment(.leading)
                        }
                    }
                  }
                }
            }
            .padding(.horizontal, .spacingSM)
        }
        .inAppBrowser(
          url: presentingBrowserURL ?? fallbackBrowserURL,
          isPresented: $isBrowserPresented
      )
        .sheet(isPresented: $showScaleModesSheet) {
            if let scale = selectedScale {
                ScaleModesScreen(
                    scale: scale,
                    isR4ScaleSetup: false,
                    isPresentedAsSheet: true
                )
                .environmentObject(Theme.shared)
                .environmentObject(Router<SettingsRoute>())
            } else {
                EmptyView()
            }
        }
    }
}
