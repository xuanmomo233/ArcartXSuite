import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version "8.3.5" apply false
}

val distDir = rootProject.layout.buildDirectory.dir("ArcartXSuite")
val paidModules = setOf("warehouse", "map", "mail", "title", "questgps", "conversation", "tab", "entitytracker", "qqbot")

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
        tasks.withType<ProtectYamlResourcesTask>().configureEach {
            if (project.path.startsWith(":modules:") && project.name in paidModules) {
                licenseBoundModuleId.set(project.name)
            }
        }

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

// 快速构建（跳过混淆，仅开发测试用）
tasks.register("buildDev") {
    group = "build"
    description = "Build all JARs WITHOUT obfuscation (dev only)"
    dependsOn(":axs-core:shadowJar")
    dependsOn(subprojects.filter { it.path.startsWith(":modules:") || it.path.startsWith(":proxy:") }.map { "${it.path}:jar" })
}
