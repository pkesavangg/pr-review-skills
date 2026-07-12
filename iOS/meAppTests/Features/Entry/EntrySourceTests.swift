//
//  EntrySourceTests.swift
//  meAppTests
//
//  Unit tests for EntrySource.isManualEntry — the edit-permission gate (MOB-1172).
//  Manual entries are fully editable (values + note); device-synced entries are
//  note-only. A nil/empty source is treated as manual (legacy records carried none).
//

@testable import meApp
import Testing

@Suite(.serialized)
struct EntrySourceTests {

    @Test("nil source is treated as manual (legacy records carried no source)")
    func nilSourceIsManual() {
        #expect(EntrySource.isManualEntry(nil))
    }

    @Test("empty or whitespace-only source is treated as manual")
    func emptySourceIsManual() {
        #expect(EntrySource.isManualEntry(""))
        #expect(EntrySource.isManualEntry("   "))
        #expect(EntrySource.isManualEntry("\n\t "))
    }

    @Test("the literal manual source is manual, case- and whitespace-insensitively")
    func manualStringIsManual() {
        #expect(EntrySource.isManualEntry("manual"))
        #expect(EntrySource.isManualEntry("Manual"))
        #expect(EntrySource.isManualEntry("MANUAL"))
        #expect(EntrySource.isManualEntry("  manual  "))
    }

    @Test("device-synced sources are not manual (values are locked, note-only edit)")
    func deviceSyncedSourcesAreNotManual() {
        #expect(!EntrySource.isManualEntry(EntrySource.bluetooth.rawValue))
        #expect(!EntrySource.isManualEntry(EntrySource.bluetoothMonitor.rawValue))
        #expect(!EntrySource.isManualEntry(EntrySource.lcbtScale.rawValue))
        #expect(!EntrySource.isManualEntry(EntrySource.wifiScale.rawValue))
        #expect(!EntrySource.isManualEntry(EntrySource.appsyncScale.rawValue))
    }

    @Test("baby-scale graduation SKU codes count as device-synced")
    func babyGraduationCodesAreNotManual() {
        #expect(!EntrySource.isManualEntry("0220"))
        #expect(!EntrySource.isManualEntry("0222"))
    }
}
