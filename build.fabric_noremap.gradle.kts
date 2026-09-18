import org.gradle.jvm.tasks.Jar

plugins {
    id("net.fabricmc.fabric-loom")
    id("maven-publish")
}

version = "${project.property("mod_version")}+${stonecutter.current.version}"
group = project.property("maven_group") as String
val minecraft: String = if (hasProperty("deps.minecraft")) project.property("deps.minecraft") as String
else stonecutter.current.version

base.archivesName = project.property("archives_base_name") as String

repositories {
    maven("https://maven.terraformersmc.com")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    implementation("net.fabricmc:fabric-loader:${property("deps.loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    runtimeOnly("com.terraformersmc:modmenu:${property("deps.modmenu_version")}")
}

tasks.processResources {
    val modVersion = project.version
    val minecraftVersion = minecraft
    inputs.property("version", modVersion)
    inputs.property("minecraft", minecraftVersion)

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "version" to modVersion,
                "minecraft" to minecraftVersion
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

java {
    withSourcesJar()
    targetCompatibility = JavaVersion.VERSION_25
    sourceCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
    inputs.property("archivesName", project.base.archivesName)

    from(rootProject.file("LICENSE")) {
        rename { "${it}_${base.archivesName}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
}
