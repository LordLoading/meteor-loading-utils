plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

val baritoneNested = configurations.create("baritoneNested") {
    isTransitive = false
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)
    modRuntimeOnly(libs.fabric.api)

    // Meteor
    modImplementation(libs.meteor.client)

    // Mods
    modImplementation(libs.litematica)
    modImplementation(libs.malilib)
    modCompileOnly(libs.baritone)
    modRuntimeOnly(libs.baritone)
    baritoneNested(libs.baritone)
}

val extractNetherPathfinder = tasks.register<Copy>("extractNetherPathfinder") {
    from(providers.provider { zipTree(baritoneNested.singleFile) }) {
        include("META-INF/jars/nether-pathfinder-*.jar")
        eachFile { path = "nether-pathfinder.jar" }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("baritone"))
}

tasks.named("classes") {
    dependsOn(extractNetherPathfinder)
}

loom {
    runs {
        named("client") {
            systemProperties.put("fabric.addMods", layout.buildDirectory.file("baritone/nether-pathfinder.jar").get().asFile.absolutePath)
        }
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
