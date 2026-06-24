import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

val protectedResourcesDir = layout.buildDirectory.dir("generated/protected-resources")

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    implementation(project(":axs-api"))
    implementation(project(":axs-placeholder"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly(files("../libs/Chemdah-1.1.8.jar"))
    compileOnly(files("../libs/MythicLib-dist-1.7.1-49.jar"))
    compileOnly(files("../libs/Mythic-Dist-5.6.1-SNAPSHOT.jar"))
    compileOnly("me.clip:placeholderapi:2.11.7")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.belerweb:pinyin4j:2.5.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(files("../libs/MythicLib-dist-1.7.1-49.jar"))
    testImplementation("me.clip:placeholderapi:2.11.7")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("../src/main/java"))
        resources {
            setSrcDirs(listOf("../src/main/resources", protectedResourcesDir))
        }
    }
}

val protectYamlResources by tasks.registering(ProtectYamlResourcesTask::class) {
    sourceDir.set(layout.projectDirectory.dir("../src/main/resources"))
    outputDir.set(protectedResourcesDir)
}

val obfuscatedJar = layout.buildDirectory.file("libs/ArcartX-Suite-obfuscated.jar")

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 17
        options.compilerArgs.add("-g:none")
    }

    val nativeLibDir = rootProject.layout.projectDirectory.dir("src/main/resources/native")
    val skipNativeCheck = project.findProperty("skipNativeCheck") == "true"

    processResources {
        dependsOn(protectYamlResources)
        doFirst {
            // jar 跨平台分发，只要包含任一平台的 native 库即可放行构建
            val expectedLibs = listOf("axs-native.dll", "libaxs-native.so", "libaxs-native.dylib")
            val found = expectedLibs.map { nativeLibDir.file(it).asFile }.filter { it.exists() }
            val isCi = System.getenv("GITHUB_ACTIONS") != null
            if (found.isEmpty()) {
                val msg = """
                    Native 安全库缺失: ${nativeLibDir.asFile.absolutePath} 下未找到任何平台原生库。
                    该库是云端模块解密（JNI）的必需组件，CI 构建时必须存在。
                    本地开发可跳过（不影响 ProGuard/字符串加密测试）。
                    如需本地构建 Native 库（CMake + OpenSSL + C++）:
                      cd native
                      cmake -B build
                      cmake --build build --config Release
                    """.trimIndent()
                if (isCi && !skipNativeCheck) {
                    throw GradleException(msg)
                } else {
                    logger.warn("[AXS-Protect] $msg")
                }
            }
        }
        exclude { details ->
            val path = details.relativePath.pathString
            (path.endsWith(".yml") || path.endsWith(".yaml")) && path != "plugin.yml" && path != "config.yml"
        }
        inputs.property("version", version)
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName.set("ArcartX-Suite")
        archiveClassifier.set("plain")
    }

    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("ArcartX-Suite")
        archiveClassifier.set("unobf")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        mergeServiceFiles()
    }

    test {
        useJUnitPlatform()
    }
}

// ═══════════════════════════════════════════════════════════════════
// 4 步保护流水线
//
//   shadowJar (未混淆)
//       ↓
//   Step 1:   ProGuard 混淆 ─── 擦除名称、重打包到 internal
//       ↓
//   Step 2:   字符串加密 ───── ASM XOR 加密所有 LDC String
//       ↓
//   Step 3:   完整性嵌入 ───── SHA-256 摘要写入 META-INF
//       ↓
//   publishCoreJar ────────── 输出到 build/libs/
//
// (Native 下沉是编译期独立步骤，native/ 目录 CMake 构建后
//  产物放入 src/main/resources/native/，随 shadowJar 一起打入)
// ═══════════════════════════════════════════════════════════════════

// Step 1: ProGuard
val step1Jar = layout.buildDirectory.file("libs/ArcartX-Suite-step1-obfuscated.jar")

val obfuscateCore by tasks.registering(ObfuscateJarTask::class) {
    dependsOn(tasks.named("shadowJar"))
    inputJar.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    outputJar.set(step1Jar)
    coreJar.set(true)
    libraryJars.from(
        configurations.named("compileClasspath"),
        project(":axs-api").tasks.named("jar").map { (it as Jar).archiveFile }
    )
    configFiles.from(rootProject.file("proguard/axs-core.pro"))
}

// Step 2: 字符串加密（ASM，ProGuard 之后）
val step2Jar = layout.buildDirectory.file("libs/ArcartX-Suite-step2-strenc.jar")

val stringEncryptCore by tasks.registering(StringEncryptTask::class) {
    dependsOn(obfuscateCore)
    inputJar.set(step1Jar)
    outputJar.set(step2Jar)
}

// Step 3: 完整性嵌入（SHA-256 摘要校验）
val step3Jar = layout.buildDirectory.file("libs/ArcartX-Suite-step3-integrity.jar")

val embedIntegrityCore by tasks.registering(EmbedIntegrityTask::class) {
    dependsOn(stringEncryptCore)
    inputJar.set(step2Jar)
    outputJar.set(step3Jar)
}

// 最终发布
val publishCoreJar by tasks.registering {
    dependsOn(embedIntegrityCore)
    val src = step3Jar.get().asFile
    val dst = layout.buildDirectory.file("libs/ArcartX-Suite-${version}.jar").get().asFile
    inputs.file(src)
    outputs.file(dst)
    doLast {
        dst.parentFile.mkdirs()
        src.copyTo(dst, overwrite = true)
    }
}

tasks.build {
    dependsOn(publishCoreJar)
}
