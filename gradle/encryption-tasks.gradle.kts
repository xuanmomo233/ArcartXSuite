// ═══════════════════════════════════════════════════════════════════
// ArcartX-Suite - JAR 加密构建任务
// 在 ProGuard 混淆之后、最终打包之前执行
// ═══════════════════════════════════════════════════════════════════

// 用法：在根 build.gradle.kts 中 apply(from = "gradle/encryption-tasks.gradle.kts")

val scriptsDir = rootProject.file("scripts")
val keysDir = rootProject.file("keys")
val nativeBuildDir = rootProject.file("native/build")

// ─── 密钥生成（首次构建或密钥轮换时手动执行） ──────────────────
tasks.register<Exec>("generateKeys") {
    group = "protection"
    description = "生成 Ed25519 签名密钥 + AES root seed"
    commandLine("python", "${scriptsDir}/generate-keys.py", "--output", keysDir.absolutePath)
    doFirst {
        println("[Protection] Generating encryption keys...")
    }
}

// ─── JAR 加密（在 ProGuard 之后执行） ────────────────────────────
// 此任务加密 axs-core 的混淆产物
tasks.register<Exec>("encryptCoreJar") {
    group = "protection"
    description = "加密混淆后的 core JAR（AES-256-GCM/ChaCha20 + native 嵌入）"

    val obfuscatedJar = project(":axs-core").layout.buildDirectory.file("libs/ArcartX-Suite-step1-obfuscated.jar")
    val encryptedJar = project(":axs-core").layout.buildDirectory.file("libs/ArcartX-Suite-encrypted.jar")

    // 依赖 ProGuard 混淆完成
    dependsOn(":axs-core:obfuscateCore")

    // 依赖 native 编译完成
    dependsOn("compileNative")

    inputs.file(obfuscatedJar)
    outputs.file(encryptedJar)

    commandLine(
        "python", "${scriptsDir}/encrypt-jar.py",
        "--input", obfuscatedJar.get().asFile.absolutePath,
        "--output", encryptedJar.get().asFile.absolutePath,
        "--native-dir", nativeBuildDir.absolutePath,
        "--keys-dir", keysDir.absolutePath,
        "--protection-level", "high",
        "--bootstrap-classes", "NativeBridge,ProtectedClassLoader,ProtectionInit,JvmAntiDebug",
        "--fake-classes-count", "20"
    )

    doFirst {
        println("[Protection] Encrypting JAR: ${obfuscatedJar.get().asFile.name}")
    }
}

// ─── 加密模块 JAR ─────────────────────────────────────────────────
// 每个模块子项目的混淆产物也需要加密
subprojects {
    if (project.path.startsWith(":modules:")) {
        afterEvaluate {
            val obfModule = tasks.findByName("obfuscateModule") ?: return@afterEvaluate

            tasks.register<Exec>("encryptModule") {
                group = "protection"
                description = "加密模块 JAR"
                dependsOn(obfModule)

                val moduleJarTask = tasks.named<Jar>("jar").get()
                val obfJar = rootProject.layout.buildDirectory.file(
                    "ArcartX-Suite/modules-obf/${moduleJarTask.archiveFileName.get()}"
                )
                val encJar = rootProject.layout.buildDirectory.file(
                    "ArcartX-Suite/modules/${moduleJarTask.archiveFileName.get()}"
                )

                commandLine(
                    "python", "${scriptsDir}/encrypt-jar.py",
                    "--input", obfJar.get().asFile.absolutePath,
                    "--output", encJar.get().asFile.absolutePath,
                    "--native-dir", nativeBuildDir.absolutePath,
                    "--keys-dir", keysDir.absolutePath,
                    "--protection-level", "high",
                    "--no-native",  // 模块不嵌入 native（由 core 提供）
                    "--fake-classes-count", "5"
                )
            }
        }
    }
}

// ─── Native 编译 ──────────────────────────────────────────────────
tasks.register<Exec>("compileNative") {
    group = "protection"
    description = "编译 native 保护库（Windows + Linux）"
    workingDir(rootProject.file("native"))
    commandLine("cmake", "--build", "build", "--config", "Release")
    doFirst {
        // 确保 build 目录存在
        val buildDir = rootProject.file("native/build")
        if (!buildDir.exists()) {
            exec {
                workingDir(rootProject.file("native"))
                commandLine("cmake", "-B", "build", "-DCMAKE_BUILD_TYPE=Release")
            }
        }
    }
}

// ─── 验证保护 ─────────────────────────────────────────────────────
tasks.register<Exec>("verifyProtection") {
    group = "protection"
    description = "验证加密 JAR 的保护完整性和强度评分"
    dependsOn("encryptCoreJar")

    val encryptedJar = project(":axs-core").layout.buildDirectory.file("libs/ArcartX-Suite-encrypted.jar")

    commandLine(
        "python", "${scriptsDir}/verify-protection.py",
        "--input", encryptedJar.get().asFile.absolutePath,
        "--keys-dir", keysDir.absolutePath,
        "--verbose"
    )
}
