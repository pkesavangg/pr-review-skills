import Foundation

struct EntrySummaryCacheEntry {
    let datasetSignature: Int
    let daily: [BathScaleWeightSummary]
    let monthly: [BathScaleWeightSummary]
}

struct EntryBabySummaryCacheEntry {
    let datasetSignature: Int
    let daily: [BathScaleWeightSummary]
    let monthly: [BathScaleWeightSummary]
}

struct EntryStreakCacheEntry {
    let datasetSignature: Int
    let streak: Streak
}

struct EntryProgressCacheEntry {
    let datasetSignature: Int
    let goalInitialWeight: Int?
    let progress: Progress
}

struct EntryMetricAccumulator {
    private(set) var sum: Double = 0
    private(set) var count: Int = 0

    mutating func add(_ value: Double?) {
        guard let value, value > 0 else { return }
        sum += value
        count += 1
    }

    var average: Double? {
        guard case 1... = count else { return nil }
        return sum / Double(count)
    }
}

struct EntrySummaryBucket {
    let accountId: String
    let period: String
    var latestTimestamp: String = ""
    var count: Int = 0
    var weightSum: Double = 0
    var weightCount: Int = 0
    var bodyFat = EntryMetricAccumulator()
    var muscleMass = EntryMetricAccumulator()
    var water = EntryMetricAccumulator()
    var bmi = EntryMetricAccumulator()
    var bmr = EntryMetricAccumulator()
    var metabolicAge = EntryMetricAccumulator()
    var proteinPercent = EntryMetricAccumulator()
    var pulse = EntryMetricAccumulator()
    var skeletalMusclePercent = EntryMetricAccumulator()
    var subcutaneousFatPercent = EntryMetricAccumulator()
    var visceralFatLevel = EntryMetricAccumulator()
    var boneMass = EntryMetricAccumulator()
    var impedance = EntryMetricAccumulator()
    var systolic = EntryMetricAccumulator()
    var diastolic = EntryMetricAccumulator()
    var meanArterial = EntryMetricAccumulator()

    init(accountId: String, period: String) {
        self.accountId = accountId
        self.period = period
    }

    mutating func add(dto: BathScaleOperationDTO) {
        count += 1
        if let timestamp = dto.entryTimestamp, timestamp > latestTimestamp {
            latestTimestamp = timestamp
        }
        if let weight = dto.weight, weight > 0 {
            weightSum += weight
            weightCount += 1
        }
        bodyFat.add(dto.bodyFat)
        muscleMass.add(dto.muscleMass)
        water.add(dto.water)
        bmi.add(dto.bmi)
        bmr.add(dto.bmr)
        metabolicAge.add(dto.metabolicAge)
        proteinPercent.add(dto.proteinPercent)
        pulse.add(dto.pulse)
        skeletalMusclePercent.add(dto.skeletalMusclePercent)
        subcutaneousFatPercent.add(dto.subcutaneousFatPercent)
        visceralFatLevel.add(dto.visceralFatLevel)
        boneMass.add(dto.boneMass)
        impedance.add(dto.impedance)
        systolic.add(dto.systolic)
        diastolic.add(dto.diastolic)
        meanArterial.add(dto.meanArterial)
    }

    var averagedWeight: Double {
        guard weightCount > 0 else { return 0 }
        return weightSum / Double(weightCount)
    }
}

enum EntrySummaryLoadResult {
    case cached(signature: Int)
    case computed(signature: Int, daily: [BathScaleWeightSummary], monthly: [BathScaleWeightSummary])
}
