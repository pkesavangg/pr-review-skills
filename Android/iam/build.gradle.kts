plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
                "proguard-rules.pro"
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

    // Hilt for dependency injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.greatergoods"
                artifactId = "gg-in-app-messaging"
                version = project.version.toString()

                pom {
                    name.set("GG In-App Messaging")
                    description.set("In-app messaging library for Greater Goods applications")
                    url.set("https://github.com/greatergoods/ggInAppMessagingPackage")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("greatergoods")
                            name.set("Greater Goods")
                            email.set("dev@greatergoods.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/greatergoods/ggInAppMessagingPackage.git")
                        developerConnection.set("scm:git:ssh://github.com/greatergoods/ggInAppMessagingPackage.git")
                        url.set("https://github.com/greatergoods/ggInAppMessagingPackage")
                    }
                }
            }
        }
    }
}
