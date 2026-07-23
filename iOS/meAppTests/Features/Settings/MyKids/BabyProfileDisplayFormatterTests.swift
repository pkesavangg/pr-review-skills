// Coverage target: units-aware baby profile display formatting (MOB-1605)

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
struct BabyProfileDisplayFormatterTests {

    // MARK: - Birthday

    @Test("birthday returns placeholder when nil")
    func birthday_nil_returnsPlaceholder() {
        #expect(BabyProfileDisplayFormatter.birthday(nil) == MyKidsStrings.Details.empty)
    }

    @Test("birthday formats as month day, year")
    func birthday_date_formatsMonthDayYear() throws {
        var components = DateComponents()
        components.year = 2024
        components.month = 6
        components.day = 10
        let date = try #require(Calendar.current.date(from: components))
        let expected = DateTimeTools.formatter("MMMM d, yyyy").string(from: date)
        #expect(BabyProfileDisplayFormatter.birthday(date) == expected)
        #expect(expected.contains("2024"))
    }

    // MARK: - Biological sex

    @Test("biologicalSex maps lowercase raw values to capitalized display")
    func biologicalSex_lowercaseRaw_capitalized() {
        #expect(BabyProfileDisplayFormatter.biologicalSex("male") == "Male")
        #expect(BabyProfileDisplayFormatter.biologicalSex("female") == "Female")
        #expect(BabyProfileDisplayFormatter.biologicalSex("private") == "Private")
    }

    @Test("biologicalSex tolerates legacy capitalized values")
    func biologicalSex_legacyCapitalized_normalized() {
        #expect(BabyProfileDisplayFormatter.biologicalSex("Male") == "Male")
        #expect(BabyProfileDisplayFormatter.biologicalSex("FEMALE") == "Female")
    }

    @Test("biologicalSex returns placeholder for nil/empty/unknown")
    func biologicalSex_invalid_returnsPlaceholder() {
        #expect(BabyProfileDisplayFormatter.biologicalSex(nil) == MyKidsStrings.Details.empty)
        #expect(BabyProfileDisplayFormatter.biologicalSex("") == MyKidsStrings.Details.empty)
        #expect(BabyProfileDisplayFormatter.biologicalSex("unknown") == MyKidsStrings.Details.empty)
    }

    // MARK: - Birth length

    @Test("birthLength imperial shows inches, trimming whole numbers")
    func birthLength_imperial_inches() {
        #expect(BabyProfileDisplayFormatter.birthLength(inches: 25.8, units: .imperialLbOz) == "25.8 in")
        #expect(BabyProfileDisplayFormatter.birthLength(inches: 20, units: .imperialLbDecimal) == "20 in")
    }

    @Test("birthLength metric converts inches to centimeters")
    func birthLength_metric_centimeters() {
        // 20 in -> 508 mm -> 50.8 cm
        #expect(BabyProfileDisplayFormatter.birthLength(inches: 20, units: .metric) == "50.8 cm")
    }

    @Test("birthLength returns placeholder for nil/zero")
    func birthLength_missing_returnsPlaceholder() {
        #expect(BabyProfileDisplayFormatter.birthLength(inches: nil, units: .imperialLbOz) == MyKidsStrings.Details.empty)
        #expect(BabyProfileDisplayFormatter.birthLength(inches: 0, units: .metric) == MyKidsStrings.Details.empty)
    }

    // MARK: - Birth weight

    @Test("birthWeight lb-oz shows both components, trimming whole ounces")
    func birthWeight_lbOz() {
        #expect(BabyProfileDisplayFormatter.birthWeight(lbs: 16, oz: 8, units: .imperialLbOz) == "16 lb 8 oz")
        #expect(BabyProfileDisplayFormatter.birthWeight(lbs: 7, oz: 4.5, units: .imperialLbOz) == "7 lb 4.5 oz")
    }

    @Test("birthWeight lb-decimal folds ounces into decimal pounds")
    func birthWeight_lbDecimal() {
        #expect(BabyProfileDisplayFormatter.birthWeight(lbs: 16, oz: 8, units: .imperialLbDecimal) == "16.5 lb")
    }

    @Test("birthWeight metric converts to kilograms")
    func birthWeight_metric() {
        // 16 lb 8 oz -> 264 oz -> 74844 decigrams -> 7.48 kg
        #expect(BabyProfileDisplayFormatter.birthWeight(lbs: 16, oz: 8, units: .metric) == "7.48 kg")
    }

    @Test("birthWeight returns placeholder when no weight recorded")
    func birthWeight_missing_returnsPlaceholder() {
        #expect(BabyProfileDisplayFormatter.birthWeight(lbs: nil, oz: nil, units: .imperialLbOz) == MyKidsStrings.Details.empty)
        #expect(BabyProfileDisplayFormatter.birthWeight(lbs: 0, oz: 0, units: .metric) == MyKidsStrings.Details.empty)
    }
}
