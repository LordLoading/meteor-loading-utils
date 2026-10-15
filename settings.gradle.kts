pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "FabricSnapshot"
            url = uri("https://maven.fabricmc.net/snapshot")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "LUtils"
