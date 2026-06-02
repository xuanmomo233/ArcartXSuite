plugins {
    id("java")
}

dependencies {
    compileOnly(project(":proxy:common"))
    compileOnly("net.md-5:bungeecord-api:1.20-R0.2")
    compileOnly("org.jetbrains:annotations:24.1.0")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Proxy-Bungee")
    archiveClassifier.set("")
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
}

val publishProxyJar by tasks.registering {
    dependsOn(tasks.jar)
    val src = tasks.jar.get().archiveFile.get().asFile
    val dst = rootProject.layout.buildDirectory.dir("ArcartXSuite/proxy").get().file(src.name).asFile
    inputs.file(src)
    outputs.file(dst)
    doLast {
        dst.parentFile.mkdirs()
        src.copyTo(dst, overwrite = true)
    }
}

tasks.build {
    dependsOn(publishProxyJar)
}
