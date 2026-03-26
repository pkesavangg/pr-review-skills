import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.google.service)
  alias(libs.plugins.firebase.crashlytics.plugin)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  id("kotlin-parcelize")
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.proto)
  alias(libs.plugins.baselineprofile)
  id("jacoco")
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

android {
  namespace = "com.dmdbrands.gurus.weight"
  compileSdk = 36
  ndkVersion = "28.2.13676358"
  defaultConfig {
    applicationId = "com.dmdbrands.gurus.weight"
    minSdk = 26
    targetSdk = 36
    versionCode = 800000
    versionName = "5.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // 16KB page alignment for Android 15 compliance
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }

    // Enable 16KB page alignment for Android 15 compliance
    externalNativeBuild {
      cmake {
        arguments += listOf("-DANDROID_PAGE_SIZE_AGNOSTIC=ON")
      }
    }
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  buildTypes {
    debug {
      enableUnitTestCoverage = true
      buildConfigField(
        "String",
        "BASE_URL",
        "\"https://api.weightgurus.com/v3/\"",
      )
      buildConfigField("Boolean", "ENABLE_ANALYTICS", "false")
    }
release {
      isMinifyEnabled = true
      isShrinkResources = true
      configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
        mappingFileUploadEnabled = true
      }
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      buildConfigField(
        "String",
        "BASE_URL",
        "\"https://api.weightgurus.com/v3/\"",
      )
      buildConfigField("Boolean", "ENABLE_ANALYTICS", "true")
    }
  }
  packaging {
    resources {
      excludes += listOf("META-INF/NOTICE", "META-INF/LICENSE", "META-INF/*.kotlin_module")
    }
    // 16KB page alignment for Android 15 compliance
    jniLibs {
      useLegacyPackaging = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  android.applicationVariants.all {
    val variantName = this.name // get the variant name here

    outputs.all {
      val outputImpl = this as BaseVariantOutputImpl
      val appName = "Weight gurus"
      val versionCode = this.versionCode
      val timestamp = SimpleDateFormat("yyyyMMdd").format(Date())
      outputImpl.outputFileName =
        "$appName-$variantName-v$versionName($versionCode)-$timestamp.apk"
    }
  }
}

dependencies {
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.androidx.hilt.navigation.fragment)
  implementation(libs.androidx.core.splashscreen)
  // Existing dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.ui.test.junit4.android)
  implementation(libs.androidx.foundation.layout)
  implementation(libs.androidx.runtime.saveable)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.hilt.common)
  implementation(libs.androidx.hilt.work)
  // Unit test dependencies
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.truth)
  testImplementation(libs.mockwebserver)
  // Instrumented test dependencies
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.truth)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
  debugImplementation(libs.leakcanary.android)
  implementation(libs.hilt.navigation.compose)

  // browser
  implementation(libs.androidx.browser)

  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.android.compiler)

  // Retrofit
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.kotlinx.serialization.json)

  // Room dependencies
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Datastore
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.datastore.preferences.core)
  implementation(libs.gson)

  // Firebase
  // Import the Firebase BoM
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging.ktx)
  // When using the BoM, you don't specify versions in Firebase library dependencies
  // Add the dependency for the Firebase SDK for Google Analytics
  implementation(libs.firebase.analytics)
  // Firebase Crashlytics
  implementation(libs.firebase.crashlytics)

  // Security - EncryptedSharedPreferences
  implementation(libs.androidx.security.crypto)

  // Protobuf dependencies
  implementation(libs.protobuf.javalite)

  // Timber
  implementation(libs.timber)

  // modules
  implementation(project(":notification"))
  implementation(project(":app:healthconnect"))
  implementation(project(":app:appsync"))
  implementation(project(":bleWrapper"))
  implementation(project(":app:wificonnect"))
  implementation(project(":iam"))

  // implementation(project(":ggBluetoothLibrary"))

  // Baseline Profiles
  implementation(libs.androidx.profileinstaller)
  baselineProfile(project(":benchmark"))

  // Play Store Review
  implementation(libs.play.review)
  implementation(libs.play.review.ktx)

  // Vico charts

  implementation(libs.vico.core)
  implementation(libs.lib.vico.compose)
  implementation(libs.lib.vico.compose.m3)

  // Gif Image
  implementation(libs.coil.compose) // For Jetpack Compose
  implementation(libs.coil.gif)

  // foundation-pullrefresh
  // implementation(libs.androidx.foundation.pullrefresh)
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}"
  }
  generateProtoTasks {
    all().forEach {
      it.builtins {
        create("java") {
          option("lite")
        }
      }
    }
  }
}

// Preload ByteBuddy agent at JVM startup so MockK doesn't load it dynamically.
// Fixes "A Java agent has been loaded dynamically" warning on JDK 17+.
tasks.withType<Test> {
  useJUnitPlatform()
  doFirst {
    val agentJar = classpath.find { it.name.contains("byte-buddy-agent") }
    if (agentJar != null) {
      jvmArgs("-javaagent:$agentJar")
    }
  }
}

// ---------------------------------------------------------------------------
// JaCoCo coverage report: ./gradlew :app:jacocoTestReport
// ---------------------------------------------------------------------------
tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")

  reports {
    html.required.set(true)
    xml.required.set(true)
  }

  // Patterns that match generated / framework code — excluded from coverage
  val jacocoExcludes = listOf(
    // Android build-generated
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    // Hilt-generated
    "**/hilt_aggregated_deps/**",
    "**/*_HiltComponents*",
    "**/*_HiltModules*",
    "**/Hilt_*",
    "**/*_MembersInjector*",
    "**/*_Factory*",
    // Room-generated (DAO implementations, DB impl)
    "**/*_Impl*",
    // Compose compiler–generated
    "**/*ComposableSingletons*",
    // Protobuf-generated
    "**/*OuterClass*",
  )

  val kotlinClassDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile
  classDirectories.setFrom(
    fileTree(kotlinClassDir) { exclude(jacocoExcludes) },
  )

  sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

  executionData.setFrom(
    fileTree(layout.buildDirectory.get().asFile) {
      include(
        // AGP 8.x location when enableUnitTestCoverage = true
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        // Fallback for older AGP / legacy jacoco plugin
        "jacoco/testDebugUnitTest.exec",
      )
    },
  )
}
