import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
}

val distDir = rootProject.layout.buildDirectory.dir("ArcartXSuite")

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
        // 模块子项目：注册混淆任务
        if (project.path.startsWith(":modules:")) {
            val moduleJarTask = tasks.named<Jar>("jar")

            // 原始 jar 输出到 build/libs（中间产物）
            moduleJarTask.configure {
                destinationDirectory.set(layout.buildDirectory.dir("libs"))
            }

            // 混淆后的 jar 输出到 dist/modules/
            val obfuscateModule = tasks.register<ObfuscateJarTask>("obfuscateModule") {
                dependsOn(moduleJarTask)
                inputJar.set(moduleJarTask.flatMap { it.archiveFile })
                outputJar.set(distDir.map { it.file("modules/${moduleJarTask.get().archiveFileName.get()}") })
                coreJar.set(false)
                libraryJars.from(configurations.named("compileClasspath"))
                configFiles.from(rootProject.file("proguard/modules.pro"))
            }

            tasks.named("build") {
                dependsOn(obfuscateModule)
            }

            // 将混淆后的模块 Jar 加密为 .axb，供上传到 AXS Cloud Platform。
            // 不挂到 build；按需执行（如 :modules:afkreward:encryptModuleAxb）。
            // 未配置 moduleKey/moduleIv 时会自动生成随机密钥并打印到控制台，
            // 请将控制台输出的 moduleKey 保存并在上传到云端时填写。
            tasks.register<EncryptModuleAxbTask>("encryptModuleAxb") {
                group = "protection"
                description = "将混淆后的模块 Jar 加密为 .axb（上传云端用）"
                dependsOn(obfuscateModule)
                inputJar.set(obfuscateModule.flatMap { it.outputJar })
                outputAxb.set(distDir.map { it.file("module-axb/${project.name}.axb") })
                // 可选：在 gradle.properties 中配置固定密钥
                //   module.<name>.key=<base64-32bytes>
                //   module.<name>.iv=<base64-12bytes>
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
