# Keychain migration: sensitive data to secure storage

One-time migration moves auth tokens and FCM tokens from legacy storage into Keychain so they are never stored in SwiftData or UserDefaults. Users remain logged in; no extra prompts.

## Migrated keys

### 1. Auth tokens (per account)

| Source | Destination | Migration flag |
|--------|-------------|----------------|
| SwiftData `Account.accessToken`, `Account.refreshToken`, `Account.expiresAt` | Keychain item with **account key** `tokens_<accountId>`, **service** `{bundleId}.tokens`. Value: JSON `{ "accessToken", "refreshToken", "expiresAt" }`. | KvStorage `tokensMigratedToKeychain` (boolean) |

- **When:** First app launch with new code; `AccountService.migrateTokensToKeychainIfNeeded()` runs in init before `updatePublishedState` / refresh.
- **After migration:** Token fields on `Account` are set to `nil` and the account is updated so SwiftData never persists them again. All reads use `KeychainService.getTokens(for: accountId)`.

### 2. FCM token (per account)

| Source | Destination | Migration flag |
|--------|-------------|----------------|
| KvStorage key `fcmToken_<accountId>` (string) | Keychain item with **account key** `fcm_<accountId>`, **service** `{bundleId}.tokens`. Value: UTF-8 string. | None (one-time: Keychain is checked first; if empty, KvStorage is read, then cleared) |

- **When:** When `PushNotificationService.loadStoredFCMToken()` runs for the active account (e.g. on load). After migration the legacy KvStorage key is cleared with `kvStorage.clearValue(forKey: legacyKey)`.

### 3. Key names reference

- **Keychain (Generic Password):**
  - Service: `Bundle.main.bundleIdentifier ?? "meApp"` + `".tokens"`
  - Account keys: `tokens_<accountId>`, `fcm_<accountId>`
- **KvStorage (UserDefaults):**
  - `tokensMigratedToKeychain` — boolean, prevents re-running token migration
  - `fcmToken_<accountId>` — legacy FCM key (removed after migration)

---

## Rollback (if needed)

Use only if you must revert to code that reads tokens from SwiftData/KvStorage instead of Keychain (e.g. emergency rollback of the release).

### Token rollback

1. **Re-populate SwiftData from Keychain (one-time script or debug code):**
   - For each account: `KeychainService.shared.getTokens(for: account.accountId)`.
   - If tokens exist: set `account.accessToken`, `account.refreshToken`, `account.expiresAt` from the decoded `Tokens`, then call `localRepo.updateAccount(account)` (using the non–clearing update so tokens are persisted).
2. **Reset migration flag** so old code doesn’t re-migrate:
   - `kvStorage.setValue(false, forKey: KvStorageKeys.tokensMigratedToKeychain.rawValue)`  
   Or remove the key so legacy code treats it as “not migrated.”
3. **Redeploy** the previous app version that reads tokens from SwiftData. Keychain entries can remain; they are ignored by the old code.

### FCM rollback

1. **Re-populate KvStorage from Keychain (one-time):**
   - For each accountId: `KeychainService.shared.getFCMToken(for: accountId)`.
   - If non-nil: `kvStorage.setValue(token, forKey: KvStorageKeys.fcmTokenKey(for: accountId))`.
2. **Redeploy** the version that reads FCM from KvStorage. Keychain FCM entries can remain.

### Notes

- Rollback does not require deleting Keychain items; old code simply doesn’t read them.
- If you roll back and later re-deploy Keychain-based code, migration will run again only for tokens (if `tokensMigratedToKeychain` is reset). FCM migration runs whenever Keychain has no FCM for the account and KvStorage has a value (so if you re-wrote KvStorage during rollback, it would migrate again on next load).
