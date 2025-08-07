# Dependency Injection (DI) Modules

This directory contains all Dagger Hilt modules used for dependency injection in the MeApp Android application. These modules provide and configure dependencies for networking, database, notification, theming, and core services.

## Overview

- **Dagger Hilt** is used as the dependency injection framework, enabling modular, testable, and maintainable code.
- Each module in this directory is responsible for providing a specific set of dependencies, such as API services, DAOs, network clients, notification handlers, and theme repositories.

## Modules

- **APIModule**: Provides API service interfaces (e.g., Auth, User) using a shared HTTP client.
- **DatabaseModule**: Provides Room DAOs for accessing local database tables.
- **NetworkModule**: Configures and provides OkHttp clients, interceptors, and network utilities.
- **NotificationModule**: Provides notification-related services and handlers.
- **ServiceModule**: Provides core app services, such as event and notification managers.
- **ThemeModule**: Provides theme-related repositories and data stores.

## Best Practices

- Use constructor injection wherever possible for better testability.
- Keep module responsibilities focused and avoid circular dependencies.
- Refer to each module's KDoc for details on provided dependencies.
