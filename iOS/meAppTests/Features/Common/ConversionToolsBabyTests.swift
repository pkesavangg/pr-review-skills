@testable import meApp
import Testing

@Suite(.serialized)
struct ConversionToolsBabyTests {

    // MARK: - Constants

    @Test("decigramsPerOunce is 283.5")
    func decigramsPerOunce() {
        #expect(ConversionTools.decigramsPerOunce == 283.5)
    }

    @Test("decigramsPerPound is 4535.924")
    func decigramsPerPound() {
        #expect(ConversionTools.decigramsPerPound == 4535.924)
    }

    @Test("decigramsPerKg is 10000.0")
    func decigramsPerKg() {
        #expect(ConversionTools.decigramsPerKg == 10000.0)
    }

    @Test("mmPerInch is 25.4")
    func mmPerInch() {
        #expect(ConversionTools.mmPerInch == 25.4)
    }

    // MARK: - kg <-> decigrams

    @Test("1 kg converts to 10000 decigrams")
    func kgToDecigrams() {
        #expect(ConversionTools.convertBabyKgToDecigrams(1.0) == 10000)
    }

    @Test("10000 decigrams converts to 1.0 kg")
    func decigramsToKg() {
        #expect(ConversionTools.convertBabyDecigramsToKg(10000) == 1.0)
    }

    @Test("kg round-trip preserves value within tolerance")
    func kgRoundTrip() {
        let original = 3.456
        let decigrams = ConversionTools.convertBabyKgToDecigrams(original)
        let result = ConversionTools.convertBabyDecigramsToKg(decigrams)
        #expect(abs(result - original) < 0.001)
    }

    @Test("0 kg converts to 0 decigrams")
    func zeroKg() {
        #expect(ConversionTools.convertBabyKgToDecigrams(0.0) == 0)
        #expect(ConversionTools.convertBabyDecigramsToKg(0) == 0.0)
    }

    // MARK: - lbs+oz <-> decigrams

    @Test("7 lbs 8 oz round-trip")
    func lbsOzRoundTrip() {
        let decigrams = ConversionTools.convertBabyLbsOzToDecigrams(lbs: 7, oz: 8.0)
        let result = ConversionTools.convertBabyDecigramsToLbsOz(decigrams)
        #expect(result.lbs == 7)
        #expect(abs(result.oz - 8.0) < 0.2)
    }

    @Test("16 oz equals 1 lb in decigrams")
    func sixteenOzEqualsOneLb() {
        let fromOz = ConversionTools.convertBabyLbsOzToDecigrams(lbs: 0, oz: 16.0)
        let fromLb = ConversionTools.convertBabyLbsOzToDecigrams(lbs: 1, oz: 0.0)
        #expect(fromOz == fromLb)
    }

    @Test("0 lbs 0 oz converts to 0 decigrams")
    func zeroLbsOz() {
        #expect(ConversionTools.convertBabyLbsOzToDecigrams(lbs: 0, oz: 0.0) == 0)
    }

    @Test("Max reasonable baby weight ~30 lbs converts correctly")
    func maxBabyWeight() {
        let decigrams = ConversionTools.convertBabyLbsOzToDecigrams(lbs: 30, oz: 0.0)
        let result = ConversionTools.convertBabyDecigramsToLbsOz(decigrams)
        #expect(result.lbs == 30)
        #expect(abs(result.oz) < 0.2)
    }

    // MARK: - decimal lb <-> decigrams

    @Test("decimal lb round-trip")
    func lbRoundTrip() {
        let original = 7.5
        let decigrams = ConversionTools.convertBabyLbToDecigrams(original)
        let result = ConversionTools.convertBabyDecigramsToLb(decigrams)
        #expect(abs(result - original) < 0.001)
    }

    @Test("0 lb converts to 0 decigrams")
    func zeroLb() {
        #expect(ConversionTools.convertBabyLbToDecigrams(0.0) == 0)
        #expect(ConversionTools.convertBabyDecigramsToLb(0) == 0.0)
    }

    // MARK: - inches <-> mm

    @Test("inches to mm round-trip")
    func inchesRoundTrip() {
        let original = 20.5
        let mm = ConversionTools.convertBabyInchesToMm(original)
        let result = ConversionTools.convertBabyMmToInches(mm)
        #expect(abs(result - original) < 0.1)
    }

    @Test("0 inches converts to 0 mm")
    func zeroInches() {
        #expect(ConversionTools.convertBabyInchesToMm(0.0) == 0)
        #expect(ConversionTools.convertBabyMmToInches(0) == 0.0)
    }

    // MARK: - cm <-> mm

    @Test("cm to mm round-trip")
    func cmRoundTrip() {
        let original = 52.3
        let mm = ConversionTools.convertBabyCmToMm(original)
        let result = ConversionTools.convertBabyMmToCm(mm)
        #expect(abs(result - original) < 0.1)
    }

    @Test("0 cm converts to 0 mm")
    func zeroCm() {
        #expect(ConversionTools.convertBabyCmToMm(0.0) == 0)
        #expect(ConversionTools.convertBabyMmToCm(0) == 0.0)
    }

    @Test("1 cm converts to 10 mm")
    func oneCm() {
        #expect(ConversionTools.convertBabyCmToMm(1.0) == 10)
    }

    // MARK: - Negative inputs

    @Test("Negative kg produces negative decigrams")
    func negativeKg() {
        let result = ConversionTools.convertBabyKgToDecigrams(-1.0)
        #expect(result == -10000)
    }

    @Test("Negative inches produces negative mm")
    func negativeInches() {
        let result = ConversionTools.convertBabyInchesToMm(-5.0)
        #expect(result == -127)
    }
}
