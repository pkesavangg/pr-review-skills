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

    @State private var presentingBrowserURL: URL?
    @State private var isBrowserPresented: Bool = false

    let entry: Entry
    let metric: BodyMetric

    private var config: MetricData { BodyMetrics.config[metric]! }

    // Extract raw metric value from Entry → BathScaleEntry/BathScaleMetric
    private var rawValue: Double? {
        switch metric {
        case .weight:                 return entry.scaleEntry?.weight.map(Double.init)
        case .bmi:                    return entry.scaleEntry?.bmi.map(Double.init)
        case .bodyFat:                return entry.scaleEntry?.bodyFat.map(Double.init)
        case .muscleMass:             return entry.scaleEntry?.muscleMass.map(Double.init)
        case .water:                  return entry.scaleEntry?.water.map(Double.init)
        case .pulse:                  return entry.scaleEntryMetric?.pulse.map(Double.init)
        case .boneMass:               return entry.scaleEntryMetric?.boneMass.map(Double.init)
        case .visceralFatLevel:       return entry.scaleEntryMetric?.visceralFatLevel.map(Double.init)
        case .subcutaneousFatPercent: return entry.scaleEntryMetric?.subcutaneousFatPercent.map(Double.init)
        case .proteinPercent:         return entry.scaleEntryMetric?.proteinPercent.map(Double.init)
        case .skeletalMusclePercent:  return entry.scaleEntryMetric?.skeletalMusclePercent.map(Double.init)
        case .bmr:                    return entry.scaleEntryMetric?.bmr.map(Double.init)
        case .metabolicAge:           return entry.scaleEntryMetric?.metabolicAge.map(Double.init)
        }
    }

    private var formattedValue: String {
      if metric == .weight {
        return WeightValueConvertor.formatWeight(rawValue ?? 0, showSymbol: false, weightUnit: weightUnit, weightless: weightlessSettings)
        } else {
            return BodyMetricsConvertor.convert(
                rawValue,
                shouldCompose: config.bodyCompositionRelated,
                wholeNumber: config.isWholeNumber
            )
        }
    }

    private var measurementDescription: String {
        guard rawValue != nil else { return MetricStrings.noMeasurementAvailable }
        let date = DateTimeTools.getMonthDayYear(entry.entryTimestamp)
        return MetricStrings.measurementTaken + " \(date)"
    }

    private var content: MetricDetailContent {
        MetricContentRepository.content(for: metric)
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
                        .padding(.trailing, .spacingXS/2)
                    }
                    Text(formattedValue)
                      .fontOpenSans(.heading2)
                      .foregroundColor(theme.textHeading)
                    if metric == .weight {
                      Text(weightUnit.rawValue)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .padding(.leading, .spacingXS/2)
                    }
                    if !config.unit.isEmpty {
                      Text(config.unit)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .padding(.leading, .spacingXS/2)
                    }
                  }
                    // Measurement date/placeholder
                    Text(measurementDescription)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)
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
                                action: {
                                    presentingBrowserURL = URL(string: res.link)!
                                    isBrowserPresented = true
                                 }
                            )
                        }
                    }
                  }
                }
            }
            .padding(.horizontal, .spacingSM)
        }
        .inAppBrowser(
          url: presentingBrowserURL ?? URL(filePath: URLStrings.baseUrl),
          isPresented: $isBrowserPresented
      )
    }
}
