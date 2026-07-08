import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

plugins {
    id("base")
    id("com.gradleup.shadow") version "8.3.5" apply false
}

val distDir = rootProject.layout.buildDirectory.dir("ArcartX-Suite")

// 混淆后的 core jar 路径（模块编译时依赖此产物，使得 bridge 等类被完全混淆后仍可链接）
val obfuscatedCoreJar: Provider<RegularFile> = project(":axs-core").layout.buildDirectory.file("libs/ArcartX-Suite-step1-obfuscated.jar")

subprojects {
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.md-5.net/repository/public/")
    }

    afterEvaluate {
        // 模块子项目：注册混淆任务 + 字符串加密任务
        if (project.path.startsWith(":modules:")) {

            // 统一移除调试信息
            tasks.withType<JavaCompile> {
                options.compilerArgs.add("-g:none")
            }

            val moduleJarTask = tasks.named<Jar>("jar")

            // 原始 jar 输出到 build/libs（中间产物）
            moduleJarTask.configure {
                destinationDirectory.set(layout.buildDirectory.dir("libs"))
            }

            // 混淆后的 jar
            // 注意：模块编译时仍依赖未混淆的 :axs-core（compileOnly），
            //       ProGuard 混淆时才以混淆后的 core 为 library jar，
            //       这样 ProGuard 会自动把模块中的原始类名引用替换为混淆后的名称。
            val obfuscatedModuleJar = distDir.map { it.file("modules-obf/${moduleJarTask.get().archiveFileName.get()}") }
            val obfuscateModule = tasks.register<ObfuscateJarTask>("obfuscateModule") {
                dependsOn(moduleJarTask)
                dependsOn(":axs-core:obfuscateCore")
                inputJar.set(moduleJarTask.flatMap { it.archiveFile })
                outputJar.set(obfuscatedModuleJar)
                coreJar.set(false)
                // library jars：混淆后的 core（用于解析引用并自动重命名）+ 其他依赖
                libraryJars.from(files(obfuscatedCoreJar))
                libraryJars.from(configurations.named("compileClasspath").get().files.filter {
                    !it.absolutePath.contains("axs-core")
                })
                configFiles.from(rootProject.file("proguard/modules.pro"))
            }

            // 字符串加密（ProGuard 之后），直接输出到最终 modules/ 目录
            val stringEncryptedModuleJar = distDir.map { it.file("modules/${moduleJarTask.get().archiveFileName.get()}") }
            val stringEncryptModule = tasks.register<StringEncryptTask>("stringEncryptModule") {
                dependsOn(obfuscateModule)
                inputJar.set(obfuscateModule.flatMap { it.outputJar })
                outputJar.set(stringEncryptedModuleJar)
            }

            tasks.named("build") {
                dependsOn(stringEncryptModule)
            }

            // ─── Option 2：逐类字节码加密（opt-in，-Paxs.protectModules）─────────
            // 对字符串加密后的模块 Jar 再做逐类 AES-GCM/ChaCha20 加密（ENCRYPTED/<path>.enc），
            // 运行时由升级后的 ByteArrayModuleClassLoader 调 native n6 逐类解密。
            // 默认关闭：未传 -Paxs.protectModules 时 .axb 仍封装明文类 Jar（与现有发布流程一致）。
            val protectModules = project.hasProperty("axs.protectModules")
            // 配置期捕获模块名，避免在执行期（doLast / 懒求值 lambda）调用 Task.project
            // ——会触发 Gradle 配置缓存报错（config_cache:requirements:use_project_during_execution）。
            val moduleName = project.name
            val encryptedClassesModuleJar = distDir.map { it.file("modules-enc/${moduleJarTask.get().archiveFileName.get()}") }
            val scriptsDir = rootProject.file("scripts")
            val nativeBuildDir = rootProject.file("native/build")

            // 方案 B：每模块一个 32 字节 moduleKey，同时作为
            //   (1) 逐类加密种子（encrypt-jar.py --module-seed-b64）
            //   (2) 外层 .axb 的 AES-256-GCM 密钥（EncryptModuleAxbTask）
            // 于是模块逐类加密与本体 root_seed 完全解耦、自包含；moduleKey 随 .axb 上传云端，
            // 运行期指纹绑定下发，ByteArrayModuleClassLoader 用它调 native n11 解密。
            val moduleKeyFileProvider = distDir.map { it.file("modulekeys/${moduleName}.key") }
            val generateModuleKey = tasks.register("generateModuleKey") {
                group = "protection"
                description = "生成/复用该模块的 32 字节 moduleKey（逐类种子 = 外层 .axb AES key）"
                val outFile = moduleKeyFileProvider
                outputs.file(outFile)
                doLast {
                    val f = outFile.get().asFile
                    if (!f.exists() || f.readText().trim().isEmpty()) {
                        f.parentFile.mkdirs()
                        val k = ByteArray(32)
                        java.security.SecureRandom().nextBytes(k)
                        f.writeText(java.util.Base64.getEncoder().encodeToString(k))
                        logger.lifecycle("[generateModuleKey] ${moduleName} 新建 moduleKey")
                    } else {
                        logger.lifecycle("[generateModuleKey] ${moduleName} 复用已有 moduleKey")
                    }
                }
            }

            val encryptModuleClasses = tasks.register<Exec>("encryptModuleClasses") {
                group = "protection"
                description = "对模块 Jar 做逐类字节码加密（Option 2，供云端下发）"
                dependsOn(stringEncryptModule, generateModuleKey)
                inputs.file(stringEncryptedModuleJar)
                inputs.file(moduleKeyFileProvider)
                outputs.file(encryptedClassesModuleJar)
                val sIn = stringEncryptedModuleJar
                val sOut = encryptedClassesModuleJar
                val nbd = nativeBuildDir
                val sdir = scriptsDir
                val keyFp = moduleKeyFileProvider
                // 种子在执行期才可读（generateModuleKey 产出），故在 doFirst 里设置 commandLine。
                doFirst {
                    val seedB64 = keyFp.get().asFile.readText().trim()
                    commandLine(
                        "python", "${sdir}/encrypt-jar.py",
                        "--input", sIn.get().asFile.absolutePath,
                        "--output", sOut.get().asFile.absolutePath,
                        "--native-dir", nbd.absolutePath,
                        "--module-seed-b64", seedB64,        // 方案 B：用模块自带 moduleKey 作种子，与本体 root_seed 解耦
                        "--protection-level", "high",
                        "--no-native",                         // 模块不嵌 native
                        "--bootstrap-classes", "",             // 模块无引导类（入口由 -keep 保持明文）
                        "--encrypt-prefixes", "",              // default-package 模式忽略前缀
                        "--keep-prefixes", "xuanmo.arcartxsuite.api.,xuanmo.arcartxsuite.auth.",
                        "--encrypt-mode", "default-package",   // 仅加密 -repackageclasses '' 扁平化后的模块自身代码
                        "--fake-classes-count", "5"
                    )
                }
            }

            // 将模块 Jar 加密为 .axb，供上传到 AXS Cloud Platform。
            // 开启 Option 2 时封装逐类加密 Jar；否则封装明文类 Jar（默认）。
            val axbSourceJar = if (protectModules) encryptedClassesModuleJar else stringEncryptedModuleJar
            tasks.register<EncryptModuleAxbTask>("encryptModuleAxb") {
                group = "protection"
                description = "将模块 Jar 加密为 .axb（上传云端用）"
                dependsOn(if (protectModules) encryptModuleClasses else stringEncryptModule)
                inputJar.set(axbSourceJar.map { it } as Provider<RegularFile>)
                outputAxb.set(distDir.map { it.file("module-axb/${moduleName}.axb") })
                val keyProp = project.findProperty("module.${moduleName}.key") as String?
                val ivProp = project.findProperty("module.${moduleName}.iv") as String?
                if (keyProp != null) moduleKey.set(keyProp)
                if (ivProp != null) moduleIv.set(ivProp)
                // 方案 B：开启逐类加密时，外层 .axb 复用与逐类加密同一份 moduleKey（取代随机/-P）。
                if (protectModules) {
                    dependsOn(generateModuleKey)
                    moduleKeyFile.set(moduleKeyFileProvider)
                }
            }
        }
    }
}

