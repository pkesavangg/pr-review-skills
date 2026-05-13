import java.util.Properties

val localProperties = Properties().apply {
  val f = file("local.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}
val gprUser: String = System.getenv("GITHUB_USERNAME")
  ?: localProperties.getProperty("gpr.user")
  ?: ""
val gprKey: String = System.getenv("GITHUB_TOKEN")
  ?: localProperties.getProperty("gpr.key")
  ?: ""

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
        uri("https://maven.pkg.github.com/gg-engineering/ggBluetoothNativeLibrary")
      credentials {
        username = gprUser
        password = gprKey
      }
    }
    maven {
      url =
        uri("https://maven.pkg.github.com/gg-engineering/vico")
      credentials {
        username = gprUser
        password = gprKey
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
