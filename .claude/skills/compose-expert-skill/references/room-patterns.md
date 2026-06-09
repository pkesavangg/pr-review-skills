# Room Patterns — MeApp Android

## Entity conventions

```kotlin
@Entity(
    tableName = "foo",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["accountId"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId"])],
)
data class FooEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val isSynced: Boolean = false,
)
```

## DAO query rules

- `@Transaction` + `@Relation` queries **MUST** use `SELECT *` (not `SELECT e.*`) — Room needs full parent columns.
- Filter by related table using a subquery (`id IN (SELECT...)`) instead of INNER JOIN when returning a `@Transaction` result.
- Aggregated queries (`GROUP BY`, `AVG`) **CAN** use JOINs and column aliases.
- For large DAOs (>500 lines), create a separate read-only DAO (e.g. `HistoryDao`).

```kotlin
// ✅ Correct — SELECT * for @Transaction
@Transaction
@Query("SELECT * FROM entry WHERE accountId = :accountId")
fun getEntriesByAccount(accountId: String): Flow<List<PopulatedEntry>>

// ✅ Subquery instead of JOIN for @Relation queries
@Query("SELECT * FROM entry WHERE id IN (SELECT entryId FROM scale_entry WHERE weight > :minWeight)")
fun getHeavyEntries(minWeight: Double): Flow<List<PopulatedEntry>>
```

## Migrations

- Every schema change bumps `AppDatabase.version` and adds a `Migration(from, to)`.
- Backfill new NOT NULL columns with a sensible default in the migration SQL.
- Register migrations in `.addMigrations(...)` in `AppDatabase.getInstance()`.

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE foo ADD COLUMN newField TEXT NOT NULL DEFAULT ''")
    }
}
```

- Export schema: `exportSchema = true` in `@Database`. Committed JSON schema files are in `app/schemas/`.
- **Hard stop**: never bump the DB version without a migration. `fallbackToDestructiveMigration(false)` is set — a missing migration crashes the app.

## TypeConverters

- `JsonConverter` — handles `List<String>` ↔ JSON string (via Gson).
- `DateConverter` — handles `Date` ↔ Long.
- `WeightUnitConverter` — handles `WeightUnit` enum ↔ String.
- Registered at `@Database` level via `@TypeConverters(...)`.

## Singleton Room instance

```kotlin
// Always use the singleton
val db = AppDatabase.getInstance(context)
```

DAOs are provided via Hilt from `DatabaseModule.kt` — never instantiate them directly.
