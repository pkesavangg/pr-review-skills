import SwiftUI
import Testing
import UIKit
@testable import meApp

@Suite
@MainActor
struct BpmDomainModelTests {

    @Test("AHA classification respects guideline thresholds and precedence")
    func ahaClassificationThresholds() {
        let cases: [(systolic: Int, diastolic: Int, expected: AhaPressureClass)] = [
            (119, 79, .normal),
            (120, 79, .elevated),
            (129, 79, .elevated),
            (119, 80, .hypertensionStage1),
            (130, 79, .hypertensionStage1),
            (139, 89, .hypertensionStage1),
            (140, 79, .hypertensionStage2),
            (129, 90, .hypertensionStage2),
            (180, 120, .hypertensionStage2),
            (181, 120, .hypertensiveCrisis),
            (120, 121, .hypertensiveCrisis),
        ]

        for testCase in cases {
            #expect(
                AhaPressureClass.classify(
                    systolic: testCase.systolic,
                    diastolic: testCase.diastolic
                ) == testCase.expected
            )
        }
    }

    @Test("AHA display strings expose the expected labels and ranges")
    func ahaDisplayStrings() {
        // swiftlint:disable:next large_tuple
        let cases: [(value: AhaPressureClass, label: String, systolic: String, diastolic: String)] = [
            (.normal, BpmDashboardStrings.ahaNormal, BpmDashboardStrings.systolicNormal, BpmDashboardStrings.diastolicNormal),
            (.elevated, BpmDashboardStrings.ahaElevated, BpmDashboardStrings.systolicElevated, BpmDashboardStrings.diastolicElevated),
            (.hypertensionStage1, BpmDashboardStrings.ahaHypertensionStage1, BpmDashboardStrings.systolicStage1, BpmDashboardStrings.diastolicStage1),
            (.hypertensionStage2, BpmDashboardStrings.ahaHypertensionStage2, BpmDashboardStrings.systolicStage2, BpmDashboardStrings.diastolicStage2),
            (.hypertensiveCrisis, BpmDashboardStrings.ahaHypertensiveCrisis, BpmDashboardStrings.systolicCrisis, BpmDashboardStrings.diastolicCrisis),
        ]

        for testCase in cases {
            #expect(testCase.value.id == testCase.value.rawValue)
            #expect(testCase.value.label == testCase.label)
            #expect(testCase.value.systolicRange == testCase.systolic)
            #expect(testCase.value.diastolicRange == testCase.diastolic)
        }
    }

    @Test("AHA theme colors map to the intended severity colors")
    func ahaThemeColors() {
        let theme = AppColors.Theme.primary.palette

        assertColor(AhaPressureClass.normal.color(theme: theme), equals: theme.statusSuccess)
        assertColor(AhaPressureClass.elevated.color(theme: theme), equals: Color(red: 0.8, green: 0.68, blue: 0.0))
        assertColor(AhaPressureClass.hypertensionStage1.color(theme: theme), equals: Color(red: 0.66, green: 0.5, blue: 0.0))
        assertColor(AhaPressureClass.hypertensionStage2.color(theme: theme), equals: theme.statusError)
        assertColor(AhaPressureClass.hypertensiveCrisis.color(theme: theme), equals: Color(red: 0.6, green: 0.0, blue: 0.0))
    }

    @Test("AHA fallback colors stay available outside themed contexts")
    func ahaFallbackColors() {
        assertColor(AhaPressureClass.normal.fallbackColor, equals: .green)
        assertColor(AhaPressureClass.elevated.fallbackColor, equals: .yellow)
        assertColor(AhaPressureClass.hypertensionStage1.fallbackColor, equals: .orange)
        assertColor(AhaPressureClass.hypertensionStage2.fallbackColor, equals: .red)
        assertColor(AhaPressureClass.hypertensiveCrisis.fallbackColor, equals: Color(red: 0.6, green: 0.0, blue: 0.0))
    }

    @Test("BP category classification follows dashboard risk rules")
    func bpCategoryClassificationThresholds() {
        let cases: [(systolic: Int, diastolic: Int, expected: BPCategory)] = [
            (119, 79, .normal),
            (120, 79, .elevated),
            (129, 79, .elevated),
            (119, 80, .highStage1),
            (130, 79, .highStage1),
            (139, 89, .highStage1),
            (140, 79, .highStage2),
            (130, 90, .highStage2),
        ]

        for testCase in cases {
            #expect(
                BPCategory.classify(
                    systolic: testCase.systolic,
                    diastolic: testCase.diastolic
                ) == testCase.expected
            )
        }
    }

    @Test("BP category colors use the expected theme accents")
    func bpCategoryThemeColors() {
        let theme = AppColors.Theme.primary.palette

        assertColor(BPCategory.normal.color(theme: theme), equals: theme.actionSuccess)
        assertColor(BPCategory.elevated.color(theme: theme), equals: theme.statusError)
        assertColor(BPCategory.highStage1.color(theme: theme), equals: theme.actionError)
        assertColor(BPCategory.highStage2.color(theme: theme), equals: theme.actionError)
    }

    @Test("three reading average label adapts to reading count")
    func threeReadingAverageLabels() {
        #expect(ThreeReadingAverage.displayLabel(for: 1) == BpmDashboardStrings.lastReading)
        #expect(ThreeReadingAverage.displayLabel(for: 2) == BpmDashboardStrings.twoEntryAverage)
        #expect(ThreeReadingAverage.displayLabel(for: 3) == BpmDashboardStrings.threeEntryAverage)
        #expect(ThreeReadingAverage.displayLabel(for: 5) == BpmDashboardStrings.threeEntryAverage)
    }
}

@MainActor
private func assertColor(
    _ actual: Color,
    equals expected: Color
) {
    let actualComponents = rgbaComponents(for: UIColor(actual))
    let expectedComponents = rgbaComponents(for: UIColor(expected))
    let tolerance = 0.001

    #expect(abs(actualComponents.red - expectedComponents.red) < tolerance)
    #expect(abs(actualComponents.green - expectedComponents.green) < tolerance)
    #expect(abs(actualComponents.blue - expectedComponents.blue) < tolerance)
    #expect(abs(actualComponents.alpha - expectedComponents.alpha) < tolerance)
}

@MainActor
// swiftlint:disable:next large_tuple
private func rgbaComponents(for color: UIColor) -> (red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat) {
    let resolved = color.resolvedColor(with: UITraitCollection(userInterfaceStyle: .light))
    var red: CGFloat = 0
    var green: CGFloat = 0
    var blue: CGFloat = 0
    var alpha: CGFloat = 0

    if resolved.getRed(&red, green: &green, blue: &blue, alpha: &alpha) {
        return (red, green, blue, alpha)
    }

    guard let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) else {
        return (0, 0, 0, 0)
    }

    let converted = resolved.cgColor.converted(to: colorSpace, intent: .defaultIntent, options: nil)
    let components = converted?.components ?? [0, 0, 0, 0]

    if components.count >= 4 {
        return (components[0], components[1], components[2], components[3])
    }

    if components.count == 2 {
        return (components[0], components[0], components[0], components[1])
    }

    return (0, 0, 0, 0)
}
