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
    compileOnly("redis.clients:jedis:5.2.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(files(rootProject.project(":axs-api").layout.buildDirectory.dir("classes/java/main"))
        .builtBy(":axs-api:compileJava"))
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.0.0")
    testRuntimeOnly("com.mysql:mysql-connector-j:8.4.0")
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
    inputs.property("durableMysqlUrl", providers.environmentVariable("AXS_EVENTPACKET_MYSQL_URL").orElse(""))
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Mail")
    archiveClassifier.set("")
}

