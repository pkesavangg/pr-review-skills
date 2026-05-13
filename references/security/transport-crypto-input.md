# Transport, Crypto & Input Sanitization

Generic security rules covering:

1. **Transport** — TLS bypass, cleartext exemptions, certificate validation
2. **Crypto** — weak algorithms, weak RNG, broken cipher modes
3. **Input** — injection vectors (URL, predicate, file path, SQL)

Applies uniformly to iOS and Android; each rule notes platform when the pattern differs.

---

## P0 — Cleartext / arbitrary-load TLS exemptions

**iOS — `Info.plist`:**
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>   <!-- ❌ disables ATS globally -->
</dict>
```

Acceptable only with explicit per-domain exceptions for third-party endpoints lacking TLS, AND with `NSExceptionMinimumTLSVersion` raised to a safe floor (TLS 1.2 minimum):
```xml
<key>NSExceptionDomains</key>
<dict>
    <key>legacy-api.partner.example</key>
    <dict>
        <key>NSExceptionAllowsInsecureHTTPLoads</key><true/>
        <key>NSExceptionMinimumTLSVersion</key><string>TLSv1.2</string>
    </dict>
</dict>
```

**Android — `AndroidManifest.xml`:**
```xml
<application android:usesCleartextTraffic="true">   <!-- ❌ -->
```

Or in `network_security_config.xml`:
```xml
<base-config cleartextTrafficPermitted="true">   <!-- ❌ allows globally -->
```

**Sniff.** Diff additions to `Info.plist`, `AndroidManifest.xml`, or any `network_security_config*.xml` setting these flags `true`. Flag P0 unless the PR description explicitly justifies and scopes the exemption.

**Fix.** Remove the global flag. For specific third-party endpoints, use per-domain exceptions with TLS-version floors. For local development against an HTTP-only mock server, scope the config to debug builds via separate `network_security_config_debug.xml`.

---

## P0 — Hardcoded `http://` URLs in production paths

```swift
let url = URL(string: "http://api.example.com/auth")!   // ❌
```

```kotlin
const val BASE_URL = "http://api.example.com/"   // ❌
```

**Sniff.** Find `http://` in `+` lines across `.swift`, `.kt`, `.kts`, `Info.plist`, `*.xml`, build configs.

Acceptable contexts:
- Inside `#if DEBUG` / `BuildConfig.DEBUG` blocks
- Files under `*/Debug/`, `*Dev/`, `*Staging/` source sets
- `localhost`, `127.0.0.1`, `10.0.2.2` (Android emulator host loopback)
- URLs in comments / documentation

Anywhere else → P0.

---

## P0 — Custom trust validation that bypasses certificate checks

**Swift.**
```swift
func urlSession(
    _ session: URLSession,
    didReceive challenge: URLAuthenticationChallenge,
    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
) {
    completionHandler(.useCredential,
                      URLCredential(trust: challenge.protectionSpace.serverTrust!))   // ❌ accepts any cert
}
```

**Kotlin.**
```kotlin
val trustManager = object : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { /* ❌ empty */ }
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { /* ❌ */ }
    override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
}
```

Or OkHttp:
```kotlin
OkHttpClient.Builder()
    .hostnameVerifier { _, _ -> true }   // ❌ accepts any hostname
    .build()
```

**Sniff.**
- iOS: `URLAuthenticationChallenge` handlers that call `completionHandler(.useCredential, URLCredential(trust: ...))` without prior validation against a pinned certificate or trust evaluator
- Android: `X509TrustManager` implementations with empty `checkServerTrusted` bodies, `HostnameVerifier { _, _ -> true }`, `SSLContext.init(null, arrayOf(trustAllManager), ...)`

**Fix.** Use the platform default. If certificate pinning is genuinely needed, use `URLSession` pinning delegate that validates against a known cert / public key (iOS), or OkHttp's `CertificatePinner` (Android) — both validate against a known good, they don't bypass validation.

---

## P1 — Weak hashing algorithm used for security

For security purposes (NOT checksums):

- `MD5` → broken since 2004
- `SHA-1` → collision attack since 2017
- For password hashing specifically, see `secrets-and-storage.md` (bcrypt/scrypt/Argon2/PBKDF2 — SHA-* is never a password hash)

**Swift:**
```swift
let hash = Insecure.MD5.hash(data: data)   // ❌ if security
let hash = Insecure.SHA1.hash(data: data)  // ❌
```

CryptoKit prefixes weak algorithms with `Insecure` — that's an API-level hint they shouldn't be used for security.

**Kotlin:**
```kotlin
MessageDigest.getInstance("MD5")      // ❌
MessageDigest.getInstance("SHA-1")    // ❌
```

**Sniff.** Literal algorithm strings in `+` lines: `MD5`, `SHA-1`, `SHA1`. Read 5 lines of context — if the code mentions `checksum`, `etag`, `fingerprint` of non-security data (e.g., file dedup), it's acceptable; flag everywhere else.

**Fix.** SHA-256 or SHA-512 for general hashing. For password verification, use a slow KDF.

---

## P1 — Weak cipher / mode

```kotlin
Cipher.getInstance("AES")                     // ❌ defaults to AES/ECB/PKCS5Padding on Android
Cipher.getInstance("AES/ECB/PKCS5Padding")    // ❌ ECB leaks plaintext patterns
Cipher.getInstance("DES/CBC/PKCS5Padding")    // ❌ DES is broken
Cipher.getInstance("RC4")                     // ❌ broken
```

**Swift.**
```swift
// CommonCrypto with kCCAlgorithmDES, kCCOptionECBMode → ❌
```

