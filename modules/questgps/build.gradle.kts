plugins {
    id("java")
}

val protectedResourcesDir = layout.buildDirectory.dir("generated/protected-resources")

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly(project(":axs-core"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly(files("../../libs/Chemdah-1.1.33-FREE.jar"))
}

sourceSets {
    main {
        resources {
            setSrcDirs(listOf("src/main/resources", protectedResourcesDir))
        }
    }
}

val protectYamlResources by tasks.registering(ProtectYamlResourcesTask::class) {
    sourceDir.set(layout.projectDirectory.dir("src/main/resources"))
    outputDir.set(protectedResourcesDir)
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.processResources {
    dependsOn(protectYamlResources)
    exclude { details ->
        val path = details.relativePath.pathString
        (path.endsWith(".yml") || path.endsWith(".yaml")) && path != "module.yml"
    }
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-QuestGPS")
    archiveClassifier.set("")
    // 仅编译期占位：运行时由 TabooLib 重定位提供，避免污染最终 JAR / 与运行时类冲突。
    exclude("ink/ptms/chemdah/taboolib/**")
    exclude("kotlin1822/**")
}

