plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

android {
  namespace = "com.greatergoods.libs.appsync"
  compileSdk = 36

  defaultConfig {
    minSdk = 26

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  // Pin the JUnit platform + launcher via the BOM so junit-platform-launcher tracks the
  // jupiter/platform version (junit6) instead of resolving an unmanaged version on a clean
  // CI build, which risks a launcher/platform mismatch. (PR #2092 review)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(libs.mockk)
  testImplementation(libs.truth)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  // CameraX core and lifecycle
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.extensions)
  // Compose BOM
  implementation(platform(libs.androidx.compose.bom))
  // Compose interop
  implementation(libs.androidx.ui)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.runtime.livedata)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
}
