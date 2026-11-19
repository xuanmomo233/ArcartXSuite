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
    compileOnly(files("../../libs/Mythic-Dist-5.8.0.jar"))
    testImplementation(project(":axs-api"))
    testImplementation(project(":axs-core"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("me.clip:placeholderapi:2.11.7")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-BattlePass")
    archiveClassifier.set("")
}

tasks.test {
    useJUnitPlatform()
}
