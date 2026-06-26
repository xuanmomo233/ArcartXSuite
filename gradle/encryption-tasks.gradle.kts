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

    // generate-keys 写入 keys/<version>/，并在 keys/current_version.txt 记录当前版本；
    // encrypt-jar 直接读 <keys-dir>/root_seed.bin，因此必须解析到带版本号的子目录。
    val currentMarker = keysDir.resolve("current_version.txt")
    val resolvedKeysDir = if (currentMarker.exists())
        keysDir.resolve(currentMarker.readText().trim()).absolutePath
    else
        keysDir.absolutePath

    commandLine(
        "python", "${scriptsDir}/encrypt-jar.py",
        "--input", obfuscatedJar.get().asFile.absolutePath,
        "--output", encryptedJar.get().asFile.absolutePath,
        "--native-dir", nativeBuildDir.absolutePath,
        "--keys-dir", resolvedKeysDir,
        "--protection-level", "high",
        // 引导类必须用全限定名（含主类）：encrypt-jar 据此保持明文，ProtectionInit 据此捕获复用
        "--bootstrap-classes",
        "xuanmo.arcartxsuite.ArcartXSuitePlugin," +
            "xuanmo.arcartxsuite.security.NativeBridge," +
            "xuanmo.arcartxsuite.security.protection.ProtectionInit," +
            "xuanmo.arcartxsuite.security.protection.ProtectedClassLoader," +
            "xuanmo.arcartxsuite.security.protection.JvmAntiDebug",
        // 仅加密插件自身代码；shaded 第三方库保持明文，api/auth 排除（模块依赖 / 独立进程入口）
        "--encrypt-prefixes", "xuanmo.arcartxsuite.",
        "--keep-prefixes", "xuanmo.arcartxsuite.api.,xuanmo.arcartxsuite.auth.",
        "--fake-classes-count", "20"
    )

    doFirst {
        println("[Protection] Encrypting JAR: ${obfuscatedJar.get().asFile.name}")
    }
}

// ─── 加密模块 JAR（逐类，Option 2） ──────────────────────────────────
// 模块逐类加密任务 encryptModuleClasses + .axb 封装的 opt-in 切换在
// 根 build.gradle.kts 的模块 afterEvaluate 块中定义（-Paxs.protectModules），
// 因其需与 stringEncryptModule / encryptModuleAxb 共享 Provider，故不在此重复注册。
//
// 一次性加密全部模块（逐类）：
tasks.register("encryptAllModuleClasses") {
    group = "protection"
    description = "对所有模块做逐类字节码加密（Option 2，需 -Paxs.protectModules + 已生成密钥）"
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") }.map { "${it.path}:encryptModuleClasses" })
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
