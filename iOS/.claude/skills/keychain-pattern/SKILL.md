---
name: keychain-pattern
description: Understand and apply secure storage patterns for sensitive data in meApp iOS. Use when working with auth tokens, passwords, credentials, PII, or health data. Reference this skill from /analytics (PII logging safety), /review-security (Keychain compliance), and /storage-change (migration planning).
---

Secure storage patterns for sensitive data in meApp iOS.

## Quick Reference: Storage Decision Tree

```
Is this data sensitive?
├─ YES (auth token, password, email, health data, weight, etc.)
│  └─ Store in → Keychain via KeychainService
│     ├─ NEVER: UserDefaults, SwiftData, KvStorage
│     └─ Access: KeychainService methods only
│
└─ NO (feature flags, UI preferences, cache, etc.)
   └─ Store in → Appropriate layer
      ├─ Persistent non-sensitive → SwiftData or KvStorage
      ├─ Lightweight preferences → UserDefaults (via KvStorageService)
      └─ Temporary/runtime → In-memory properties
```

---

## Sensitive Data Classification

### Tier 1: Always Keychain (Security-Critical)
- **Auth tokens** — access, refresh, ID tokens
- **Passwords** — user passwords (rare; usually delegated to backend)
- **API credentials** — API keys, secrets
- **Encryption keys** — any cryptographic material

### Tier 2: Always Keychain (PII/Privacy-Critical)
- **Email addresses** — personally identifiable information
- **Phone numbers** — personally identifiable information
- **Health/biometric data** — weight, blood pressure, body measurements
- **Account numbers** — banking, payment, third-party integrations
- **Social security numbers** — identity information

### Tier 3: Analyze Case-by-Case
- **User profile names** — may be public; check business rules
- **Workout/activity logs** — depends on privacy setting
- **App-specific IDs** — usually non-sensitive unless they encode PII

### NOT Sensitive (Safe for SwiftData/KvStorage/Defaults)
- **Feature flags** — toggle state for features
- **UI preferences** — last selected tab, theme, language
- **Cached metadata** — list item names, dates (not measurements)
- **Analytics flags** — opt-in/out states
- **App version** — build number, last updated

---

## Implementation Patterns

### Pattern 1: Store Auth Token in Keychain

**❌ WRONG:**
```swift
@Model
class Account {
    var accessToken: String  // FAIL: sensitive data in SwiftData
    var refreshToken: String // FAIL: unencrypted
}

// Or in UserDefaults:
UserDefaults.standard.set(token, forKey: "authToken") // FAIL
```

**✅ CORRECT:**
```swift
// In Account model (SwiftData):
@Model
class Account {
    @Transient var accessToken: String?  // Transient — not stored
    @Transient var refreshToken: String? // Transient — not stored
}

// Actual storage (Keychain):
@MainActor
final class KeychainService {
    func storeToken(_ token: String, for accountId: String, type: TokenType) throws {
        let key = "token:\(accountId):\(type.rawValue)"
        try keychain.set(token, forKey: key)
    }
    
    func retrieveToken(for accountId: String, type: TokenType) throws -> String? {
        let key = "token:\(accountId):\(type.rawValue)"
        return try keychain.get(key)
    }
}

// Usage (feature code):
try keychain.storeToken(accessToken, for: accountId, type: .access)
let token = try keychain.retrieveToken(for: accountId, type: .access)
```

**How tokens reach feature code:** `AccountService.updatePublishedState()` hydrates tokens from Keychain and stamps them onto `AccountSnapshot.accessToken` / `.refreshToken` / `.expiresAt` (`let` fields). Consumers read `accountService.activeAccount?.accessToken` directly — that read is a plain `String?` access on a `Sendable` struct, safe on any actor. The `Account` `@Model`'s `@Transient` token fields are internal to `AccountService` and never leave it.

---

### Pattern 2: Health Data Storage (Weight, Blood Pressure)

**❌ WRONG:**
```swift
// Logging raw health data
logger.log(level: .info, message: "Weight entries: \(entries.map { $0.weight })")
// FAIL: Logs contain health measurements (PII)

UserDefaults.standard.set(weight, forKey: "currentWeight")
// FAIL: Health data not encrypted
```

