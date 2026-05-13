# Sensitive Data in Logs & Exposure Surfaces

Generic rules covering:

1. **PII / PHI / tokens in logs** — the centerpiece
2. **Crash reporter / analytics** user identifier leaks
3. **Clipboard / screenshot** exposure of sensitive screens
4. **Exported components** without auth gates (Android)
5. **Deep-link / URL-scheme** handlers without source validation
6. **WebView JS bridges** without origin validation

These complement [logging-hygiene.md](../ios/logging-hygiene.md), which covers WHERE logging happens (body, `.onChange`, hot streams). This file covers WHAT is being logged.

---

## P0 — PII / PHI / tokens in log statements

Never log:

| Class | Examples |
|---|---|
| **Authentication artefacts** | Passwords, auth tokens, refresh tokens, session IDs, CSRF tokens, full `Authorization: Bearer ...` headers, API keys |
| **Direct identifiers** | Email, phone number, SSN / national ID, full name, date of birth, physical address, IP address |
| **Health data (HIPAA)** | Weight, BMI, blood pressure, heart rate, blood glucose, medications, diagnoses, lab values |
| **Financial data** | Full card number, CVV, bank account, routing number, account balance |
| **Raw bodies** | Full HTTP request/response bodies that may contain any of the above |

**Swift examples:**
```swift
logger.log(level: .info, message: "user logged in: \(user.email)")                            // ❌ PII
logger.log(level: .debug, message: "response: \(String(data: data, encoding: .utf8) ?? "")")  // ❌ raw body
logger.log(level: .info, message: "auth header: \(request.allHTTPHeaderFields)")              // ❌ token
print("user: \(dump(user))")                                                                  // ❌ dump exposes all fields
os_log("password = %@", password)                                                             // ❌
```

**Kotlin examples:**
```kotlin
Log.d("Auth", "logged in as $email")                       // ❌
Log.d("Net", "response = $rawJson")                        // ❌
Timber.d("user = $user")                                   // ❌ if user.toString() exposes PII
Timber.d("auth = ${request.headers["Authorization"]}")     // ❌ token
logger.info("payload: ${gson.toJson(request)}")            // ❌ full body
```

**Sniff.** For each logging call in `+` lines — `log(`, `Log.d/i/w/e(`, `print(`, `NSLog(`, `os_log`, `Timber.*(`, `logger.*(`, `Logger().` — inspect the message argument:

1. **Identifier name match.** Variable names interpolated into the message matching: `email`, `phone`, `ssn`, `dob`, `birth`, `name(?!space)`, `address`, `password`, `token`, `bearer`, `auth`, `secret`, `card`, `cvv`, `weight`, `bmi`, `bp`, `glucose`, `medication`, `diagnosis`.
2. **Object dump.** `dump(<var>)`, `String(describing: <var>)`, `<var>.toString()`, `gson.toJson(<var>)`, `JSONEncoder().encode(<var>)` where `<var>` is a model with sensitive fields. Read the type's declaration from the worktree to confirm.
3. **Raw body / payload.** Variable names matching `body`, `response`, `request`, `payload`, `json`, `data` concatenated into a log message without prior sanitisation, in a file that handles HTTP.

For matches → P0.

**Fix.** Log opaque identifiers and discrete events:
```swift
logger.log(level: .info, message: "user logged in",
           data: ["accountId": user.id.uuidString])
```
```kotlin
Timber.d("logged in: accountId=%s", account.id)
```

Mask if you must indicate presence:
- Email → `j***@example.com`
- Card → last 4 only
- Token → never log; if presence matters, log `hasToken: true`

---

## P0 — Crash reporter / analytics user identifier set to PII

```swift
Crashlytics.crashlytics().setUserID(user.email)    // ❌
Analytics.setUserID(user.email)                    // ❌
Sentry.setUser(User(email: user.email))            // ❌ stores in Sentry
```

```kotlin
FirebaseCrashlytics.getInstance().setUserId(user.email)       // ❌
FirebaseAnalytics.getInstance(context).setUserId(user.email)  // ❌
Sentry.setUser(User().apply { email = user.email })           // ❌
```

Crash reports and analytics events get retained for months and indexed across users by the third-party tool — setting an email as user ID makes it the primary key in their UI.

