plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.splatage"

val baseVersion = "0.2"
val buildNumber = System.getenv("BUILD_NUMBER")
val releaseBuild = System.getenv("RELEASE") == "true"

version = when {
    releaseBuild && !buildNumber.isNullOrBlank() -> "${baseVersion}.${buildNumber}"
    !buildNumber.isNullOrBlank() -> "${baseVersion}.${buildNumber}-SNAPSHOT"
    else -> "${baseVersion}.0-SNAPSHOT"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.helpch.at/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.12.2")

    compileOnly("org.xerial:sqlite-jdbc:3.51.3.0")
    compileOnly("com.mysql:mysql-connector-j:9.6.0")
    compileOnly("com.zaxxer:HikariCP:7.0.2")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")

    testRuntimeOnly("org.xerial:sqlite-jdbc:3.51.3.0")
    testRuntimeOnly("com.mysql:mysql-connector-j:9.6.0")
    testRuntimeOnly("com.zaxxer:HikariCP:7.0.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(
            mapOf(
                "version" to project.version,
            )
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
