plugins {
    id("java")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation(project(":axs-api"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly(project(":axs-api"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
}

tasks.test { useJUnitPlatform() }

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Vanilla")
    archiveClassifier.set("")
}
