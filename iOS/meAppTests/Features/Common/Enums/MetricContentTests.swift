@testable import meApp
import Testing

/// Tests for `MetricContentRepository.content(for:)`.
///
/// Exercises every `BodyMetric` case and asserts the returned `MetricDetailContent`
/// is fully populated (header, messages, resources) with the exact values the
/// source repository dictates.
@Suite(.serialized)
struct MetricContentTests {
    /// Expected header + message/resource counts per metric, mirroring
    /// `MetricContentRepository.content(for:)` exactly.
    private struct Expectation {
        let header: String
        let messageCount: Int
        let resourceCount: Int
    }

    private static let expectations: [BodyMetric: Expectation] = [
        .weight: Expectation(header: "Why Weight?", messageCount: 2, resourceCount: 2),
        .bmi: Expectation(header: "Why BMI?", messageCount: 2, resourceCount: 2),
        .bodyFat: Expectation(header: "Why Body Fat?", messageCount: 2, resourceCount: 2),
        .muscleMass: Expectation(header: "Why Muscle Mass?", messageCount: 2, resourceCount: 2),
        .water: Expectation(header: "Why Body Water?", messageCount: 2, resourceCount: 2),
        .pulse: Expectation(header: "Why Heart Rate?", messageCount: 3, resourceCount: 2),
        .boneMass: Expectation(header: "Why Bone Mass?", messageCount: 3, resourceCount: 2),
        .visceralFatLevel: Expectation(header: "Why Visceral Fat?", messageCount: 3, resourceCount: 1),
        .subcutaneousFatPercent: Expectation(header: "Why Subcutaneous Fat?", messageCount: 3, resourceCount: 2),
        .proteinPercent: Expectation(header: "Why Protein?", messageCount: 2, resourceCount: 2),
        .skeletalMusclePercent: Expectation(header: "Why Skeletal Muscle?", messageCount: 2, resourceCount: 2),
        .bmr: Expectation(header: "Why BMR?", messageCount: 3, resourceCount: 2),
        .metabolicAge: Expectation(header: "Why Metabolic Age?", messageCount: 2, resourceCount: 2)
    ]

    @Test("every BodyMetric case returns fully populated content")
    func everyMetricHasPopulatedContent() {
        for metric in BodyMetric.allCases {
            let content = MetricContentRepository.content(for: metric)

            #expect(!content.header.isEmpty, "\(metric) header should not be empty")
            #expect(!content.messages.isEmpty, "\(metric) messages should not be empty")
            #expect(!content.resources.isEmpty, "\(metric) resources should not be empty")

            for message in content.messages {
                #expect(!message.isEmpty, "\(metric) should not contain an empty message")
            }

            for resource in content.resources {
                #expect(!resource.title.isEmpty, "\(metric) resource title should not be empty")
                #expect(
                    resource.link.hasPrefix("https://") || resource.link.hasPrefix("http://"),
                    "\(metric) resource link should be a web URL: \(resource.link)"
                )
            }
        }
    }

    @Test("each metric returns the exact header and message/resource counts")
    func metricContentMatchesSource() {
        for metric in BodyMetric.allCases {
            guard let expected = Self.expectations[metric] else {
                Issue.record("Missing expectation for \(metric)")
                continue
            }

            let content = MetricContentRepository.content(for: metric)

            #expect(content.header == expected.header, "\(metric) header mismatch")
            #expect(
                content.messages.count == expected.messageCount,
                "\(metric) message count mismatch"
            )
            #expect(
                content.resources.count == expected.resourceCount,
                "\(metric) resource count mismatch"
            )
        }
    }

    @Test("headers are unique across metrics")
    func headersAreUnique() {
        let headers = BodyMetric.allCases.map { MetricContentRepository.content(for: $0).header }
        #expect(Set(headers).count == headers.count, "Each metric should have a distinct header")
    }

    @Test("expectation table covers all metric cases")
    func expectationTableIsComplete() {
        #expect(Self.expectations.count == BodyMetric.allCases.count)
    }

    @Test("spot-check known values for weight and pulse")
    func spotCheckKnownValues() {
        let weight = MetricContentRepository.content(for: .weight)
        #expect(weight.header == "Why Weight?")
        #expect(weight.resources.first?.title == "Harvard School of Public Health")

        let pulse = MetricContentRepository.content(for: .pulse)
        #expect(pulse.header == "Why Heart Rate?")
        #expect(pulse.messages.count == 3)
    }
}
