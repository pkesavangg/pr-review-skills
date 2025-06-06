# .cursor/rules Folder Structure & Documentation

This directory contains all Cursor rule files for the MeApp Android project. These rules define and enforce best practices for architecture, code style, documentation, and maintainability across the codebase.

## Table of Contents

- [Purpose](#purpose)
- [Folder Structure](#folder-structure)
- [Rule File Descriptions](#rule-file-descriptions)
- [How to Use](#how-to-use)
- [How to Add New Rules](#how-to-add-new-rules)
- [Best Practices for Rules](#best-practices-for-rules)

---

## Purpose

- Ensure consistency and quality across the codebase.
- Serve as onboarding and reference material for all contributors.
- Enable automated and manual code review processes.

## Folder Structure

```
.cursor/rules/
  api-best-practices.mdc
  datastore-best-practices.mdc
  entity-dao-best-practices.mdc
  enum-interface-model-best-practices.mdc
  logger-best-practices.mdc
  repository-best-practices.mdc
  service-best-practices.mdc
  README.md
```

## Rule File Descriptions

- **api-best-practices.mdc**: Standards for defining, documenting, and using API interfaces (Retrofit).
- **datastore-best-practices.mdc**: Guidelines for using Proto DataStore, including structure, serialization, and documentation.
- **entity-dao-best-practices.mdc**: Best practices for Room entities and DAOs, including relationships and KDoc.
- **enum-interface-model-best-practices.mdc**: Naming, structure, and documentation for enums, interfaces, and models.
- **logger-best-practices.mdc**: Comprehensive logging standards, including AppLog usage, log levels, structure, privacy, and review.
- **repository-best-practices.mdc**: Repository pattern rules for interface, implementation, DI, usage, and testing.
- **service-best-practices.mdc**: Service pattern rules for interface, implementation, DI, usage, and testing.

## How to Use

- Review these rules before starting new features or refactoring.
- Reference the examples in each rule file when creating new code.
- During code review, check for compliance with these rules.
- Use these rules for onboarding new team members.

## How to Add New Rules

1. Create a new `.mdc` file in this directory.
2. Use a clear, descriptive filename (e.g., `viewmodel-best-practices.mdc`).
3. Follow the structure of existing rule files: summary, numbered guidelines, and examples.
4. Add a short description to this README under "Rule File Descriptions."
5. Announce new or updated rules to the team.

## Best Practices for Maintaining Rules

- Keep rules concise, actionable, and up to date.
- Use numbered lists and code examples for clarity.
- Update rules as the codebase and standards evolve.
- Remove outdated or redundant rules.
- Encourage feedback and continuous improvement.

---

For questions or suggestions, contact the project maintainers.
