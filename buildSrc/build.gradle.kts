plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.guardsquare:proguard-base:7.4.2")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
}
