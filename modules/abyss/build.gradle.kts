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
    compileOnly(files("../../libs/Mythic-Dist-5.8.0.jar"))
    compileOnly(files("../../libs/FastAsyncWorldEdit-Bukkit-2.11.0.jar"))
    compileOnly(files("../../libs/Adyeshach-2.1.30.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(project(":axs-api"))
    testRuntimeOnly(files(rootProject.project(":axs-core").layout.buildDirectory.dir("classes/java/main"))
        .builtBy(":axs-core:compileJava"))
}

tasks.test { useJUnitPlatform() }
tasks.compileTestJava { options.encoding = "UTF-8"; options.release = 17 }

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Abyss")
    archiveClassifier.set("")
}