**✅ CORRECT:**
```swift
// Log IDs and counts only
logger.log(level: .info, message: "Synced \(entries.count) entries. accountId=\(accountId)")
// PASS: No raw health data in logs

// Store in SwiftData (at-rest encrypted via iOS data protection)
@Model
class Entry {
    var weight: Double
    var bloodPressure: BloodPressure?
    var createdAt: Date
    // SwiftData encryption is handled by iOS automatically
}

// If sync with backend needed:
@MainActor
final class EntryService {
    func syncEntries(for accountId: String) async throws {
        let entries = try await repository.fetchEntries(for: accountId)
        try await api.syncEntries(entries, accountId: accountId)
        // PASS: Sync happens via encrypted HTTPS + Keychain tokens
    }
}
```

---

### Pattern 3: Avoiding Logging Sensitive Data

**❌ WRONG:**
```swift
// Logging auth tokens
logger.log(level: .info, message: "Auth token: \(token)")
// FAIL: Token in plaintext logs

// Logging email addresses
logger.log(level: .info, message: "User email: \(user.email)")
// FAIL: PII in logs

// Logging health data
logger.log(level: .debug, message: "Weight: \(entry.weight), BP: \(entry.bloodPressure)")
// FAIL: Health data exposed in console
```

**✅ CORRECT:**
```swift
// Log IDs, not tokens
logger.log(level: .info, message: "Auth succeeded. accountId=\(accountId)")
// PASS: No sensitive data

// Log counts, not email
logger.log(level: .info, message: "Fetched \(count) users from account")
// PASS: PII not exposed

// Use .debug only for verbose context (never persisted)
logger.log(level: .debug, message: "Entry weights normalized. count=\(entries.count)")
// PASS: Debug logs aren't persisted, safe for verbose output

// Or use structured data without PII
logger.log(level: .info, message: "Entry synced", data: "entryId=\(id) timestamp=\(date)")
// PASS: No sensitive fields in data
```

---

### Pattern 4: Multi-Account Keychain Access

**❌ WRONG:**
```swift
// Storing tokens without account separation
try keychain.set(token, forKey: "accessToken")
// FAIL: No account isolation; token could be overwritten across accounts
```

**✅ CORRECT:**
```swift
// Keychain keys must include accountId for multi-account safety
func storeToken(_ token: String, for accountId: String) throws {
    let key = "token:access:\(accountId)"  // Include accountId
    try keychain.set(token, forKey: key)
}

func retrieveToken(for accountId: String) throws -> String? {
    let key = "token:access:\(accountId)"
    return try keychain.get(key)
}

// Always pass accountId when accessing tokens
let token = try keychain.retrieveToken(for: activeAccountId)
```

---

### Pattern 5: Temporary Credentials (Testing)

**For unit tests only:**
```swift
@MainActor
final class MockKeychainService: KeychainServiceProtocol {
    private var storage: [String: String] = [:]
    
    func storeToken(_ token: String, for accountId: String, type: TokenType) throws {
        let key = "token:\(accountId):\(type.rawValue)"
        storage[key] = token  // In-memory, test only
    }
    
    func retrieveToken(for accountId: String, type: TokenType) throws -> String? {
        let key = "token:\(accountId):\(type.rawValue)"
        return storage[key]
    }
}
```

**For dev/staging environments:**
```swift
// Use real Keychain, but populate with test tokens only via secure mechanism
// NEVER hardcode tokens in source code
```

---

## Security Checklist

### Code Review: Keychain Compliance

When reviewing code, check:

- ✅ **Auth tokens** → Stored in Keychain via `KeychainService`
- ✅ **Passwords** → Never in SwiftData, UserDefaults, or plaintext
- ✅ **Email/PII** → Not logged or stored unencrypted
- ✅ **Health data** → Stored in SwiftData (iOS-encrypted) or Keychain
- ✅ **Multi-account safety** → Keychain keys include `accountId`
- ✅ **Logging** → No auth tokens, passwords, or PII in logs
- ✅ **Sensitive logs** → `.debug` level only (never persisted)
- ✅ **Log data field** → No sensitive fields in `data:` parameter

