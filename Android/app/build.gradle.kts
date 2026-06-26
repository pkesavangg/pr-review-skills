import com.android.build.api.variant.impl.VariantOutputImpl
import com.android.ide.common.vectordrawable.Svg2Vector
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.google.proto)
  alias(libs.plugins.baselineprofile)
  // parcelize ships with AGP's bundled Kotlin facet. Do NOT alias it via libs.versions.toml —
  // Gradle errors with "plugin already on classpath with unknown version" because AGP applies
  // kotlin-android transitively.
  id("org.jetbrains.kotlin.plugin.parcelize")
  id("jacoco")
}

// Apply Firebase plugins only when google-services.json is present (CI injects it via secret).
// Fail fast if a release build is requested without the file — better to break CI loudly than
// ship a release without Crashlytics, Firebase, or mapping-file upload.
val googleServicesFile = file("google-services.json")
val isReleaseBuild = gradle.startParameter.taskNames.any { task ->
  task.contains("Release", ignoreCase = true) || task.contains("bundle", ignoreCase = true)
}
if (isReleaseBuild && !googleServicesFile.exists()) {
  throw GradleException(
    "google-services.json is required for release builds but was not found at ${googleServicesFile.absolutePath}. " +
      "Ensure the CI secret is injected before building release.",
  )
}
if (googleServicesFile.exists()) {
  apply(plugin = libs.plugins.google.service.get().pluginId)
  apply(plugin = libs.plugins.firebase.crashlytics.plugin.get().pluginId)
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
    versionCode = 810000
    versionName = "5.0.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // 16KB page alignment for Android 15 compliance
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    }

    // 16KB page alignment is handled by linker flags in appsync/scripts/build-android-libs.sh
    // No CMakeLists.txt exists — externalNativeBuild is not needed
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
        "\"http://ec2-13-217-141-203.compute-1.amazonaws.com:3005/v3/\"",
      )
      buildConfigField("Boolean", "ENABLE_ANALYTICS", "false")
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      if (googleServicesFile.exists()) {
        configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
          mappingFileUploadEnabled = true
        }
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
  sourceSets {
    getByName("androidTest").assets.srcDirs("$projectDir/schemas")
  }
}

// onVariants replaces applicationVariants under AGP 9 newDsl. The VariantOutputImpl cast
// is required because outputFileName is not yet on the public VariantOutput API.
// Drop the cast once Google promotes outputFileName.
// AGP component tracker: https://issuetracker.google.com/issues?q=componentid:192709%20outputFileName
// Note: buildDateStamp is captured at configuration time; with config cache enabled the value
// would be cached across days. Acceptable today (no config cache); switch to a ValueSource if enabled.
androidComponents {
  val appName = "Weight gurus"
  val buildDateStamp = SimpleDateFormat("yyyyMMdd").format(Date())
  onVariants { variant ->
    variant.outputs.forEach { output ->
      val versionName = output.versionName.orNull ?: "0.0.0"
      val versionCode = output.versionCode.orNull ?: 0
      val outputImpl = output as? VariantOutputImpl ?: error(
        "APK rename: expected VariantOutputImpl, got ${output::class.qualifiedName}. " +
          "AGP upgrade likely broke the cast — see MA-3818.",
      )
      outputImpl.outputFileName.set(
        "$appName-${variant.name}-v$versionName($versionCode)-$buildDateStamp.apk",
      )
    }
  }
}

configurations.configureEach {
  resolutionStrategy {
    force(libs.androidx.junit.get().toString())
    force(libs.androidx.espresso.core.get().toString())
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
  testImplementation(kotlin("test"))
  testImplementation(kotlin("reflect")) // callSuspend on private suspend fns under test
  testImplementation(libs.turbine)
  testImplementation(libs.truth)
  testImplementation(libs.mockwebserver)
  // Instrumented test dependencies
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.androidx.test.uiautomator)
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
  implementation(libs.vico.gg)

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
// JaCoCo — shared configuration
// ---------------------------------------------------------------------------

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

// Class files that match the on-the-fly coverage exec: AGP compiles Kotlin via the
// built-in kotlinc and runs unit tests against the ASM-transformed output, so the
// JaCoCo class IDs correspond to these dirs — NOT the legacy build/tmp/kotlin-classes.
val jacocoClassDirectories = fileTree(
  layout.buildDirectory.dir("intermediates/classes/debug/transformDebugClassesWithAsm/dirs").get().asFile,
) { exclude(jacocoExcludes) }

val jacocoExecutionData = fileTree(layout.buildDirectory.get().asFile) {
  include(
    // AGP 8.x location when enableUnitTestCoverage = true
    "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
    // Fallback for older AGP / legacy jacoco plugin
    "jacoco/testDebugUnitTest.exec",
  )
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

  classDirectories.setFrom(jacocoClassDirectories)
  sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
  executionData.setFrom(jacocoExecutionData)
}

// ---------------------------------------------------------------------------
// JaCoCo coverage verification: ./gradlew :app:jacocoTestCoverageVerification
// Enforces minimum 80 % line coverage — build fails if threshold is not met.
// ---------------------------------------------------------------------------
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
  dependsOn("testDebugUnitTest")

  classDirectories.setFrom(jacocoClassDirectories)
  executionData.setFrom(jacocoExecutionData)

  violationRules {
    rule {
      limit {
        counter = "LINE"
        value = "COVEREDRATIO"
        minimum = "0.80".toBigDecimal()
      }
    }
  }
}

