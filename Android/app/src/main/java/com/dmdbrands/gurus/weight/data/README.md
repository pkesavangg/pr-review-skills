# Data Module

Handles data operations, including API calls and local storage.

## Structure

-   **api/**: Retrofit API interfaces and data sources
-   **repository/**: Repository implementations
-   **services/**: Network and data service implementations
-   **storage/**: Local data storage
    -   **persistence/**: DataStore, SharedPreferences
    -   **db/**: Room database setup, entities, and DAOs

# Database Migration Strategy and Versioning

## Database Version History

-   **Version 1**: Initial database setup with Account, Entry, and Device tables.

## Migration Strategy

-   For development, the database uses `fallbackToDestructiveMigration()`, which means that if the database version is increased, the database will be recreated from scratch.
-   For production, implement proper migration strategies using Room's Migration class. Example:
    ```kotlin
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Migration logic here
        }
    }
    ```
