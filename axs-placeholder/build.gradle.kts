plugins {
    id("java")
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("me.clip:placeholderapi:2.11.7")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Placeholder")
    archiveClassifier.set("")
}