/**
 * Convert SVG files to Android Vector Drawables using Google's Svg2Vector
 * (the same converter Android Studio's "Vector Asset" tool uses).
 *
 * Usage:
 *   ./gradlew :app:convertSvg -PsvgInput="/path/to/folder_or_file.svg"
 *   ./gradlew :app:convertSvg -PsvgInput="/path/to/folder" -PsvgPrefix="scale_"
 *
 * Options (passed via -P flags):
 *   svgInput   — (required) path to a single SVG file or a folder of SVGs
 *   svgOutput  — (optional) output folder, defaults to res/drawable/
 *   svgPrefix  — (optional) prefix to add to output filenames (e.g. "scale_", "ic_")
 *   svgReplace — (optional) "true" to delete existing .png with the same name
 */
tasks.register("convertSvg") {
  group = "assets"
  description = "Convert SVG → Android Vector Drawable XML using Google's Svg2Vector"

  doLast {
    val inputPath = project.findProperty("svgInput")?.toString()
      ?: error("Missing -PsvgInput. Usage: ./gradlew :app:convertSvg -PsvgInput=\"/path/to/svgs\"")

    val defaultOutput = file("src/main/res/drawable")
    val outputDir = file(project.findProperty("svgOutput")?.toString() ?: defaultOutput.absolutePath)
    val prefix = project.findProperty("svgPrefix")?.toString() ?: ""
    val replacePng = project.findProperty("svgReplace")?.toString()?.toBoolean() ?: true

    val inputFile = file(inputPath)
    if (!inputFile.exists()) error("Input not found: $inputPath")

    val svgFiles = if (inputFile.isDirectory) {
      inputFile.walkTopDown().filter { it.extension.equals("svg", ignoreCase = true) }.toList()
    } else {
      listOf(inputFile)
    }

    if (svgFiles.isEmpty()) error("No SVG files found in: $inputPath")

    outputDir.mkdirs()
    var success = 0
    var failed = 0

    svgFiles.forEach { svg ->
      val baseName = svg.nameWithoutExtension
        .replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(" ", "_")
        .replace("(", "").replace(")", "")
        .replace("-", "_")
        .lowercase()
        .replace(Regex("_+"), "_")
        .trimEnd('_')

      val outputName = if (prefix.isNotEmpty() && !baseName.startsWith(prefix)) {
        "${prefix}$baseName"
      } else {
        baseName
      }

      val outputFile = File(outputDir, "${outputName}.xml")
      val pngFile = File(outputDir, "${outputName}.png")

      try {
        val bos = ByteArrayOutputStream()
        val conversionLog = Svg2Vector.parseSvgToXml(svg.toPath(), bos)
        val xmlContent = bos.toString(Charsets.UTF_8.name())

        if (xmlContent.isBlank()) {
          println("FAIL: ${svg.name} → empty output")
          if (conversionLog.isNotEmpty()) println("  Errors: $conversionLog")
          failed++
          return@forEach
        }

        outputFile.writeText(xmlContent)

        if (replacePng && pngFile.exists()) {
          pngFile.delete()
          println("  OK: ${svg.name} → ${outputName}.xml (replaced ${outputName}.png)")
        } else {
          println("  OK: ${svg.name} → ${outputName}.xml")
        }

        if (conversionLog.isNotEmpty()) {
          conversionLog.lines().forEach { line ->
            println(line)
          }
        }
        success++
      } catch (e: Exception) {
        println("FAIL: ${svg.name} → ${e.message}")
        failed++
      }
    }

    println("\nDone: $success succeeded, $failed failed out of ${svgFiles.size} total")
    if (failed > 0) {
      error("$failed of ${svgFiles.size} SVGs failed to convert")
    }
  }
}