**Sniff.** `setUserID(` / `setUserId(` / `Sentry.setUser` / `Bugsnag.setUser` calls where the argument is or contains a property named `email`, `phone`, `username`, `name`.

**Fix.** Use the account's opaque UUID:
```swift
Crashlytics.crashlytics().setUserID(user.id.uuidString)
```

---

## P1 — Logging raw `Error` / `Exception` that may carry tokens in the description

```swift
catch {
    logger.log(level: .error, message: "request failed: \(error)")   // ❌
}
```

`URLError.localizedDescription` can include the full URL (including query string with tokens). `String(describing: error)` exposes all associated values. `error.localizedDescription` on a JSON-decoding error can include parts of the payload.

```kotlin
catch (e: Exception) {
    Timber.e("request failed: $e")                  // ❌ may include URL + token
    Timber.e(e, "request failed")                   // ✅ structured, framework strips PII
}
```

**Sniff.** `catch { ... }` / `catch (e: ...) { ... }` blocks in HTTP/auth-handling files where the log message interpolates `\(error)` / `$e` / `String(describing: error)` directly.

**Fix.** Log structured fields:
```swift
catch {
    let ns = error as NSError
    logger.log(level: .error, message: "auth request failed",
               data: ["code": "\(ns.code)", "domain": ns.domain])
}
```

---

## P1 — Sensitive value placed on the system clipboard

```swift
UIPasteboard.general.string = authToken   // ❌ readable by any other app
UIPasteboard.general.string = otp         // ❌ unless user explicitly tapped Copy
```

```kotlin
val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
clipboard.setPrimaryClip(ClipData.newPlainText("token", authToken))   // ❌
```

**Sniff.** `UIPasteboard.general.string = ` / `ClipboardManager.setPrimaryClip(` where the right-hand value is a token / password / OTP / health metric variable. Read variable name + surrounding context.

**Fix.** If user explicitly requested a copy (visible "Copy" button on screen), use the platform's sensitive-content flag:

- iOS: `UIPasteboard.general.setItems(_:options:)` with an `.expirationDate` so the value auto-clears after 60 seconds, and `.localOnly` so it doesn't sync to other devices via Universal Clipboard
- Android 13+: `ClipDescription.EXTRA_IS_SENSITIVE` so the system clipboard preview doesn't show the value

For values that should never be copyable (auth tokens, raw PHI), don't write to clipboard at all.

---

## P1 — Sensitive screens recordable / screenshottable

A screen displaying tokens, OTPs, health charts, full payment forms, or full PII should be protected against screenshots and screen recording.

**iOS.**
```swift
// Detect screen recording
if UIScreen.main.isCaptured {
    // overlay blur
}

// Blur on app-switcher backgrounding
func sceneWillResignActive(_ scene: UIScene) {
    blurOverlay.isHidden = false
}
func sceneDidBecomeActive(_ scene: UIScene) {
    blurOverlay.isHidden = true
}
```

iOS doesn't expose a hard "block screenshots" API for app content; the convention is blur-on-background + detect-screen-recording.

**Android.**
```kotlin
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE,
)
```

`FLAG_SECURE` blocks screenshots, screen recording, AND the recents-screen thumbnail preview.

**Sniff.** New views/screens/activities in `+` lines under paths matching `Auth*`, `Login*`, `Payment*`, `Card*`, `Health*`, `Profile*`, `OTP*`, `MFA*` that don't reference `isCaptured`, `FLAG_SECURE`, or a blur-on-resign hook. Flag P1 since the rule depends on content — reviewer should confirm whether the screen actually displays sensitive data.

---

## P1 — Android exported component without permission gate

```xml
<activity android:name=".SettingsActivity"
          android:exported="true">                            <!-- ❌ -->
```

```xml
<receiver android:name=".SmsReceiver"
          android:exported="true">                            <!-- ❌ any app can broadcast -->
    <intent-filter>
        <action android:name="com.example.RECEIVE_OTP" />
    </intent-filter>
</receiver>
```

`android:exported="true"` lets any app on the device launch the component. If it performs sensitive operations (settings change, money transfer, OTP read, file access), it must be gated.

**Sniff.** `AndroidManifest.xml` diff additions with `android:exported="true"` and no `android:permission="..."` (custom signature-level permission). Acceptable if the component's intent filters only match system-broadcast actions like `BOOT_COMPLETED` AND the component only does work appropriate to those system events.

