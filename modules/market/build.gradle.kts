plugins {
    id("java")
}

val protectedResourcesDir = layout.buildDirectory.dir("generated/protected-resources")

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("redis.clients:jedis:5.1.0")
    compileOnly("com.belerweb:pinyin4j:2.5.1")
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
    archiveBaseName.set("ArcartXSuite-Market")
    archiveClassifier.set("")
}
