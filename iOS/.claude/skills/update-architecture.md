---
name: update-architecture
description: Update architecture.md after a structural change to the codebase. Use after adding a new feature, service, SwiftData model, external dependency, or DI registration. Also use when the user says "update architecture", "architecture is outdated", or "reflect this in the docs". Recommended follow-up after /feature-slice and /wire-service.
---

Keep architecture.md current after a structural change.

The change that was made is: $ARGUMENTS

## Instructions

### 1 — Identify What Changed

Determine the category of change from $ARGUMENTS or the recent git diff:

```bash
git diff $(git merge-base HEAD origin/main) HEAD --name-only
```

Categories:
- New feature module added to `meApp/Features/`
- New service (protocol + implementation + `ServiceRegistry` registration)
- New SwiftData `@Model` in `Domain/Models/DB/`
- New external SPM dependency
- Renamed type, method, or file that affects architecture
- New API endpoint category or integration

---

### 2 — Read the Current Architecture File

Read `architecture.md` in full. Identify which sections need updating based on the change category:

| Change type | Sections to update |
|---|---|
| New feature module | Section 3.2 Feature Modules table; Section 3 bottom tab nav if tab-visible |
| New service | Section 3.3 Service Layer table |
| New SwiftData model | Section 4.1 Key Models table |
| New SPM dependency | Section 5 External Integrations table |
| Renamed type | Any section referencing the old name |
| Architecture pattern change | Section 3.1 and/or Section 2 diagrams |

---

### 3 — Update architecture.md

Apply the minimum necessary changes:
- Add the new row(s) to the relevant table(s), following the existing row format exactly
- Update the `Date of Last Update` field in Section 10 to today's date
- Do not reformat, rewrite, or restructure sections that were not affected by the change

---

### 4 — Check Skill Files for Stale References

If any type, file, or class was **renamed or removed**, search `.claude/skills/` and `.claude/agents/` for stale references:

```bash
rg -l "{old type name}" .claude/skills .claude/agents -g '*.md'
```

For each file that contains a stale reference:
- Read the skill
- Identify the outdated class name, file path, or method name
- Report it as a staleness warning

Do NOT auto-edit skill files without user confirmation — report them and ask whether to update.

---

### 5 — Report

```
architecture.md updated:
- Section(s) changed: {list}
- Rows added: {count and names}
- Date updated: {today}

Staleness check:
- Skill files with stale references: {list or "None found"}
```

Recommend running `/build` to confirm the codebase still compiles after any structural change.
