plugins {
    id("java")
}

version = "2.0.0"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(project(":axs-api"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(files("../../libs/Chemdah-1.1.33-FREE.jar"))
    compileOnly(project(":axs-api"))
    compileOnly(project(":axs-core"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly(files("../../libs/Chemdah-1.1.33-FREE.jar"))
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    includeEmptyDirs = false
    archiveBaseName.set("ArcartXSuite-QuestGPS")
    archiveClassifier.set("")
    // 仅编译期占位：运行时由 TabooLib 重定位提供，避免污染最终 JAR / 与运行时类冲突。
    exclude("ink/ptms/chemdah/taboolib/**")
    exclude("kotlin1822/**")
}

