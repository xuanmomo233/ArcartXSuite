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

    processResources {
        dependsOn(protectYamlResources)
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
// 4 步保护流水线
//
//   shadowJar (未混淆)
//       ↓
//   Step 1: ProGuard 混淆 ─── 擦除名称、死代码剔除
//       ↓
//   Step 3: ClassFinal VMP ── 抽空方法体加密（可选，需 tools/classfinal/）
//       ↓
//   Step 4: 完整性嵌入 ───── SHA-256 摘要写入 META-INF
//       ↓
//   publishCoreJar ────────── 输出到 build/dist/
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

// Step 3: ClassFinal VMP（仅当 tools/classfinal/ 存在时执行）
val step3Jar = layout.buildDirectory.file("libs/ArcartXSuite-step3-vmp.jar")
val classFinalAvailable = rootProject.file("tools/classfinal/classfinal-fatjar.jar").isFile

val classFinalCore by tasks.registering(ClassFinalTask::class) {
    dependsOn(obfuscateCore)
    inputJar.set(step1Jar)
    outputJar.set(step3Jar)
    packages.set(listOf(
        "xuanmo.arcartxsuite.security",
        "xuanmo.arcartxsuite.config"
    ))
    enabled = classFinalAvailable
}

// 选择 Step 3 的输出（如果 ClassFinal 可用），否则直接用 Step 1 的
val publishSrcJar = if (classFinalAvailable) step3Jar else step1Jar
val publishSrcTask = if (classFinalAvailable) "classFinalCore" else "obfuscateCore"

// 最终发布 — 使用自定义 task 避免 Copy lambda 捕获脚本上下文（配置缓存兼容）
val publishCoreJar by tasks.registering {
    dependsOn(tasks.named(publishSrcTask))
    val src = publishSrcJar.get().asFile
    val dst = rootDir.resolve("build/ArcartXSuite/ArcartXSuite-${version}.jar")
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
