import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.google.service)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  id("kotlin-parcelize")
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.proto)
  kotlin("kapt")
}

android {
  namespace = "com.dmdbrands.gurus.weight"
  compileSdk = 36
  ndkVersion = "28.2.13676358"
  defaultConfig {
    applicationId = "com.dmdbrands.gurus.weight"
    minSdk = 26
    targetSdk = 36
    versionCode = 800002
    versionName = "5.0.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // 16KB page alignment for Android 15 compliance
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }

    // 16KB page alignment is handled by linker flags in appsync/scripts/build-android-libs.sh
    // No CMakeLists.txt exists — externalNativeBuild is not needed
  }

  buildTypes {
    debug {
    }
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
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
  kotlinOptions {
    jvmTarget = "11"
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
  implementation(libs.kotlin.reflect)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.androidx.hilt.navigation.fragment)
  implementation(libs.androidx.core.splashscreen)
  // Existing dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
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
  implementation(libs.work.runtime.ktx)
  implementation(libs.androidx.hilt.common)
  implementation(libs.androidx.hilt.work)
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
  implementation(libs.hilt.navigation.compose)

  // browser
  implementation(libs.androidx.browser)

  // Hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.android.compiler)

  // Retrofit
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.kotlinx.serialization.json)

  // Room dependencies
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.sqlite.bundled)
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

  // Datastore
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.datastore.preferences.core)
  implementation(libs.gson)

  // Protobuf dependencies
  implementation(libs.protobuf.javalite)
  implementation(libs.androidx.datastore)

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
  // Play Store Review
  implementation(libs.play.review)
  implementation(libs.play.review.ktx)

  // Vico charts

  implementation(libs.vico.core)
  implementation(libs.lib.vico.compose)
  implementation(libs.lib.vico.compose.m3)

  // Gif Image
  implementation(libs.coil.compose)       // For Jetpack Compose
  implementation(libs.coil.gif)

  // foundation-pullrefresh
  // implementation(libs.androidx.foundation.pullrefresh)
}

// Allow references to generated code
kapt {
  correctErrorTypes = true
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.25.3"
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
