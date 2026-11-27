import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("base")
    id("com.gradleup.shadow") version "8.3.5" apply false
}

// 宿主、公开 API、认证组件、模块与代理端使用同一套发布版本。
val runtimeTarget = RuntimeTarget.parse(providers.gradleProperty("axs.runtimeTarget").orElse("universal").get())
val suiteEdition = providers.gradleProperty("axs.edition").orElse("customer").get()
require(suiteEdition in setOf("free", "customer")) { "axs.edition must be free or customer" }
allprojects {
    version = rootProject.version
    // Profiles can coexist without overwriting another target's generated resources or JARs.
    if (runtimeTarget != RuntimeTarget.UNIVERSAL) {
        layout.buildDirectory.set(layout.projectDirectory.dir("build/runtime/${runtimeTarget.id}"))
    }
}

val distDir = rootProject.layout.buildDirectory.dir("ArcartX-Suite")

// 混淆后的 core jar 路径（模块编译时依赖此产物，使得 bridge 等类被完全混淆后仍可链接）
val obfuscatedCoreJar: Provider<RegularFile> = project(":axs-core").layout.buildDirectory.file("libs/ArcartX-Suite-step1-obfuscated.jar")

subprojects {
    plugins.withId("java") {
        val module = path.startsWith(":modules:")
        val host = path == ":axs-core"
        if (module || host || path == ":axs-api") {
            extensions.configure<SourceSetContainer> {
                if (!host) {
                    named("main") {
                        java.srcDir("src/$suiteEdition/java")
                        resources.srcDir("src/$suiteEdition/resources")
                    }
                }
                named("test") {
                    java.srcDir("src/${suiteEdition}Test/java")
                }
            }
        }
        if (module || host) {
            val editionFile = layout.buildDirectory.file("generated/suite-edition/axs-edition.txt")
            val generateSuiteEdition = tasks.register("generateSuiteEdition") {
                inputs.property("edition", suiteEdition)
                outputs.file(editionFile)
                doLast {
                    val output = editionFile.get().asFile
                    output.parentFile.mkdirs()
                    output.writeText("$suiteEdition\n")
                }
            }
            tasks.named<ProcessResources>("processResources") {
                dependsOn(generateSuiteEdition)
                from(editionFile) { into("META-INF") }
            }
        }
    }

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.md-5.net/repository/public/")
        maven("https://jitpack.io")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://repo.rosewooddev.io/repository/public/")
    }

    afterEvaluate {
        // 模块子项目：注册混淆任务 + 字符串加密任务
        // debug 模块仅本地调试用，跳过混淆/加密/签名等保护流程。
        if (project.path.startsWith(":modules:") && project.path != ":modules:debug") {

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

            // Some modules must retain their package for package-private access after ProGuard.
            val namedModuleEntry = (findProperty("axs.namedModuleEntry") as? String).orEmpty()
            val encryptModuleClasses = tasks.register<Exec>("encryptModuleClasses") {
                group = "protection"
                description = "对模块 Jar 做逐类字节码加密（Option 2，供云端下发）"
                dependsOn(stringEncryptModule, generateModuleKey)
                inputs.file(stringEncryptedModuleJar)
                inputs.file(moduleKeyFileProvider)
                inputs.file(scriptsDir.resolve("encrypt-jar.py"))
                inputs.property("namedModuleEntry", namedModuleEntry)
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
                        "--bootstrap-classes", namedModuleEntry,
                        "--encrypt-prefixes", if (namedModuleEntry.isEmpty()) "" else namedModuleEntry.substringBeforeLast('.') + ".",
                        "--keep-prefixes", "xuanmo.arcartxsuite.api.,xuanmo.arcartxsuite.auth.",
                        "--encrypt-mode", if (namedModuleEntry.isEmpty()) "default-package" else "prefix",
                        "--fake-classes-count", "5"
                    )
                }
            }

            // 将模块 Jar 加密为 .axb，供上传到 AXS Cloud Platform。
            // 开启 Option 2 时封装逐类加密 Jar；否则封装明文类 Jar（默认）。
            val baseAxbSourceJar = if (protectModules) encryptedClassesModuleJar else stringEncryptedModuleJar
            val watermarkRecipient = providers.gradleProperty("axs.watermark.recipient")
            val watermarkLicense = providers.gradleProperty("axs.watermark.license")
            val watermarkKey = providers.gradleProperty("axs.watermark.privateKey")
            val watermarkEnabled = watermarkRecipient.isPresent || watermarkLicense.isPresent || watermarkKey.isPresent
            require(!watermarkEnabled || (watermarkRecipient.isPresent && watermarkLicense.isPresent && watermarkKey.isPresent)) {
                "Watermark builds require axs.watermark.recipient, axs.watermark.license and axs.watermark.privateKey"
            }
            val watermarkedJar = distDir.map { it.file("modules-watermarked/${moduleJarTask.get().archiveFileName.get()}") }
            val watermarkTask = if (watermarkEnabled) tasks.register<Exec>("watermarkModuleJar") {
                group = "protection"
                description = "逐类加密后写入指定收件人的签名水印，再交由 AXB 封装"
                dependsOn(if (protectModules) encryptModuleClasses else stringEncryptModule)
                inputs.file(baseAxbSourceJar)
                inputs.file(rootProject.file(watermarkKey.get()))
                inputs.file(scriptsDir.resolve("watermark-module.py"))
                inputs.property("recipient", watermarkRecipient.get())
                inputs.property("license", watermarkLicense.get())
                val release = providers.gradleProperty("axs.watermark.release").orElse(rootProject.version.toString()).get()
                inputs.property("release", release)
                outputs.file(watermarkedJar)
                commandLine(
                    providers.gradleProperty("axs.python").orElse("python").get(),
                    scriptsDir.resolve("watermark-module.py").absolutePath, "stamp",
                    "--input", baseAxbSourceJar.get().asFile.absolutePath,
                    "--output", watermarkedJar.get().asFile.absolutePath,
                    "--module-id", moduleName, "--release", release,
                    "--recipient", watermarkRecipient.get(), "--license-id", watermarkLicense.get(),
                    "--private-key", rootProject.file(watermarkKey.get()).absolutePath, "--overwrite"
                )
            } else null
            val axbSourceJar = if (watermarkEnabled) watermarkedJar else baseAxbSourceJar
            tasks.register<EncryptModuleAxbTask>("encryptModuleAxb") {
                group = "protection"
                description = "将模块 Jar 加密为 .axb（上传云端用）"
                dependsOn(if (watermarkEnabled) watermarkTask!! else if (protectModules) encryptModuleClasses else stringEncryptModule)
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

// debug 模块仅本地调试用，不参与 CI 构建、云端下发、批量混淆/加密。
fun isProductionModule(p: Project): Boolean =
    p.path.startsWith(":modules:") && p.path != ":modules:debug"

tasks.register("buildAll") {
    group = "build"
    description = "Build all subprojects (with obfuscation)"
    dependsOn(":axs-core:build")
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") || it.path.startsWith(":proxy:") }.filter { it.path != ":modules:debug" }.map { "${it.path}:build" })
}

tasks.register("buildModules") {
    group = "build"
    description = "Build all module JARs (with obfuscation)"
    dependsOn(subprojects.filter { isProductionModule(it) }.map { "${it.path}:obfuscateModule" })
}

tasks.register("encryptAllModuleAxb") {
    group = "protection"
    description = "将所有模块加密为 .axb（输出到 build/ArcartX-Suite/module-axb/，上传云端用）"
    dependsOn(subprojects.filter { isProductionModule(it) }.map { "${it.path}:encryptModuleAxb" })
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
    dependsOn(":axs-core:thinCoreJar")
    dependsOn(subprojects.filter { isProductionModule(it) }.map { "${it.path}:jar" })
    // 仅对应用了 shadow 插件的代理子项目（velocity/bungee）依赖 shadowJar；
    // proxy:common 是纯 java 库、无 shadowJar 任务，其 jar 会随 velocity/bungee 的 shadowJar 传递构建。
    dependsOn(
        subprojects
            .filter { it.path.startsWith(":proxy:") && it.plugins.hasPlugin("com.gradleup.shadow") }
            .map { "${it.path}:shadowJar" }
    )
}
