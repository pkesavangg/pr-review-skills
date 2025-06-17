# MeApp Android Project

This project follows Clean Architecture principles and is organized into the following main modules:

## Project Structure

### Core Module

-   `config/` - App-wide constants and configuration
-   `shared/` - Common extensions and helper functions
-   `di/` - Dependency Injection modules (Hilt)
-   `navigation/` - Navigation components and route definitions
-   `network/` - Network clients (Retrofit, OkHttp)
-   `services/` - Global utility services
-   `logging/` - Logging utilities

### Domain Module

-   `model/` - Domain-level data classes
-   `interfaces/` - Interface contracts (UseCases, Repositories)
-   `repository/` - Repository interfaces
-   `services/` - Abstract service contracts
-   `enum/` - Domain-level enums and sealed classes

### Data Module

-   `api/` - Retrofit API interfaces
-   `repository/` - Repository implementations
-   `services/` - Network and data service implementations
-   `storage/` - Data storage implementations
-   `model/` - Data layer models

### Features Module

-   `common/` - Shared UI components and logic
-   `auth/` - Authentication features
-   `login/` - Login functionality
-   `sample/` - Sample feature implementation

### Theme Module

-   Contains app-wide theming and styling

### Resources Module

-   Contains app resources (strings, colors, etc.)

### Tests Module

-   Contains all test implementations

## Architecture Guidelines

1. **Clean Architecture**

    - Domain layer is independent of other layers
    - Data layer implements domain interfaces
    - Features layer uses domain models and interfaces

2. **Dependency Injection**

    - Uses Hilt for dependency injection
    - Modules are organized by feature and layer

3. **Navigation**

    - Uses Navigation Component
    - Routes are defined in core module

4. **Testing**
    - Unit tests for domain and data layers
    - UI tests for features
    - Integration tests for critical flows

## Development Guidelines

1. **Code Management**

    - Always check if a file exists before creating a new one
    - Read existing files thoroughly before making changes
    - Review all changes carefully before committing
    - Do not remove core business logic unnecessarily
    - Remove duplicate methods that serve the same purpose
    - Clean up redundant code while preserving functionality

2. **Build Process**

    - Run build after every code change
    - Resolve build issues immediately
    - Ensure build succeeds before proceeding
    - Fix any compilation errors or warnings

3. **Code Quality**

    - Keep domain models pure and independent
    - Use interfaces for repository and service contracts
    - Implement proper error handling
    - Follow SOLID principles
    - Write tests for critical functionality
    - Use proper naming conventions
    - Document complex logic
    - Keep features modular and independent

4. **Import Management**

    - Ensure all necessary imports are added correctly
    - Remove unused imports
    - Organize imports according to project standards
    - Use static imports where appropriate

5. **Code Review Process**

    - Review changes for architectural compliance
    - Verify business logic preservation
    - Check for code duplication
    - Ensure proper error handling
    - Validate test coverage

6. **Resource Management**

    - Check the res/drawable folder for existing vector assets before adding new Android vector drawables.
    - Reuse assets when applicable to reduce redundancy.
    - Follow naming conventions for vector assets (e.g., ic_feature_name.xml).
    - Remove unused or duplicate assets during refactors.

7. **Android Components**
    - Use Jetpack Compose for all new UI implementations
    - Follow Material Design 3 guidelines for component usage
    - Use latest AndroidX libraries instead of older Android Support libraries
    - Implement proper lifecycle management for all components
    - Handle configuration changes appropriately
    - Use ViewModels for UI state management
    - Follow Android best practices for memory management
    - Implement proper error handling for Android-specific operations

## Compose Preview & Composable Rules (UPDATED)

1. **All Compose previews must use the `@PreviewTheme` annotation.**
2. **All previews must be wrapped in `MeAppTheme { ... }`** to ensure correct colors, typography, and spacing.
3. **All styles in previews and all composables must use tokens from `MeAppTheme`** (e.g., `MeAppTheme.colorScheme`, `MeAppTheme.typography`, `MeAppTheme.spacing`).
4. **All previews must support both light and dark themes** (as provided by `@PreviewTheme`).
5. **Do not use hardcoded colors, text styles, animation or spacing in any composable or preview. Always use tokens from the theme.**
6. **Example:**

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
        ) {
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
