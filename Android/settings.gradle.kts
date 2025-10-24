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
    maven {
      url =
        uri("https://maven.pkg.github.com/dmdbrands/ggBluetoothNativeLibrary")
      credentials {
        username = "Selva-GG"
        password = "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"
      }
    }
    maven {
      url =
        uri("https://maven.pkg.github.com/dmdbrands/vico")
      credentials {
        username = "Selva-GG"
        password = "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"
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
