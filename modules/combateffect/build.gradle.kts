plugins {
    id("java")
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly(project(":axs-core"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly(files("../../libs/MythicLib-dist-1.7.1-49.jar"))
    compileOnly(files("../../libs/Mythic-Dist-5.6.1-SNAPSHOT.jar"))
    compileOnly(files("../../libs/Chronos_Plugin-2.0.1.jar"))
    compileOnly(files("../../libs/ArcartX-2.7.77.jar"))
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-CombatEffect")
    archiveClassifier.set("")
}