**Sniff.** Match algorithm strings literally in `+` lines:
- `"AES"\b` without `/CBC/` or `/GCM/` or `/CTR/`
- `/ECB/`
- `DES`, `3DES`, `RC4`, `Blowfish`
- `kCCAlgorithmDES`, `kCCOptionECBMode`

**Fix.** AES-GCM with a fresh random nonce per encryption: `AES/GCM/NoPadding` (Android) / `AES.GCM` (CryptoKit). Use AES-CBC only with HMAC for authenticity if GCM is unavailable.

---

## P1 — Hardcoded IV / nonce

```swift
let nonce = try AES.GCM.Nonce(data: Data(repeating: 0, count: 12))   // ❌ all zeros
```

```kotlin
val iv = ByteArray(12)   // ❌ all zeros
val ivSpec = IvParameterSpec(iv)
```

Reusing a nonce with AES-GCM destroys all of its security guarantees — two messages encrypted under the same key+nonce can be XOR'd to recover plaintext.

**Sniff.** `IvParameterSpec(` / `AES.GCM.Nonce(data:` / `GCMParameterSpec(` constructions in `+` lines where the source is:
- A literal byte array
- `ByteArray(N)` (default-initialised zero array)
- `Data(repeating:count:)`
- A `static` / `const` / file-scope constant

**Fix.**
```swift
let nonce = AES.GCM.Nonce()   // fresh per encryption
```
```kotlin
val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
```

Store the IV alongside the ciphertext (it doesn't need to be secret, just unique).

---

## P1 — Insecure RNG for security values

**Swift.**
```swift
let token = String(Int.random(in: 0..<1_000_000_000))   // ❌ for security
```

Apple's `Int.random(in:)` uses `arc4random_buf` in practice, which is cryptographically secure on Apple platforms — but the API doesn't *guarantee* that. For security-sensitive values, use the explicit secure path:
```swift
var bytes = [UInt8](repeating: 0, count: 32)
let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
guard status == errSecSuccess else { /* fail loudly */ }
```

Or:
```swift
var rng = SystemRandomNumberGenerator()   // documented as cryptographically secure
let token = rng.next()
```

**Kotlin.**
```kotlin
Math.random()                              // ❌ uses java.util.Random, not crypto-strong
Random.nextInt(1_000_000)                  // ❌
kotlin.random.Random.Default.nextBytes(...)// ❌ not guaranteed secure
```

```kotlin
SecureRandom().nextBytes(buffer)           // ✅
```

**Sniff.** `Math.random` / `java.util.Random` / `kotlin.random.Random` / `Int.random(in:` in `+` lines under any file where the surrounding context mentions `token`, `nonce`, `salt`, `password`, `session`, `csrf`, `otp`, `key`. Read 5 lines of context.

---

## P1 — User input interpolated into URL paths (URL injection)

```swift
let url = URL(string: "https://api.example.com/users/\(userInput)/orders")!   // ❌
```

```kotlin
val url = "https://api.example.com/users/$userInput/orders"   // ❌
```

Untrusted input can inject path components (`../`, `%2F`), inject query parameters (`?admin=true`), or break the URL parse entirely.

**Sniff.** String interpolations in URL construction in `+` lines where the interpolated variable name suggests user input (`userInput`, `query`, `search`, `email`, `name`, or anything bound to a `TextField` / `EditText`).

**Fix.**
```swift
var components = URLComponents(string: "https://api.example.com")!
components.path = "/users/\(userInput)/orders"
components.queryItems = [URLQueryItem(name: "filter", value: filter)]
let url = components.url!
```

```kotlin
val uri = Uri.Builder()
    .scheme("https").authority("api.example.com")
    .appendPath("users").appendPath(userInput)
    .appendPath("orders")
    .appendQueryParameter("filter", filter)
    .build()
```

URL-encode path segments — `URLComponents` and `Uri.Builder` do this for you.

---

## P1 — User input in predicates / database queries

**Swift / SwiftData.**
```swift
let predicate = #Predicate<Account> { $0.email == userInput }   // ✅ typed, parameterized

let descriptor = FetchDescriptor<Account>(
    predicate: NSPredicate(format: "email == '\(userInput)'")   // ❌ string predicate, injectable
)
```

**Kotlin / Room.**
```kotlin
@Query("SELECT * FROM account WHERE email = :email")           // ✅
fun findByEmail(email: String): Account?

@Query("SELECT * FROM account WHERE email = '" + email + "'")  // ❌ string concat
```

**Raw SQL (any platform).**
```swift
db.execute("SELECT * FROM account WHERE email = '\(userInput)'")   // ❌
```

**Sniff.** `NSPredicate(format:` with string interpolation; Room `@Query` with `+` concatenation instead of `:param` placeholders; `db.execute` / `rawQuery` with interpolated user input.

**Fix.** Parameterized queries / typed predicates (`#Predicate`, Room `@Query` with `:param`, `db.execute(sql, args: [...])`).

---

## P1 — User input in file paths (path traversal)

```swift
let url = documentsDirectory.appendingPathComponent(userInput)   // ❌ if userInput = "../../etc/passwd"
```

```kotlin
val file = File(filesDir, userInput)   // ❌ same
```

**Sniff.** `appendingPathComponent(` / `File(_, _)` / `Path` construction with an interpolated user-input variable.

**Fix.** Validate the input doesn't contain `..`, `/`, `\`, or use a UUID prefix and store the user's display name in a sidecar metadata file. Always resolve the final path and confirm it stays within the intended directory:
```swift
let resolved = url.resolvingSymlinksInPath().standardized
guard resolved.path.hasPrefix(documentsDirectory.path) else { throw .invalidPath }
```

---

## Output

For each finding:

```
[<file>:<line>] <severity> — Security/<Transport|Crypto|Input> — <rule> · <one-sentence fix>
```
