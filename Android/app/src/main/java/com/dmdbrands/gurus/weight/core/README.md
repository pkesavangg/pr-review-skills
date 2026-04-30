# Core Module

This module contains app-wide configurations, utilities, and shared components that are used throughout the MeApp Android application. It provides the foundational building blocks for networking, dependency injection, navigation, and global services.

## Structure

- **config/**: App-wide constants and configuration values (e.g., API base URLs, headers).
- **di/**: Dependency injection modules using Dagger Hilt for providing singletons and app-scoped dependencies.
- **navigation/**: Navigation system, route definitions, and back stack management for Compose Navigation.
- **network/**: HTTP client setup, interceptors, connectivity observer, and network utilities.
- **service/**: Global utility services, such as event handling and push notifications.
- **shared/**: Common extensions, helpers, and utilities that are reused across multiple features or modules.

## Key Responsibilities

- Centralizes configuration and constants for easy management.
- Provides dependency injection setup for the entire app.
- Implements a robust navigation system for Jetpack Compose.
- Handles network configuration, connectivity monitoring, and HTTP interceptors.
- Manages global services such as event bus and push notification handling.
- Supplies shared utilities and helpers to avoid code duplication.

## Usage

Import and use the provided modules and services in your feature modules or presentation layer. For example, inject services using Hilt, use navigation routes for Compose navigation, and leverage shared utilities for common tasks.

## Best Practices

- Keep all app-wide logic and configuration in this module.
- Avoid placing feature-specific code here; use the appropriate feature module instead.
- Update this README as new subfolders or responsibilities are added.

---

For more details, see the main project documentation or the README files in each subfolder.
