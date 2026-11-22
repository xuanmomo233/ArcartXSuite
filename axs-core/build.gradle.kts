import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/releases/")
}

// Compile against upstream APIs, but ship only the runtime components used by Suite.
val runtimeTarget = RuntimeTarget.parse(providers.gradleProperty("axs.runtimeTarget").orElse("universal").get())
val packagingSqlite by configurations.creating { isTransitive = false }
val packagingCrypto by configurations.creating { isTransitive = false }

val compactSqliteJar by tasks.registering(Jar::class) {
    archiveFileName.set("sqlite-jdbc-${runtimeTarget.id}.jar")
    destinationDirectory.set(layout.buildDirectory.dir("packaging"))
    from({ zipTree(packagingSqlite.singleFile) })
    exclude(ExcludeOtherRuntimeAssets(runtimeTarget))
    exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val generateRuntimeTarget by tasks.registering(GenerateRuntimeTargetTask::class) {
    targetId.set(runtimeTarget.id)
    outputFile.set(layout.buildDirectory.file("generated/runtime/axs-runtime.properties"))
}

val compactCryptoJar by tasks.registering(CompactCryptoJarTask::class) {
    inputJar.set(layout.file(providers.provider { packagingCrypto.singleFile }))
    consumerClasses.from(layout.buildDirectory.dir("classes/java/main"),
        project(":auth-engine").layout.buildDirectory.dir("classes/java/main"))
    dependsOn("compileJava", ":auth-engine:compileJava")
    outputJar.set(layout.buildDirectory.file("packaging/bcprov-suite.jar"))
}

configurations.matching { it.name == "runtimeClasspath" || it.name == "testRuntimeClasspath" }.configureEach {
    exclude(group = "org.xerial", module = "sqlite-jdbc")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
}

dependencies {
    packagingSqlite("org.xerial:sqlite-jdbc:3.46.0.0")
    packagingCrypto("org.bouncycastle:bcprov-jdk18on:1.86")
    runtimeOnly(files(compactSqliteJar.flatMap { it.archiveFile }))
    runtimeOnly(files(compactCryptoJar.flatMap { it.outputJar }))
    implementation(project(":axs-api"))
    implementation(project(":auth-minecraft"))
    implementation(project(":auth-engine"))
    implementation(project(":auth-protocol"))
    implementation(project(":axs-placeholder"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly(files("../libs/Chemdah-1.1.33-FREE.jar"))
    compileOnly(files("../libs/MythicLib-dist-1.7.1-49.jar"))
    compileOnly(files("../libs/Mythic-Dist-5.8.0.jar"))
    compileOnly(files("../libs/NeigeItems-1.21.160-api.jar"))
    compileOnly(files("../libs/Overture-1.0.3.jar"))
    compileOnly(files("../libs/MMOItems-6.9.5-SNAPSHOT.jar"))
    compileOnly(files("../libs/symphony-bukkit-1.0.6.jar"))
    compileOnly(files("../libs/Adyeshach-2.1.30.jar"))
    compileOnly(files("../libs/ArcartX-2.7.92.jar"))
    compileOnly(files("../libs/AttributePlus-3.3.3.5.jar"))
    compileOnly(files("../libs/CraneAttribute-0.1.0-dev.jar"))
    compileOnly(files("../libs/Rondo-1.1.6-SNAPSHOT.jar"))
    compileOnly(files("../libs/SystemMail-1.0.0-obfuscated.jar"))
    compileOnly("me.clip:placeholderapi:2.11.7")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.mysql:mysql-connector-j:8.4.0") {
        // Suite uses JDBC; protobuf is only needed by Connector/J's X DevAPI.
        exclude(group = "com.google.protobuf", module = "protobuf-java")
    }
    implementation("org.tukaani:xz:1.12")
    constraints {
        implementation("com.google.protobuf:protobuf-java:3.25.5") {
            because("MySQL Connector/J brings a version affected by CVE-2024-7254")
        }
    }
    implementation("redis.clients:jedis:5.2.0")
    implementation("com.belerweb:pinyin4j:2.5.1")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation(files("../libs/MythicLib-dist-1.7.1-49.jar"))
    testImplementation(files("../libs/symphony-bukkit-1.0.6.jar"))
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    testImplementation("me.clip:placeholderapi:2.11.7")
}

// modern 源集的依赖配置
// modernCompileOnly：声明依赖（不可解析，类似 compileOnly）
// modernCompileClasspath：可解析，供 compileModernJava 使用
val modernCompileOnly by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
}
val modernCompileClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    extendsFrom(modernCompileOnly)
}

