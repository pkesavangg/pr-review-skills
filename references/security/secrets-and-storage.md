# Secrets & Insecure Storage

Generic rules for any iOS or Android codebase covering:

1. Hardcoded credentials in source
2. Sensitive data stored in the wrong place (`UserDefaults` / `SharedPreferences` instead of Keychain / `EncryptedSharedPreferences`)
3. Backup flags that expose sensitive data

Each rule has Swift and Kotlin examples where the pattern differs. Use the severity each rule prescribes — do not re-classify.

---

## P0 — Hardcoded secrets / API keys / tokens

Anything that looks like a credential committed to source. Expand the existing one-line regex with these patterns:

| Pattern | Detection |
|---|---|
| Generic secret assignment | `(?i)(api[_-]?key\|secret\|token\|password\|client_secret\|access_key)\s*[:=]\s*"[A-Za-z0-9+/_=-]{16,}"` |
| AWS access key ID | `\bAKIA[0-9A-Z]{16}\b` |
| AWS secret key | `(?i)aws_secret_access_key\s*[:=]\s*"[A-Za-z0-9+/]{40}"` |
| GCP service account | JSON containing `"type":\s*"service_account"` AND `"private_key":` |
| Firebase / Google API key | `\bAIza[0-9A-Za-z_-]{35}\b` |
| JWT (likely committed dev/test token) | `\beyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b` |
| Sentry / Bugsnag DSN | `https://[a-f0-9]{32}@(?:o[0-9]+\.)?ingest\.(?:us\.)?sentry\.io/` |
| Private key block | `-----BEGIN (RSA\|EC\|DSA\|OPENSSH\|PRIVATE) (PRIVATE )?KEY-----` |
| Slack token | `xox[bpoars]-[A-Za-z0-9-]+` |
| GitHub PAT | `\bghp_[A-Za-z0-9]{36}\b` or `\bgithub_pat_[A-Za-z0-9_]{82}\b` |

**Swift example:**
```swift
let apiKey = "sk_live_EXAMPLE_redacted_not_a_real_key"   // ❌ hardcoded secret
```

**Kotlin example:**
```kotlin
const val API_KEY = "AIzaSyEXAMPLE_redacted_key"   // ❌ hardcoded secret
```

**Sniff.** Run each regex against added `+` lines across all changed files (not just `.swift` / `.kt` — also `.plist`, `.xml`, `.json`, `.yaml`, `.properties`, build configs).

Exemptions:
- Inside `*/Tests/**`, `**/sample/**`, `**/fixtures/**`, `**/Mocks/**` where value is obviously fake (contains `dummy`, `test`, `xxxxxx`, `fake`, `placeholder`) → downgrade to P2.
- Public client keys that *must* ship in the binary (Firebase client API key, public Stripe key prefixed `pk_`) — acceptable if documented in a project `SECRETS.md`. Without docs, still flag P1 and note the project should document intentional public keys.

**Fix.** Move the value out of source:
- iOS: `xcconfig` files (gitignored), `Info.plist` keys baked at build time from CI secrets, or runtime-fetched secret store
- Android: `local.properties` (gitignored) → exposed via `BuildConfig`, or runtime fetch

---

## P0 — Auth tokens / passwords stored in `UserDefaults` or `SharedPreferences`

Auth tokens, refresh tokens, raw passwords, session cookies must NOT live in `UserDefaults` (iOS) or `SharedPreferences` (Android). Both are world-readable on jailbroken/rooted devices, survive app reinstall on some platforms, and may be backed up to iCloud/Google in plaintext.

**Swift:**
```swift
UserDefaults.standard.set(authToken, forKey: "authToken")   // ❌
```

Use the iOS Keychain (`Security.framework` directly, or a wrapper like `KeychainAccess`):
```swift
try keychain.set(authToken, key: "authToken",
                 ignoringAttributeSynchronizable: true)   // ✅
```

**Kotlin:**
```kotlin
sharedPrefs.edit().putString("auth_token", authToken).apply()   // ❌
```

Use `EncryptedSharedPreferences` (Jetpack Security):
```kotlin
val prefs = EncryptedSharedPreferences.create(
    context, "secure_prefs", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)
prefs.edit().putString("auth_token", authToken).apply()    // ✅
```

For hardware-backed storage of high-value keys: Android Keystore via `KeyGenParameterSpec` with `setUserAuthenticationRequired(true)`.

