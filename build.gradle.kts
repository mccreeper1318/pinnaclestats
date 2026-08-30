plugins {
    java
}

group = "org.pinnaclesmp"
version = "1.0.11"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    // Paper 26.1+ uses the new build-based version format.
    // The old 26.2-R0.1-SNAPSHOT coordinate does not exist.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("PinnacleStats")
}

tasks.test {
    useJUnitPlatform()
}
