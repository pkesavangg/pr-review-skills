---
name: fix-bug
description: Targeted bug fix with regression test. Use after /debug-issue identifies root cause, or when the user says "fix this bug", "apply this fix", "patch this issue".
---

Fix a specific bug and add a regression test to prevent recurrence.

The bug or root cause is: $ARGUMENTS

## Instructions

### 1 — Confirm Root Cause

If called after /debug-issue, read the root cause note from that session.

If called directly and the root cause is not obvious from `$ARGUMENTS`, perform a brief investigation before proceeding:
1. Identify the affected file(s) and method(s)
2. Read the execution path (store → service → repository)
3. Check for related tests and recent changes
4. Determine: expected vs actual behaviour, the exact condition that triggers the failure

If after investigation the root cause is still unclear, run `/debug-issue` explicitly. Do not guess — a wrong fix is worse than no fix.

---

### 2 — Assess Blast Radius

Before changing anything, check how widely the affected type is used:

```bash
rg -l "{AffectedType}" meApp -g '*.swift' | head -20
```

If used across more than 3 feature modules, flag as medium risk and confirm with the user before proceeding.

---

### 3 — Implement the Minimal Fix

Make only the change required to fix the root cause.
- Do not refactor surrounding code.
- Do not fix unrelated issues.
- One logical change per fix.

---

### 4 — Add a Regression Test

Find the existing test file for the affected type. If none exists, run `/gen-test-file` first.

Add a test that:
- Names the failure condition explicitly: `"methodName failure: <description of the bug>"`
- Reproduces the pre-fix behaviour in the Arrange block
- Asserts the corrected behaviour in the Assert block
- Uses `@Test`, `#expect`, `@Suite(.serialized)` (Swift Testing, not XCTest)

If the bug was caused by a missing guard, nil handling, or async race, cover all branches.

---

### 5 — Verify

Run a build first, then run the specific test on a connected physical device:

```bash
# Find device ID
xcodebuild -project meApp.xcodeproj -scheme meAppTests -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator | head -1

# Run the specific test
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'id={DEVICE_ID}' \
  -only-testing:meAppTests/{TestClassName}/{testMethodName}
```

Fix any build errors. Confirm the new test passes and no existing tests break.

---

### 6 — Report

```
Bug Fixed: <root cause summary>
File(s) changed: <list>
Regression test added: <TestClassName.testMethodName>
Test result: PASS
```

Proceed to `/self-review` then `/commit`.
