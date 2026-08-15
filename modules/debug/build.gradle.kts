plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
}

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly(project(":axs-core"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    // WebSocket 服务端库（需打包进 jar，宿主未提供）
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "17"
}

// 将运行时依赖（Java-WebSocket + Kotlin stdlib）打包进模块 jar，
// 使模块独立 ClassLoader 可直接加载。
tasks.jar {
    archiveBaseName.set("ArcartXSuite-Debug")
    archiveClassifier.set("")
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    // 去除依赖 jar 内的签名文件与重复 META-INF，避免打包冲突
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
