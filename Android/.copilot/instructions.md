# MeApp Android Project Copilot Instructions

This document provides guidelines for working on the MeApp Android project, derived from the existing `CURSOR_RULES.md`. These instructions are specifically for Android development within this project.

## Project Structure

The project follows Clean Architecture principles. Key modules include:

### Core Module
-   `config/`: App-wide constants and configuration.
-   `shared/`: Common extensions and helper functions.
-   `di/`: Dependency Injection modules (Hilt).
-   `navigation/`: Navigation components and route definitions.
-   `network/`: Network clients (Retrofit, OkHttp).
-   `services/`: Global utility services.
-   `logging/`: Logging utilities.

### Domain Module
-   `model/`: Domain-level data classes.
-   `interfaces/`: Interface contracts (UseCases, Repositories).
-   `repository/`: Repository interfaces.
-   `services/`: Abstract service contracts.
-   `enum/`: Domain-level enums and sealed classes.

### Data Module
-   `api/`: Retrofit API interfaces.
-   `repository/`: Repository implementations.
-   `services/`: Network and data service implementations.
-   `storage/`: Data storage implementations.
-   `model/`: Data layer models.

### Features Module
-   `common/`: Shared UI components and logic.
-   Individual feature modules (e.g., `auth/`, `login/`, `sample/`).

### Theme Module
-   Contains app-wide theming and styling.

### Resources Module
-   Contains app resources (strings, colors, etc.).

### Tests Module
-   Contains all test implementations.

## Architecture Guidelines

1.  **Clean Architecture**:
    *   Domain layer is independent.
    *   Data layer implements domain interfaces.
    *   Features layer uses domain models and interfaces.
2.  **Dependency Injection**:
    *   Use Hilt.
    *   Organize modules by feature and layer.
3.  **Navigation**:
    *   Use Navigation Component.
    *   Define routes in the core module.
4.  **Testing**:
    *   Unit tests for domain and data layers.
    *   UI tests for features.
    *   Integration tests for critical flows.

## Development Guidelines

1.  **Code Management**:
    *   Verify file existence before creation.
    *   Read files thoroughly before editing.
    *   Review changes carefully.
    *   Avoid removing core business logic without strong justification.
    *   Eliminate duplicate methods.
    *   Refactor redundant code while preserving functionality.
2.  **Build Process**:
    *   Run build after every code change.
    *   Resolve build issues immediately.
    *   Ensure build succeeds before proceeding.
    *   Fix all compilation errors and warnings.
3.  **Code Quality**:
    *   Maintain pure and independent domain models.
    *   Use interfaces for repository and service contracts.
    *   Implement robust error handling.
    *   Adhere to SOLID principles.
    *   Write tests for critical functionality.
    *   Follow consistent naming conventions.
    *   Document complex logic.
    *   Keep features modular and independent.
4.  **Import Management**:
    *   Ensure all necessary imports are added.
    *   Remove unused imports.
    *   Organize imports according to project standards.
    *   Use static imports judiciously.
5.  **Code Review Process**:
    *   Verify architectural compliance.
    *   Confirm preservation of business logic.
    *   Check for code duplication.
    *   Ensure proper error handling.
    *   Validate test coverage.
6.  **Resource Management**:
    *   Check `res/drawable` for existing vector assets before adding new ones.
    *   Reuse assets where possible.
    *   Follow naming conventions (e.g., `ic_feature_name.xml`).
    *   Remove unused or duplicate assets.
7.  **Android Components**:
    *   Use Jetpack Compose for all new UI.
    *   Follow Material Design 3 guidelines.
    *   Use latest AndroidX libraries.
    *   Implement proper lifecycle management.
    *   Handle configuration changes correctly.
    *   Use ViewModels for UI state management.
    *   Follow Android best practices for memory management.
    *   Implement proper error handling for Android-specific operations.

## Compose Preview & Composable Rules

1.  **All Compose previews MUST use the `@PreviewTheme` annotation.**
2.  **All previews MUST be wrapped in `MeAppTheme { ... }`** to ensure correct colors, typography, and spacing.
3.  **All styles in previews and all composables MUST use tokens from `MeAppTheme`** (e.g., `MeAppTheme.colorScheme`, `MeAppTheme.typography`, `MeAppTheme.spacing`).
4.  **All previews MUST support both light and dark themes** (as provided by `@PreviewTheme`).
5.  **DO NOT use hardcoded colors, text styles, animation, or spacing in any composable or preview. Always use tokens from the theme.**
6.  **Example:**
    ```kotlin
    @PreviewTheme
    @Composable
    fun ExampleButtonPreview() {
        MeAppTheme {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeAppTheme.colorScheme.primaryAction
                )
            )
            {
                Text(
                    "Save",
                    style = MeAppTheme.typography.button1,
                    color = MeAppTheme.colorScheme.inverse
                )
            }
        }
    }
    ```

This ensures all composables and previews match the app's design system and support both light and dark modes.
