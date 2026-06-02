//
//  ProductSelection.swift
//  meApp
//

import Foundation

/// Represents a single selectable item in the product-type header dropdown.
///
/// - myWeight: the primary user's weight/body-comp data (scale devices)
/// - myBloodPressure: the primary user's BPM data
/// - baby: an individual child profile, shown by the child's name
enum ProductSelection: Equatable, Hashable, Identifiable {

    case myWeight
    case myBloodPressure
    case baby(profile: BabyProfile)

    // MARK: - Identifiable

    var id: String {
        switch self {
        case .myWeight:           return "my_weight"
        case .myBloodPressure:    return "my_blood_pressure"
        case .baby(let profile):  return "baby_\(profile.id)"
        }
    }

    // MARK: - Display

    var displayName: String {
        switch self {
        case .myWeight:           return ProductTypeStrings.myWeight
        case .myBloodPressure:    return ProductTypeStrings.myBloodPressure
        case .baby(let profile):  return profile.name
        }
    }

    /// Title shown in the History screen header.
    var historyTitle: String {
        switch self {
        case .myWeight:           return ProductTypeStrings.weightHistory
        case .myBloodPressure:    return ProductTypeStrings.bloodPressure
        case .baby(let profile):  return profile.name
        }
    }

    /// Title shown in the Dashboard screen header.
    var dashboardTitle: String {
        switch self {
        case .myWeight:           return ProductTypeStrings.myWeight
        case .myBloodPressure:    return ProductTypeStrings.myBP
        case .baby(let profile):  return profile.name
        }
    }

    /// Title shown in the Manual Entry screen header.
    var entryTitle: String {
        switch self {
        case .myWeight:           return ProductTypeStrings.weightEntry
        case .myBloodPressure:    return ProductTypeStrings.bpEntry
        case .baby(let profile):  return profile.name
        }
    }

    var iconName: String {
        switch self {
        case .myWeight:           return "scalemass"
        case .myBloodPressure:    return "heart.fill"
        case .baby:               return "figure.2.and.child.holdinghands"
        }
    }

    // MARK: - Convenience

    /// True if this item represents the primary user (not a baby).
    var isPersonalSelection: Bool {
        switch self {
        case .myWeight, .myBloodPressure: return true
        case .baby: return false
        }
    }

    /// The DeviceType to use when saving or filtering entries for this selection.
    var deviceType: DeviceType {
        switch self {
        case .myWeight:           return .scale
        case .myBloodPressure:    return .bpm
        case .baby:               return .babyScale
        }
    }

    /// The dashboard entry type this selection maps to.
    var entryType: EntryType {
        switch self {
        case .myBloodPressure: return .bpm
        case .myWeight: return .scale
        case .baby: return .baby
        }
    }

    /// The unified entries API `category` this selection maps to (`weight`/`bp`/`baby`).
    /// Used to scope reads, cursor pagination, and CSV export to the active product.
    var entriesCategory: String? {
        EntryCategory(entryType: entryType)?.rawValue
    }
}