### Automated Checks

`/review-security` will flag:
- Tokens stored in UserDefaults or SwiftData → **FAIL**
- Email/passwords in logs → **WARNING**
- Sensitive data without account isolation → **FAIL**
- HTTP requests carrying sensitive data (should use HTTPS + Keychain tokens) → **FAIL**

---

## Migration Patterns

### Moving Existing Data to Keychain

**Scenario:** Account model stores tokens; need to migrate to Keychain

**Migration checklist:**
1. Add `@Transient` fields to Account model (preserves schema)
2. Create migration service that reads tokens from Account, writes to Keychain
3. Mark Account token fields for deletion after migration
4. Add rollback test: verify tokens accessible from Keychain
5. Document in `docs/KEYCHAIN_MIGRATION.md`:
   - Migration date and version
   - Rollback steps
   - Verification steps

**Example:**
```swift
@MainActor
final class AccountMigrationService {
    @Injector private var keychain: KeychainServiceProtocol
    
    func migrateTokensToKeychain(account: Account) throws {
        // Read from SwiftData
        guard let oldToken = account.accessToken else { return }
        
        // Write to Keychain
        try keychain.storeToken(oldToken, for: account.id, type: .access)
        
        // Mark for cleanup (or delete immediately after verification)
        account.accessToken = nil  // Clear the SwiftData field
    }
}
```

---

## HIPAA Compliance

**meApp is HIPAA-regulated.** Sensitive health data must:

1. **At Rest** → Encrypted via Keychain or iOS data protection
   - Auth tokens → Keychain (hardware-backed)
   - Health measurements → SwiftData (iOS data protection)
   - Never → UserDefaults, KvStorage, plaintext files

2. **In Transit** → HTTPS + Keychain tokens
   - All API calls via `HTTPClient.send()` with `needsAuth: true`
   - Tokens from Keychain, never hardcoded
   - Never `http://` in production

3. **In Logs** → No PII or health data
   - Log IDs, counts, flags — not raw data
   - Use `.debug` only (console-only, not persisted)
   - Sensitive operations logged as "completed" or "failed" only

4. **Access Control** → Account-scoped Keychain keys
   - Every Keychain key includes `accountId`
   - Prevents cross-account token leakage
   - Multi-account switching respects isolation

---

## Reference

### Services
- **`KeychainService`** — `meApp/Data/Services/KeychainService.swift`
- **`LoggerService`** — `meApp/Data/Services/LoggerService.swift` (no PII)
- **`KvStorageService`** — `meApp/Data/Services/KvStorageService.swift` (non-sensitive only)

### Models
- **`Account`** — `meApp/Domain/Models/DB/Account.swift` (@Transient token fields)
- **`Entry`** — `meApp/Domain/Models/DB/Entry.swift` (health data in SwiftData)
- **`DashboardSettings`** — `meApp/Domain/Models/DB/DashboardSettings.swift` (non-sensitive prefs)

### Documentation
- **Migration guide** — `docs/KEYCHAIN_MIGRATION.md`
- **HIPAA compliance** — `CLAUDE.md` Security section
- **Testing patterns** — `meAppTests/docs/UNIT_TESTING.md`

---

## Related Skills

Reference this skill from:
- **`/analytics.md`** — PII logging safety rules
- **`/review-security.md`** — Keychain compliance checks
- **`/storage-change.md`** — Migration planning and token storage decisions

---

## Summary

**Golden Rules:**

1. **Sensitive data → Keychain only** (tokens, passwords, PII, health data)
2. **Never log sensitive data** (use IDs/counts instead)
3. **Multi-account keys** (include accountId in Keychain keys)
4. **HTTPS + token auth** (all API calls via HTTPClient with Keychain tokens)
5. **SwiftData encrypted** (health data safe in SwiftData; iOS handles encryption)
6. **Test in-memory only** (never hardcode test credentials)

**When in doubt:** Store in Keychain. It's the most secure option and always correct for sensitive data.
