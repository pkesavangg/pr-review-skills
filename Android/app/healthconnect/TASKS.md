# Health Connect Plugin Migration: Ionic/Capacitor → Native Android Library

## Rules

- Each data class must be in its own file in a 'model' subfolder.
- Each enum must be in its own file in an 'enum' subfolder.
- Each interface must be in its own file in an 'interfaces' subfolder.
- Each helper class/object must be in its own file in a 'helper' subfolder.
- Never group multiple top-level types in a single file.

## Overview

This document tracks the migration of the Health Connect plugin from the Ionic/Capacitor implementation to a robust, idiomatic, and reusable native Android library in Kotlin.

---

## Phase 1: Analysis & Planning

### Task 1: Review Existing Plugin

- [x] Analyze Ionic plugin Android code (`@/android`)
- [x] Analyze TypeScript API surface (`@/src`)
- [x] List all Health Connect API interactions, permission flows, data models, and UI/UX flows
- [x] Document all features, edge cases, and background sync logic

### Task 2: Define Android Library API

- [x] Design Kotlin API surface (coroutines, data classes, sealed classes, StateFlow/Flow)
- [x] Specify public API for:
    - [x] Permissions/consent
    - [x] Data read/write/delete
    - [x] Status/availability
    - [x] Background sync
    - [x] UI onboarding/permission flows

---

## Phase 2: Core Library Implementation

### Task 3: Project Setup

- [x] Ensure `com.greatergoods.libs.healthconnect` package
- [~] Set up Gradle, dependencies, and module structure

### Task 4: Data Models & Result Types

- [x] Convert TypeScript interfaces/enums to Kotlin data classes and enums
- [x] Define sealed classes for operation results and errors

### Task 5: Health Connect API Wrappers

- [x] Implement Health Connect API calls using coroutines
- [x] Handle permissions, consent, and error cases robustly
- [x] Provide suspend functions and Flows for all operations

### Task 6: Permission & Consent Handling

- [x] Implement permission checks, requests, and consent flows
- [x] Expose easy-to-use APIs for host apps

### Task 7: Background Sync

- [x] Implement background sync using WorkManager
- [x] Ensure proper permission and error handling

---

## Phase 3: UI Components

### Task 8: Composable/Fragment UI

- [x] Provide a Composable (Jetpack Compose) and/or Fragment for onboarding and permissions
- [x] Follow Material 3 and Compose best practices
- [x] Make UI components easily embeddable in any host app

### Task 9: Unit Tests

- [x] Write comprehensive unit tests for all core logic, API wrappers, and permission handling

---

## Phase 4: Testing

### Task 10: Instrumentation Tests

- [x] Add UI and integration tests for onboarding, permissions, and background sync

---

## Phase 5: Documentation & Packaging

### Task 11: Documentation

- [x] Add KDoc for all public APIs
- [x] Provide usage examples and integration guide

### Task 12: Packaging

- [~] Ensure the library is modular, independent, and ready for distribution (e.g., via Maven)

---

## Progress Tracking

- [ ] Update this file as tasks are completed or requirements change.
