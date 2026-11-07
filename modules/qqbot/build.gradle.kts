plugins {
    id("java")
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.java-websocket:Java-WebSocket:1.5.7")
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation(project(":axs-api"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.jetbrains:annotations:24.1.0")
    testImplementation("com.zaxxer:HikariCP:5.1.0")
    testImplementation("org.xerial:sqlite-jdbc:3.46.0.0")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("org.java-websocket:Java-WebSocket:1.5.7")
    testImplementation("me.clip:placeholderapi:2.11.7")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-QQBot")
    archiveClassifier.set("")
}

tasks.test {
    useJUnitPlatform()
    val testTemp = layout.buildDirectory.dir("test-tmp")
    systemProperty("java.io.tmpdir", testTemp.get().asFile.absolutePath)
    doFirst { testTemp.get().asFile.mkdirs() }
}
