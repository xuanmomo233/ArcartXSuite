plugins {
    id("java")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly(project(":axs-api"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("com.belerweb:pinyin4j:2.5.1")
    compileOnly("net.luckperms:api:5.4")
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
    archiveBaseName.set("ArcartXSuite-Market")
    archiveClassifier.set("")
}
