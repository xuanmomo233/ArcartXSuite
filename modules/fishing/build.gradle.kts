plugins {
    id("java")
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly(project(":axs-core"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation(project(":axs-api"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("simulateFishing") {
    group = "verification"
    description = "Run seeded fishing trials against the server's fight logic."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("xuanmo.arcartxsuite.fishing.service.FishingSimulationCli")

    val runtime = providers.gradleProperty("fishingAriaJar").orNull?.let { rootProject.file(it) }
    val scenario = providers.gradleProperty("fishingScenario").orNull?.let { rootProject.file(it) }
    val dataDir = providers.gradleProperty("fishingDataDir").orNull?.let { rootProject.file(it) }
    val trace = providers.gradleProperty("fishingTrace").orNull?.let { rootProject.file(it) }
    if (runtime != null) classpath(runtime)
    args(dataDir ?: project.file("src/main/resources"), scenario ?: project.file("simulation/legendary-carp.yml"), runtime?.name ?: "")
    if (trace != null) args(trace)
    doFirst {
        if (runtime == null || !runtime.isFile) {
            throw GradleException("Set -PfishingAriaJar to an ArcartX or Aria jar containing the Aria engine.")
        }
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Fishing")
    archiveClassifier.set("")
}
