import com.android.build.gradle.ProguardFiles.getDefaultProguardFile
import org.gradle.kotlin.dsl.android
import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.libs
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.protobuf
import org.gradle.kotlin.dsl.publishing

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.proto)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  kotlin("kapt")
  `maven-publish`
}

android {
  namespace = "com.greatergoods.ggInAppMessaging"
  compileSdk = 36

  defaultConfig {
    minSdk = 26

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")

    // Library version info
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    buildConfigField("String", "LIBRARY_NAME", "\"GG In-App Messaging\"")
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

  kotlinOptions {
    jvmTarget = "11"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.8"
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
      withJavadocJar()
    }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons.extended)

  // Gson for JSON serialization
  implementation(libs.gson)

  // Image loading
  implementation(libs.coil.compose)

  // Timber for logging
  implementation(libs.timber)

  // DataStore dependencies
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.datastore.preferences.core)

  // Protobuf dependencies
  implementation(libs.protobuf.javalite)

  // Kotlin serialization
  implementation(libs.kotlinx.serialization.json)

  // browser
  implementation(libs.androidx.browser)

  // Hilt dependencies
  implementation(libs.hilt.android)
  kapt(libs.hilt.android.compiler)
  implementation(libs.hilt.navigation.compose)

  // ViewModel Compose
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Testing
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
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
