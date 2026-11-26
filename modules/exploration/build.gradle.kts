plugins {
    id("java")
}

extra["axs.namedModuleEntry"] = "xuanmo.arcartxsuite.exploration.ExplorationModule"

dependencies {
    compileOnly(project(":axs-api"))
    compileOnly(files("../../libs/Adyeshach-2.1.30.jar"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(project(":axs-api"))
    testImplementation(files("../../libs/Adyeshach-2.1.30.jar"))
    testImplementation("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
}

tasks.test { useJUnitPlatform() }

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 17
}

tasks.jar {
    archiveBaseName.set("ArcartXSuite-Exploration")
    archiveClassifier.set("")
}