**Fix.**
```xml
<activity android:name=".SettingsActivity"
          android:exported="false">                           <!-- ✅ in-app only -->
```

Or require a signature-level permission:
```xml
<permission android:name="com.example.app.permission.MODIFY_SETTINGS"
            android:protectionLevel="signature" />

<activity android:name=".SettingsActivity"
          android:exported="true"
          android:permission="com.example.app.permission.MODIFY_SETTINGS">
```

---

## P1 — Deep-link / URL-scheme handler mutating state without auth

**Swift.**
```swift
func application(_ app: UIApplication,
                 open url: URL,
                 options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
    if url.host == "transfer" {
        performTransfer(to: url.queryItem("to"),
                        amount: url.queryItem("amount"))   // ❌ no auth, no source check
    }
    return true
}
```

**Kotlin.**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    intent.data?.let { uri ->
        if (uri.host == "transfer") {
            performTransfer(uri.getQueryParameter("to"),
                            uri.getQueryParameter("amount"))   // ❌
        }
    }
}
```

Custom URL schemes and deep links can be invoked by any app or by any web page. Universal Links / App Links narrow this (server-side `apple-app-site-association` / `assetlinks.json`), but the handler must still:

1. Require an authenticated user session before mutating state
2. Validate the source application where the API allows (`options[.sourceApplication]` on iOS, `referrer` / `getReferrer()` on Android — both can be spoofed, but they're a useful signal)
3. Confirm with the user before destructive operations — never silent-mutate

**Sniff.** URL handler delegate methods / intent filters in `+` lines where the handler calls methods named `perform*`, `delete*`, `transfer*`, `pay*`, `set*`, `update*`, `add*` without preceding `session.isAuthenticated` / `currentUser != null` / equivalent checks.

**Fix.** Gate behind authentication; for state-changing actions, route through an in-app confirmation screen.

---

## P1 — WebView JS bridge / cleartext load

**Android.**
```kotlin
webView.settings.javaScriptEnabled = true
webView.addJavascriptInterface(MyBridge(), "Android")   // ❌ exposes bridge to all loaded JS
webView.loadUrl(externalUrl)
```

`addJavascriptInterface` exposes a native object to all JavaScript executing in the WebView. If the WebView can load arbitrary URLs (deep link, redirect chain, third-party content), any loaded page can call into the bridge.

**iOS.**
```swift
let userContentController = WKUserContentController()
userContentController.add(self, name: "myBridge")   // ❌ no origin check on the JS side
let config = WKWebViewConfiguration()
config.userContentController = userContentController
let webView = WKWebView(frame: .zero, configuration: config)
webView.load(URLRequest(url: externalURL))   // arbitrary URL
```

**Sniff.**
- Android: `addJavascriptInterface(` in a file that also has `loadUrl(` with a variable URL
- iOS: `WKUserContentController.add(_:name:)` paired with a `WKWebView.load(` of a variable URL

**Fix.**
- Restrict the WebView to known origins. Override `shouldOverrideUrlLoading` (Android) / `decidePolicyFor` (iOS) and return `false` / `.cancel` for non-allowlisted hosts.
- For bridges, validate the message origin in the handler:
  ```swift
  func userContentController(_ controller: WKUserContentController,
                              didReceive message: WKScriptMessage) {
      guard message.frameInfo.securityOrigin.host == "trusted.example.com" else { return }
      // process
  }
  ```

---

## P2 — `LSApplicationQueriesSchemes` probing installed-app inventory

**iOS — `Info.plist`:**
```xml
<key>LSApplicationQueriesSchemes</key>
<array>
    <string>fb</string>
    <string>whatsapp</string>
    <string>instagram</string>
    <string>twitter</string>
    <string>tiktok</string>
    <!-- ... many entries -->
</array>
```

Each entry lets your app probe whether the named app is installed via `UIApplication.canOpenURL`. A long list (5+) effectively fingerprints the user's installed apps.

**Sniff.** `LSApplicationQueriesSchemes` additions with 5+ entries → P2 reminder to confirm each scheme is genuinely used and disclosed in the privacy policy.

---

## Output

```
[<file>:<line>] <severity> — Security/Exposure — <rule> · <one-sentence fix>
```