dependencies {
    // modern 源集：用 1.21 spigot-api 编译跨版本兼容代码（如 PotionAccessorModern）
    "modernCompileOnly"("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    "modernCompileOnly"("org.jetbrains:annotations:24.1.0")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("../src/main/java"))
        resources {
            setSrcDirs(listOf("../src/main/resources"))
        }
    }
    // modern 源集：跨版本兼容代码，用 1.21 API 编译
    // 编译产物合并进最终 jar，运行时按版本反射加载
    val modern by creating {
        java.setSrcDirs(listOf("../src/modern/java"))
        compileClasspath += sourceSets.main.get().output
        compileClasspath += modernCompileClasspath
        runtimeClasspath += sourceSets.main.get().runtimeClasspath
    }
}

val obfuscatedJar = layout.buildDirectory.file("libs/ArcartX-Suite-obfuscated.jar")

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release = 17
        options.compilerArgs.add("-g:none")
    }

    named<JavaCompile>("compileModernJava") {
        options.encoding = "UTF-8"
        options.release = 17
        options.compilerArgs.add("-g:none")
    }

    val nativeLibDir = rootProject.layout.projectDirectory.dir("src/main/resources/native")
    val skipNativeCheck = project.findProperty("skipNativeCheck") == "true"
    val expectedNativeFiles = runtimeTarget.nativeFiles

    processResources {
        exclude(ExcludeOtherRuntimeAssets(runtimeTarget))
        from(generateRuntimeTarget.flatMap { it.outputFile }) { into("META-INF") }
        doFirst {
            // jar 跨平台分发，只要包含任一平台的 native 库即可放行构建
            val expectedLibs = expectedNativeFiles
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
        inputs.property("version", version)
        inputs.property("runtimeTarget", runtimeTarget.id)
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        archiveBaseName.set("ArcartX-Suite")
        archiveClassifier.set("plain${runtimeTarget.fileSuffix}")
        // 合并 modern 源集编译产物（跨版本兼容类）
        from(sourceSets.named("modern").get().output)
    }

    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("ArcartX-Suite")
        archiveClassifier.set("all-unobf${runtimeTarget.fileSuffix}")
        destinationDirectory.set(layout.buildDirectory.dir("packaging"))
        mergeServiceFiles()
        // 合并 modern 源集编译产物
        from(sourceSets.named("modern").get().output)
    }

    test {
        useJUnitPlatform()
        systemProperty("java.io.tmpdir", temporaryDir.absolutePath)
        jvmArgumentProviders.add(objects.newInstance(PackagingReferenceArguments::class.java).apply {
            sqliteJar.set(layout.file(providers.provider { packagingSqlite.singleFile }))
            cryptoJar.set(layout.file(providers.provider { packagingCrypto.singleFile }))
        })
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
val thinCoreJar by tasks.registering(SplitRuntimeTask::class) {
    inputJar.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    outputJar.set(layout.buildDirectory.file("libs/ArcartX-Suite-${version}-unobf${runtimeTarget.fileSuffix}.jar"))
    libraries.set(layout.buildDirectory.dir("runtime-libraries"))
}

tasks.test {
    jvmArgumentProviders.add(objects.newInstance(RuntimeLibraryTestArguments::class.java).apply {
        jar.set(thinCoreJar.flatMap { it.outputJar })
        libraries.set(thinCoreJar.flatMap { it.libraries })
    })
}

val step1Jar = layout.buildDirectory.file("libs/ArcartX-Suite-step1-obfuscated.jar")

val obfuscateCore by tasks.registering(ObfuscateJarTask::class) {
    inputJar.set(thinCoreJar.flatMap { it.outputJar })
    outputJar.set(step1Jar)
    coreJar.set(true)
    libraryJars.from(
        configurations.named("compileClasspath"),
        // modern 源集用 1.21 spigot-api 编译，ProGuard 需要解析其方法引用
        modernCompileClasspath,
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
    val dst = layout.buildDirectory.file("libs/ArcartX-Suite-${version}${runtimeTarget.fileSuffix}.jar").get().asFile
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
