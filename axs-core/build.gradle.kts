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

val obfuscatedJar = layout.buildDirectory.file("libs/ArcartXSuite-obfuscated.jar")

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 17
        options.compilerArgs.add("-g:none")
    }

    val nativeLibDir = rootProject.layout.projectDirectory.dir("src/main/resources/native")

    processResources {
        dependsOn(protectYamlResources)
        doFirst {
            // jar 跨平台分发，只要包含任一平台的 native 库即可放行构建
            val expectedLibs = listOf("axs-native.dll", "libaxs-native.so", "libaxs-native.dylib")
            val found = expectedLibs.map { nativeLibDir.file(it).asFile }.filter { it.exists() }
            if (found.isEmpty()) {
                throw GradleException(
                    """
                    Native 安全库缺失: ${nativeLibDir.asFile.absolutePath} 下未找到任何平台原生库。
                    该库是云端模块解密（JNI）的必需组件，必须先构建后再打包 jar。
                    本地构建步骤（CMake + OpenSSL + C++ 编译器）:
                      cd native
                      cmake -B build
                      cmake --build build --config Release
                    构建产物会自动输出到 src/main/resources/native/，随后重新执行 gradle build。
                    """.trimIndent()
                )
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
        archiveBaseName.set("ArcartXSuite")
        archiveClassifier.set("plain")
    }

    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("ArcartXSuite")
        archiveClassifier.set("unobf")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        mergeServiceFiles()
    }

    test {
        useJUnitPlatform()
    }
}

// ═══════════════════════════════════════════════════════════════════
// 5 步保护流水线
//
//   shadowJar (未混淆)
//       ↓
//   Step 1:   ProGuard 混淆 ─── 擦除名称、重打包到 internal
//       ↓
//   Step 1.5: 字符串加密 ───── ASM XOR 加密所有 LDC String
//       ↓
//   Step 3:   ClassFinal VMP ── 抽空方法体加密（可选）
//       ↓
//   Step 4:   完整性嵌入 ───── SHA-256 摘要写入 META-INF
//       ↓
//   publishCoreJar ────────── 输出到 build/libs/
//
// (Step 2 Native 下沉 是编译期独立步骤，native/ 目录 CMake 构建后
//  产物放入 src/main/resources/native/，随 shadowJar 一起打入)
// ═══════════════════════════════════════════════════════════════════

// Step 1: ProGuard
val step1Jar = layout.buildDirectory.file("libs/ArcartXSuite-step1-obfuscated.jar")

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

// Step 1.5: 字符串加密（ASM，ProGuard 之后）
val step15Jar = layout.buildDirectory.file("libs/ArcartXSuite-step15-strenc.jar")

val stringEncryptCore by tasks.registering(StringEncryptTask::class) {
    dependsOn(obfuscateCore)
    inputJar.set(step1Jar)
    outputJar.set(step15Jar)
}

// Step 3: ClassFinal VMP（仅当 tools/classfinal/ 存在时执行）
val step3Jar = layout.buildDirectory.file("libs/ArcartXSuite-step3-vmp.jar")
val classFinalAvailable = rootProject.file("tools/classfinal/classfinal-fatjar.jar").isFile

val classFinalCore by tasks.registering(ClassFinalTask::class) {
    dependsOn(stringEncryptCore)
    inputJar.set(step15Jar)
    outputJar.set(step3Jar)
    packages.set(listOf(
        "xuanmo.arcartxsuite.internal"
    ))
    enabled = classFinalAvailable
}

// 选择 Step 3 的输出（如果 ClassFinal 可用），否则直接用 Step 1.5 的
val publishSrcJar = if (classFinalAvailable) step3Jar else step15Jar
val publishSrcTask = if (classFinalAvailable) "classFinalCore" else "stringEncryptCore"

// 最终发布
val publishCoreJar by tasks.registering {
    dependsOn(tasks.named(publishSrcTask))
    val src = publishSrcJar.get().asFile
    val dst = layout.buildDirectory.file("libs/ArcartXSuite-${version}.jar").get().asFile
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
