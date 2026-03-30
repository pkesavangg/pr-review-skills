import Testing
@testable import meApp

@Suite
struct BpmDomainModelTests {

    @Test("AHA classification respects guideline thresholds")
    func ahaClassificationThresholds() {
        #expect(AhaPressureClass.classify(systolic: 119, diastolic: 79) == .normal)
        #expect(AhaPressureClass.classify(systolic: 120, diastolic: 79) == .elevated)
        #expect(AhaPressureClass.classify(systolic: 129, diastolic: 80) == .hypertensionStage1)
        #expect(AhaPressureClass.classify(systolic: 140, diastolic: 89) == .hypertensionStage2)
        #expect(AhaPressureClass.classify(systolic: 181, diastolic: 120) == .hypertensiveCrisis)
    }

    @Test("three reading average label adapts to reading count")
    func threeReadingAverageLabels() {
        #expect(ThreeReadingAverage.displayLabel(for: 1) == "last reading")
        #expect(ThreeReadingAverage.displayLabel(for: 2) == "two entry average")
        #expect(ThreeReadingAverage.displayLabel(for: 3) == "three entry average")
        #expect(ThreeReadingAverage.displayLabel(for: 5) == "three entry average")
    }
}
