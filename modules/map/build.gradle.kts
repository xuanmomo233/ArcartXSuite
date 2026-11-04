plugins {
    id("java")
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly(project(":axs-core"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly(files("../../libs/Multiverse-Core-5.8.1.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
}

tasks.test { useJUnitPlatform() }

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Map")
    archiveClassifier.set("")
}

