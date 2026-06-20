import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
}

val distDir = rootProject.layout.buildDirectory.dir("ArcartXSuite")

// 混淆后的 core jar 路径（模块编译时依赖此产物，使得 bridge 等类被完全混淆后仍可链接）
val obfuscatedCoreJar: Provider<RegularFile> = project(":axs-core").layout.buildDirectory.file("libs/ArcartXSuite-step1-obfuscated.jar")

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
            val obfuscatedModuleJar = distDir.map { it.file("modules/${moduleJarTask.get().archiveFileName.get()}") }
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

            // 字符串加密（ProGuard 之后）
            val stringEncryptedModuleJar = distDir.map { it.file("modules-enc/${moduleJarTask.get().archiveFileName.get()}") }
            val stringEncryptModule = tasks.register<StringEncryptTask>("stringEncryptModule") {
                dependsOn(obfuscateModule)
                inputJar.set(obfuscateModule.flatMap { it.outputJar })
                outputJar.set(stringEncryptedModuleJar)
            }

            // 最终产物：从字符串加密后的 jar 复制到 modules/ 目录
            val publishModuleJar = tasks.register("publishModuleJar") {
                dependsOn(stringEncryptModule)
                val src = stringEncryptedModuleJar.get().asFile
                val dst = obfuscatedModuleJar.get().asFile
                inputs.file(src)
                outputs.file(dst)
                doLast {
                    dst.parentFile.mkdirs()
                    src.copyTo(dst, overwrite = true)
                }
            }

            tasks.named("build") {
                dependsOn(publishModuleJar)
            }

            // 将混淆后的模块 Jar 加密为 .axb，供上传到 AXS Cloud Platform。
            tasks.register<EncryptModuleAxbTask>("encryptModuleAxb") {
                group = "protection"
                description = "将混淆后的模块 Jar 加密为 .axb（上传云端用）"
                dependsOn(publishModuleJar)
                inputJar.set(obfuscatedModuleJar.map { it } as Provider<RegularFile>)
                outputAxb.set(distDir.map { it.file("module-axb/${project.name}.axb") })
                val keyProp = project.findProperty("module.${project.name}.key") as String?
                val ivProp = project.findProperty("module.${project.name}.iv") as String?
                if (keyProp != null) moduleKey.set(keyProp)
                if (ivProp != null) moduleIv.set(ivProp)
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
    description = "将所有模块加密为 .axb（输出到 build/ArcartXSuite/module-axb/，上传云端用）"
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") }.map { "${it.path}:encryptModuleAxb" })
}

// 快速构建（跳过混淆，仅开发测试用）
tasks.register("buildDev") {
    group = "build"
    description = "Build all JARs WITHOUT obfuscation (dev only)"
    dependsOn(":axs-core:shadowJar")
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") }.map { "${it.path}:jar" })
    dependsOn(subprojects.filter { it.path.startsWith(":proxy:") }.map { "${it.path}:shadowJar" })
}