**Sniff.** Scan `+` lines for:
- iOS: `UserDefaults.standard.set(` / `UserDefaults.standard.setValue(` where the variable name on the right matches `(?i)(token\|password\|secret\|bearer\|pin\|jwt\|refresh)`
- Android: `SharedPreferences.Editor.putString(` / `.edit { putString(...) }` with the same name heuristic

If the project already has a `KeychainService` / `EncryptedPrefs` class in the worktree, suggest routing through it in the fix.

---

## P0 — Plaintext password stored in a database / model

```swift
@Model class Account {
    var email: String
    var password: String   // ❌ plaintext storage
}
```

```kotlin
@Entity
data class User(
    val email: String,
    val password: String,   // ❌ plaintext
)
```

Passwords stored on-device must be hashed. Use a slow, salted hash:

- **Argon2** (preferred — OWASP current recommendation)
- bcrypt
- scrypt
- PBKDF2 with high iteration count (600,000+ for SHA-256)

Plain SHA-256 / SHA-512 / MD5 / SHA-1 are **not** password hashes — they're too fast to be safe against offline brute force.

**Symmetric encryption of a password (AES-GCM, etc.) also fails** — the decryption key lives in the same binary, so an attacker who has the binary has the key.

**Sniff.** Look for model declarations in `+` lines:
- Swift: `@Model` / `class` / `struct` with `password: String` (or `password: Data`) property
- Kotlin: `@Entity` data class with `password: String`

Then read the persistence path: if the password is written into the store as-is (no hashing wrapper between the input and `INSERT`/`save`), P0.

**Fix.** Hash before storing; verify by hashing the candidate at login and comparing. Use a per-account random salt stored alongside the hash.

---

## P1 — Sensitive files written without file protection

**Swift / iOS.**
```swift
try data.write(to: url)   // ❌ no protection level for sensitive content
```

For files containing tokens, PII, or health data, set the protection class:
```swift
try data.write(to: url, options: .completeFileProtection)   // ✅
```

`.completeFileProtection` = file is unreadable while the device is locked. Other levels: `.completeFileProtectionUnlessOpen`, `.completeFileProtectionUntilFirstUserAuthentication`.

**Sniff.** Find `Data.write(to:` / `data.write(to:` in `+` lines without an `options:` argument. Cross-reference the surrounding context — flag P1 if the data variable or destination URL name suggests sensitive content (`token`, `account`, `health`, `auth`, `session`); otherwise P2 reminder.

**Kotlin / Android.**
Use `EncryptedFile` (Jetpack Security) for sensitive content:
```kotlin
val encryptedFile = EncryptedFile.Builder(
    context, file, masterKey,
    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
).build()
encryptedFile.openFileOutput().use { it.write(data) }   // ✅
```

`file.writeBytes(data)` / `FileOutputStream(file)` for sensitive content → P1.

---

## P1 — Backup flags exposing sensitive data

**Android.**
```xml
<application android:allowBackup="true">   <!-- ❌ default; exposes app data via adb backup / Auto Backup -->
```

For apps holding tokens, health data, or PII:
```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="@xml/backup_rules">   <!-- ✅ explicit -->
```

If Auto Backup is required for UX (e.g., transferring settings to a new device), use `android:fullBackupContent` and `android:dataExtractionRules` (Android 12+) to exclude sensitive paths:

```xml
<full-backup-content>
    <exclude domain="sharedpref" path="secure_prefs.xml" />
    <exclude domain="database" path="health.db" />
</full-backup-content>
```

**Sniff.** Diff additions to `AndroidManifest.xml` setting `android:allowBackup="true"` without a corresponding `android:fullBackupContent` / `android:dataExtractionRules` attribute → P1.

**iOS.**
Files under `Documents/` are backed up to iCloud by default. Sensitive caches / decrypted artefacts should be excluded:

```swift
var resourceValues = URLResourceValues()
resourceValues.isExcludedFromBackup = true
try fileURL.setResourceValues(resourceValues)   // ✅
```

Or use `Caches/` / `tmp/` directories which are never backed up.

**Sniff.** New file writes under `URL(documentsDirectory)` or `FileManager.SearchPathDirectory.documentDirectory` where the filename contains tokens / health terms, without a subsequent `isExcludedFromBackup = true` → P1.

---

## Output

For each finding:

```
[<file>:<line>] <severity> — Security/Storage — <rule> · <one-sentence fix>
```

The orchestrator handles de-duplication against swiftui-pro and prior reviewer comments (Step 4a.4) before posting.
