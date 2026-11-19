plugins {
    id("java")
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    testImplementation(project(":axs-api"))
    testImplementation(project(":modules:mail"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    testRuntimeOnly("com.mysql:mysql-connector-j:8.4.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
    inputs.property("eventPacketMysqlUrl", providers.environmentVariable("AXS_EVENTPACKET_MYSQL_URL").orElse(""))
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-EventPacket")
    archiveClassifier.set("")
}

