import java.util.Properties

// Load local.properties for credentials
val localProperties = Properties()
val localPropertiesFile = file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val githubUser = System.getenv("GITHUB_USERNAME") ?: localProperties["gpr.user"]?.toString() ?: ""
val githubToken = System.getenv("GITHUB_TOKEN") ?: localProperties["gpr.key"]?.toString() ?: ""

if (githubUser.isEmpty() || githubToken.isEmpty()) {
    logger.warn("GitHub credentials missing. Set GITHUB_USERNAME/GITHUB_TOKEN env vars or gpr.user/gpr.key in local.properties. GitHub Package Registry repos will fail to resolve.")
}

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    mavenLocal()
    maven {
      url = uri("https://maven.pkg.github.com/dmdbrands/ggBluetoothNativeLibrary")
      credentials {
        username = githubUser
        password = githubToken
      }
    }
    maven {
      url = uri("https://maven.pkg.github.com/dmdbrands/vico")
      credentials {
        username = githubUser
        password = githubToken
      }
    }
  }
}

rootProject.name = "Me App"
include(":app")
include(":notification")
include(":app:healthconnect")
include(":app:wificonnect")
include(":app:appsync")
include(":bleWrapper")
// include(":ggBluetoothLibrary")
include(":iam")
include(":benchmark")

// Local vico development — comment out to use published GPR version
//includeBuild("/Users/selvakumar/Projects/vico") {
//  dependencySubstitution {
//    substitute(module("com.dmdbrands.lib:compose-android")).using(project(":vico:compose"))
//  }
//}