tasks.register("buildAll") {
    group = "build"
    description = "Build all subprojects (with obfuscation)"
    dependsOn(":axs-core:build")
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") || it.path.startsWith(":proxy:") }.map { "${it.path}:build" })
}

tasks.register("buildModules") {
    group = "build"
    description = "Build all module JARs (with obfuscation)"
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") }.map { "${it.path}:obfuscateModule" })
}

tasks.register("encryptAllModuleAxb") {
    group = "protection"
    description = "将所有模块加密为 .axb（输出到 build/ArcartX-Suite/module-axb/，上传云端用）"
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") }.map { "${it.path}:encryptModuleAxb" })
}

// ─── JAR 加密保护构建任务 ─────────────────────────────────────────
apply(from = "gradle/encryption-tasks.gradle.kts")

// 扩展 clean 任务，确保自定义分发目录 build/ArcartX-Suite/ 也被清理
tasks.named<Delete>("clean") {
    delete(distDir)
}

// 快速构建（跳过混淆，仅开发测试用）
tasks.register("buildDev") {
    group = "build"
    description = "Build all JARs WITHOUT obfuscation (dev only)"
    dependsOn(":axs-core:shadowJar")
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") }.map { "${it.path}:jar" })
    // 仅对应用了 shadow 插件的代理子项目（velocity/bungee）依赖 shadowJar；
    // proxy:common 是纯 java 库、无 shadowJar 任务，其 jar 会随 velocity/bungee 的 shadowJar 传递构建。
    dependsOn(
        subprojects
            .filter { it.path.startsWith(":proxy:") && it.plugins.hasPlugin("com.gradleup.shadow") }
            .map { "${it.path}:shadowJar" }
    )
}
