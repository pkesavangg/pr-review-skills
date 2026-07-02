import Foundation
@testable import meApp
import SQLite3

// MARK: - SQLiteTestHelper

enum SQLiteTestHelper {
    struct OpStackRow {
        let userId: String
        let timestamp: String
        let opType: String
        let weight: Int32
        var bodyFat: Int32?
        var muscleMass: Int32?
        var water: Int32?
        var bmi: Int32?
        var source: String?
        var attempts: Int32 = 0
    }

    struct OpStackMetricRow {
        let userId: String
        let timestamp: String
        var bmr: Int32?
        var metabolicAge: Int32?
        var proteinPercent: Int32?
        var pulse: Int32?
        var skeletalMusclePercent: Int32?
        var subcutaneousFatPercent: Int32?
        var visceralFatLevel: Int32?
        var boneMass: Int32?
    }

    static func createEmptyDatabase(at path: String) {
        var db: OpaquePointer?
        sqlite3_open(path, &db)
        sqlite3_close(db)
    }

    static func createDatabase(
        at path: String,
        entries: [OpStackRow],
        metrics: [OpStackMetricRow] = []
    ) {
        var db: OpaquePointer?
        sqlite3_open(path, &db)

        // Create opStack table
        let opStackSQL = """
            CREATE TABLE opStack (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                entryTimestamp TEXT NOT NULL,
                operationType TEXT,
                weight INTEGER DEFAULT 0,
                bodyFat INTEGER,
                muscleMass INTEGER,
                water INTEGER,
                bmi INTEGER,
                source TEXT,
                attempts INTEGER DEFAULT 0
            )
        """
        sqlite3_exec(db, opStackSQL, nil, nil, nil)

        // Create opStack_metric table
        let opStackMetricSQL = """
            CREATE TABLE opStack_metric (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                entryTimestamp TEXT NOT NULL,
                bmr INTEGER,
                metabolicAge INTEGER,
                proteinPercent INTEGER,
                pulse INTEGER,
                skeletalMusclePercent INTEGER,
                subcutaneousFatPercent INTEGER,
                visceralFatLevel INTEGER,
                boneMass INTEGER
            )
        """
        sqlite3_exec(db, opStackMetricSQL, nil, nil, nil)

        // Insert entries
        for entry in entries {
            let sql = buildInsertSQL(for: entry)
            sqlite3_exec(db, sql, nil, nil, nil)
        }

        // Insert metrics
        for metric in metrics {
            let sql = buildInsertSQL(for: metric)
            sqlite3_exec(db, sql, nil, nil, nil)
        }

        sqlite3_close(db)
    }

    static func createDatabaseWithNullOpType(at path: String, userId: String, timestamp: String, weight: Int32) {
        var db: OpaquePointer?
        sqlite3_open(path, &db)

        let opStackSQL = """
            CREATE TABLE opStack (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                entryTimestamp TEXT NOT NULL,
                operationType TEXT,
                weight INTEGER DEFAULT 0,
                bodyFat INTEGER,
                muscleMass INTEGER,
                water INTEGER,
                bmi INTEGER,
                source TEXT,
                attempts INTEGER DEFAULT 0
            )
        """
        sqlite3_exec(db, opStackSQL, nil, nil, nil)

        let opStackMetricSQL = """
            CREATE TABLE opStack_metric (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                entryTimestamp TEXT NOT NULL,
                bmr INTEGER,
                metabolicAge INTEGER,
                proteinPercent INTEGER,
                pulse INTEGER,
                skeletalMusclePercent INTEGER,
                subcutaneousFatPercent INTEGER,
                visceralFatLevel INTEGER,
                boneMass INTEGER
            )
        """
        sqlite3_exec(db, opStackMetricSQL, nil, nil, nil)

        // Insert with NULL operationType
        let sql = "INSERT INTO opStack (userId, entryTimestamp, operationType, weight) VALUES ('\(userId)', '\(timestamp)', NULL, \(weight))"
        sqlite3_exec(db, sql, nil, nil, nil)

        sqlite3_close(db)
    }

    static func cleanup(path: String) {
        try? FileManager.default.removeItem(atPath: path)
    }

    // MARK: - SQL Builders

    private static func buildInsertSQL(for entry: OpStackRow) -> String {
        var columns = ["userId", "entryTimestamp", "operationType", "weight", "attempts"]
        var values = ["'\(entry.userId)'", "'\(entry.timestamp)'", "'\(entry.opType)'", "\(entry.weight)", "\(entry.attempts)"]

        if let bodyFat = entry.bodyFat {
            columns.append("bodyFat"); values.append("\(bodyFat)")
        }
        if let muscleMass = entry.muscleMass {
            columns.append("muscleMass"); values.append("\(muscleMass)")
        }
        if let water = entry.water {
            columns.append("water"); values.append("\(water)")
        }
        if let bmi = entry.bmi {
            columns.append("bmi"); values.append("\(bmi)")
        }
        if let source = entry.source {
            columns.append("source"); values.append("'\(source)'")
        }

        return "INSERT INTO opStack (\(columns.joined(separator: ", "))) VALUES (\(values.joined(separator: ", ")))"
    }

    private static func buildInsertSQL(for metric: OpStackMetricRow) -> String {
        var columns = ["userId", "entryTimestamp"]
        var values = ["'\(metric.userId)'", "'\(metric.timestamp)'"]

        if let bmr = metric.bmr {
            columns.append("bmr"); values.append("\(bmr)")
        }
        if let metabolicAge = metric.metabolicAge {
            columns.append("metabolicAge"); values.append("\(metabolicAge)")
        }
        if let proteinPercent = metric.proteinPercent {
            columns.append("proteinPercent"); values.append("\(proteinPercent)")
        }
        if let pulse = metric.pulse {
            columns.append("pulse"); values.append("\(pulse)")
        }
        if let skeletalMusclePercent = metric.skeletalMusclePercent {
            columns.append("skeletalMusclePercent"); values.append("\(skeletalMusclePercent)")
        }
        if let subcutaneousFatPercent = metric.subcutaneousFatPercent {
            columns.append("subcutaneousFatPercent"); values.append("\(subcutaneousFatPercent)")
        }
        if let visceralFatLevel = metric.visceralFatLevel {
            columns.append("visceralFatLevel"); values.append("\(visceralFatLevel)")
        }
        if let boneMass = metric.boneMass {
            columns.append("boneMass"); values.append("\(boneMass)")
        }

        return "INSERT INTO opStack_metric (\(columns.joined(separator: ", "))) VALUES (\(values.joined(separator: ", ")))"
    }
}
