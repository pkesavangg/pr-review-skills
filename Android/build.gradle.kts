// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.service) apply false
    alias(libs.plugins.firebase.crashlytics.plugin) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.owasp.dependency.check)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        ignoreFailures.set(true)
        outputToConsole.set(true)
        baseline.set(file("ktlint-baseline.xml"))

}

    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        buildUponDefaultConfig = false
        parallel = true
        baseline = file("${rootProject.projectDir}/config/detekt/detekt-baseline.xml")
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "11"
        exclude("**/build/**", "**/vico/**", "**/bleWrapper/**")
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "11"
        exclude("**/build/**", "**/vico/**", "**/bleWrapper/**")
    }
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "$projectDir/config/owasp-suppressions.xml"
    formats = listOf("HTML", "JSON")
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.absolutePath
    analyzers {
        assemblyEnabled = false
    }
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
}
