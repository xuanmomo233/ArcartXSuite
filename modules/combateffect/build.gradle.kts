plugins {
    id("java")
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly(project(":axs-core"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly(files("../../libs/MythicLib-dist-1.7.1-49.jar"))
    compileOnly(files("../../libs/Mythic-Dist-5.8.0.jar"))
    compileOnly(files("../../libs/ArcartX-2.7.92.jar"))
    testImplementation(project(":axs-api"))
    testImplementation(project(":axs-core"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testRuntimeOnly(files("../../libs/ArcartX-2.7.92.jar"))
}

tasks.test {
    useJUnitPlatform()
    inputs.files(providers.environmentVariable("AXS_CHRONOS_TEST_JAR").map { listOf(file(it)) }.orElse(emptyList()))
    inputs.files(providers.environmentVariable("AXS_CHRONOS_ARIA_JAR").map { listOf(file(it)) }.orElse(emptyList()))
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-CombatEffect")
    archiveClassifier.set("")
}

