# WiFiConnect Android Library

A modern, scalable Android library for WiFi smart configuration, supporting Esptouch, SmartConfig, and AP mode, with a unified API and clean architecture.

---

## 📁 Folder Structure

```
com.greatergoods.lib.wificonnect/
├── model/         # Data classes and sealed result/request types
│   ├── ApModeModels.kt
│   ├── EsptouchModels.kt
│   ├── SmartConfigModels.kt
│   └── WifiConnectRequest.kt
├── utilities/     # Implementation classes for each connector
│   ├── WifiApConnector.kt
│   ├── WifiEsptouchConnector.kt
│   └── WifiSmartConfigConnector.kt
├── WifiSmartConnectManager.kt  # Unified manager for all operations
```

---

## 🚀 Initialization

Use dependency injection (Hilt) to inject `WifiSmartConnectManager` into your class:

```kotlin
@Inject lateinit var wifiManager: WifiSmartConnectManager
```

Or, if not using DI:

```kotlin
val wifiManager = WifiSmartConnectManager(
    WifiEsptouchConnector(activity),
    WifiApConnector(context),
    WifiSmartConfigConnector(context)
)
```

---

## 🧩 Usage: Unified API

All operations are performed via the `connect` suspend function, which takes a `WifiConnectRequest` sealed class and returns a `WifiConnectResult` sealed class.

### Example: Esptouch

```kotlin
val params = EsptouchParams(
    ssid = "YourSSID",
    bssid = "BSSID",
    token = "token",
    password = "password",
    userNumber = 1
)
val result = wifiManager.connect(WifiConnectRequest.Esptouch(params))
when (result) {
    is WifiConnectResult.Esptouch -> { /* handle EsptouchResult */ }
    is WifiConnectResult.SmartConfig -> { /* ... */ }
    is WifiConnectResult.ApMode -> { /* ... */ }
}
```

### Example: SmartConfig

```kotlin
val params = SmartConfigParams(
    ssid = "YourSSID",
    password = "password",
    userNumber = 1,
    tokenHexString = "token"
)
val result = wifiManager.connect(WifiConnectRequest.SmartConfig(params))
```

### Example: AP Mode

```kotlin
val params = ApConnectParams(
    ssid = "YourSSID",
    password = "password",
    userNumber = 1,
    tokenHexString = "token"
)
val result = wifiManager.connect(WifiConnectRequest.ApMode(params))
```

---

## 🛑 Stopping Operations

You can stop any ongoing operation:

```kotlin
wifiManager.stopEsptouch()
wifiManager.stopSmartConfig()
wifiManager.stopApMode()
wifiManager.stopAll() // Stops all
```

---

## 🏷️ Data Types & Parameters

### EsptouchParams

- `ssid: String`
- `bssid: String`
- `token: String`
- `password: String`
- `userNumber: Int`

### SmartConfigParams

- `ssid: String`
- `password: String`
- `userNumber: Int`
- `tokenHexString: String`

### ApConnectParams

- `ssid: String`
- `password: String`
- `userNumber: Int`
- `tokenHexString: String`

---

## 🏁 Result Types

### WifiConnectResult (sealed class)

- `Esptouch(result: EsptouchResult)`
- `SmartConfig(result: SmartConfigResult)`
- `ApMode(result: ApConnectResult)`

### EsptouchResult

- `Success(deviceType: String, deviceMac: String)`
- `Failure(errorMessage: String)`

### SmartConfigResult

- `Success`
- `Failure(errorMessage: String)`

### ApConnectResult

- `Success(buffer: ByteArray)`
- `Failure(errorMessage: String)`

---

## 🛠️ Advanced: Direct Connector Usage

You can also use the connectors directly (not recommended unless you need fine-grained control):

```kotlin
@Inject lateinit var esptouch: WifiEsptouchConnector
val result = esptouch.connect(params)
```

---

## 📝 Notes

- All connect methods are `suspend` and must be called from a coroutine.
- Always use the unified API for best maintainability.
- All static text, error messages, and logging follow project standards.

---

## 📚 License

Copyright (c) Greater Goods. All rights reserved.
