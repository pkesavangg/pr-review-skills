# In-App Browser Guide

A simple guide to using the in-app browser in your Android app.

## Quick Start

### 1. Basic Usage
```kotlin
// Open a URL from any Activity or Fragment
CustomTabLauncher.launchUrl(context, "https://example.com")
```

### 2. Using in Compose
```kotlin
@Composable
fun MyScreen() {
    // Get the launcher
    val customTabLauncher = rememberLifecycleAwareCustomTab()

    // Use it in a button
    Button(onClick = {
        customTabLauncher.launchUrl("https://example.com")
    }) {
        Text("Open Website")
    }
}
```

### 3. Using in ViewModel
```kotlin
class MyViewModel @Inject constructor(
    private val customTabManager: CustomTabManager
) : ViewModel() {

    fun openWebsite(url: String) {
        customTabManager.launchUrl(url)
    }
}
```

## Lifecycle Management

### 1. Using LifecycleObserver
```kotlin
class MyActivity : AppCompatActivity() {
    private val customTabManager = CustomTabManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Register as lifecycle observer
        lifecycle.addObserver(customTabManager)
    }
}
```

### 2. Custom LifecycleObserver Implementation
```kotlin
class CustomTabLifecycleObserver(
    private val context: Context
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        // Bind service when activity starts
        CustomTabManager.bindCustomTabsService(context)
    }

    override fun onStop(owner: LifecycleOwner) {
        // Unbind service when activity stops
        CustomTabManager.unbindCustomTabsService(context)
    }
}

// Usage in Activity
class MyActivity : AppCompatActivity() {
    private val customTabObserver = CustomTabLifecycleObserver(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(customTabObserver)
    }
}
```

### 3. Using with Fragment
```kotlin
class MyFragment : Fragment() {
    private val customTabManager = CustomTabManager()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Register as lifecycle observer
        viewLifecycleOwner.lifecycle.addObserver(customTabManager)
    }
}
```

### 4. Using with Compose

#### Basic Compose Integration
```kotlin
@Composable
fun MyScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val customTabManager = remember { CustomTabManager() }

    // Register lifecycle observer
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(customTabManager)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(customTabManager)
        }
    }

    Button(onClick = {
        customTabManager.launchUrl("https://example.com")
    }) {
        Text("Open Website")
    }
}
```

#### Custom Compose Observer
```kotlin
@Composable
fun rememberCustomTabLauncher(): CustomTabManager {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val customTabManager = remember { CustomTabManager() }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                CustomTabManager.bindCustomTabsService(context)
            }

            override fun onStop(owner: LifecycleOwner) {
                CustomTabManager.unbindCustomTabsService(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return customTabManager
}

// Usage in Composable
@Composable
fun MyScreen() {
    val customTabManager = rememberCustomTabLauncher()

    Button(onClick = {
        customTabManager.launchUrl("https://example.com")
    }) {
        Text("Open Website")
    }
}
```

#### Using with ViewModel in Compose
```kotlin
class MyViewModel @Inject constructor(
    private val customTabManager: CustomTabManager
) : ViewModel() {
    fun openWebsite(url: String) {
        customTabManager.launchUrl(url)
    }
}

@Composable
fun MyScreen(
    viewModel: MyViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Register lifecycle observer for ViewModel
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(viewModel)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(viewModel)
        }
    }

    Button(onClick = {
        viewModel.openWebsite("https://example.com")
    }) {
        Text("Open Website")
    }
}
```

## Common Use Cases

### 1. Opening Links from Text
```kotlin
Text(
    text = "Visit our website",
    modifier = Modifier.clickable {
        CustomTabLauncher.launchUrl(context, "https://example.com")
    }
)
```

### 2. Opening Links from Menu
```kotlin
MenuItem(
    text = "Privacy Policy",
    onClick = {
        CustomTabLauncher.launchUrl(context, "https://example.com/privacy")
    }
)
```

### 3. Opening Links with Error Handling
```kotlin
try {
    CustomTabLauncher.launchUrl(context, url)
} catch (e: Exception) {
    // Show error message to user
    Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
}
```

## Security Tips

1. **Always validate URLs**
```kotlin
fun isValidUrl(url: String): Boolean {
    return try {
        val uri = Uri.parse(url)
        uri.scheme == "http" || uri.scheme == "https"
    } catch (e: Exception) {
        false
    }
}

// Use it
if (isValidUrl(url)) {
    CustomTabLauncher.launchUrl(context, url)
}
```

2. **Disable sharing (if needed)**
```kotlin
CustomTabIntentBuilder()
    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
    .build()
```

## Best Practices

1. **Pre-warm the browser** (add to your Application class)
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm for faster loading
        CustomTabManager.bindCustomTabsService(this)
    }
}
```

2. **Clean up** (in your Activity)
```kotlin
override fun onDestroy() {
    super.onDestroy()
    CustomTabManager.unbindCustomTabsService(this)
}
```

## Customization Examples

### 1. Change Toolbar Color
```kotlin
CustomTabIntentBuilder()
    .setToolbarColor(ContextCompat.getColor(context, R.color.primary))
    .build()
```

### 2. Add Animations
```kotlin
CustomTabIntentBuilder()
    .setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left)
    .setExitAnimations(context, R.anim.slide_in_left, R.anim.slide_out_right)
    .build()
```

### 3. Track Navigation Events
```kotlin
CustomTabEventListener(
    onNavigationStarted = { url ->
        // User started loading a page
    },
    onNavigationFinished = { url ->
        // Page finished loading
    }
)
```

## Troubleshooting

1. **Link doesn't open**
   - Check if URL is valid
   - Check internet connection
   - Try fallback WebView

2. **Slow loading**
   - Use pre-warming
   - Check network speed
   - Consider pre-fetching

3. **WebView fallback issues**
   - Check WebView settings
   - Verify URL permissions
   - Check for JavaScript errors

4. **Lifecycle issues**
   - Ensure proper observer registration
   - Check for memory leaks
   - Verify service binding/unbinding

## Dependencies

Add to your `build.gradle.kts`:
```kotlin
dependencies {
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
```

## Need Help?

If you encounter any issues:
1. Check the URL format
2. Verify internet connection
3. Check if Chrome is installed
4. Look for error messages in logcat
5. Verify lifecycle observer registration